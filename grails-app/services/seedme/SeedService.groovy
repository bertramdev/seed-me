package seedme

import grails.util.GrailsUtil
import org.codehaus.groovy.grails.plugins.DomainClassGrailsPlugin
import org.codehaus.groovy.grails.commons.ConfigurationHolder
import org.codehaus.groovy.grails.web.metaclass.BindDynamicMethod
import org.springframework.web.context.request.RequestContextHolder
import org.codehaus.groovy.grails.plugins.GrailsPluginUtils

class SeedService {
	static transactional = false
	def grailsApplication

	def installSeedData() {
		log.info("seedService.installSeedData")
		def seedFiles = getSeedFiles()

		log.info("seedService - processing ${seedFiles?.size()} files")
		def seedSets    = buildSeedSets(seedFiles)
		def newSeedSets = orderSeedSetsByDepends(seedSets)
		def seedList = []

		newSeedSets?.each { tmpSet ->
			seedList.addAll(tmpSet.seedList)
		}
		log.debug("processing: ${seedList}")
		seedList?.each { tmpSeed ->
			processSeedItem(tmpSeed)
		}

	}

	private orderSeedSetsByDepends(seedSets) {
		//sort them by depends on
		def newSeedSets = seedSets.clone()
		seedSets?.each { tmpSet ->
			if(tmpSet?.dependsOn?.size() > 0) {
				def maxIndex = 0
				def myIndex = newSeedSets.findIndexOf{it.name == tmpSet.name}
				tmpSets.dependsOn.each { tmpDepends ->
					def tmpMatch
					if(tmpDepends.contains('.')) {
						def dependsArgs = tmpDepends.split(".")
						def pluginName  = dependsArgs[0]
						def seedName    = dependsArgs[1]
						tmpMatch = newSeedSets.findIndexOf{it.name == seedName && it.plugin == pluginName}
					} else {
						def matchCount = newSeedSets.findAll{it.name == tmpDepends}
						if(matchCount.size() > 1) {
							tmpMatch = newSeedSets.findIndexOf{it.name == tmpDepends && it.plugin == 'application'}
						} else {
							tmpMatch = newSeedSets.findIndexOf{it.name == tmpDepends}
						}

					}

					if(tmpMatch > -1)
						maxIndex = tmpMatch
				}
				if(myIndex < maxIndex) {
					def tmpOut = newSeedSets.remove(myIndex)
					newSeedSets.putAt(maxIndex - 1, tmpOut)
				}
			}
		}
		return newSeedSets
	}

	private buildSeedSets(seedFiles) {
		def seedSets = []
		seedFiles.each { seedFile ->
			def tmpFile    = seedFile.file
			def pluginName = seedFile.plugin
			def tmpContent = tmpFile.getText()
			if(tmpContent?.length() > 0) {
				def tmpBinding = new Binding()
				def tmpConfig = new groovy.lang.GroovyShell(tmpBinding).evaluate(tmpContent)
				def tmpBuilder = new SeedBuilder()
				tmpBuilder.seed(tmpBinding.getVariable('seed'))
				if(tmpBuilder.seedList?.size() > 0) {
					def tmpSet = [seedList:[], dependsOn:tmpBuilder.dependsOn, name:getSeedSetName(tmpFile.name), plugin: pluginName]
					tmpSet.seedList.addAll(tmpBuilder.seedList)
					seedSets << tmpSet
				}
			}
		}
		return seedSets
	}

	def processSeedItem(seedItem) {
		def tmpDomain = grailsApplication.getArtefactByLogicalPropertyName('Domain', seedItem.domainClass)
		def tmpMeta = seedItem.meta
		if(tmpDomain && tmpMeta.key) {
			def tmpProperties = tmpDomain.getPersistentProperties()
			def tmpData = seedItem.data
			def saveData = [:]
			tmpData.each { key, value ->
				def tmpProp = tmpProperties.find{it.getFieldName().toLowerCase() == key.toLowerCase()}
				if(tmpProp) {
					if(tmpProp.isAssociation()) {
						def subDomain = tmpProp.getReferencedDomainClass()
						if(tmpProp.isOneToMany()) {
							if(value instanceof Map) {
								setSeedValue(saveData, key, value, subDomain)
							} else if(value instanceof List) {
								setSeedValue(saveData, key, value, subDomain)
							}
						} else if(tmpProp.isHasOne() || tmpProp.isOneToOne()) {
							if(value instanceof Map) {
								setSeedValue(saveData, key, value)
							}
						} else if(tmpProp.isManyToMany()) {
							if(value instanceof Map) {
								setSeedValue(saveData, key, value, subDomain)
							}
						} else if(tmpProp.isManyToOne()) {
							if(value instanceof Map) {
								setSeedValue(saveData, key, value)
							}
						} else {
							log.warn "association is not handled thus this object may not be seeded"
						}
					} else {
						setSeedValue(saveData, key, value)
					}
				} else {
					setSeedValue(saveData, key, value)
				}
			}
			createSeed(tmpDomain, tmpMeta.key, saveData)
		}
	}

	def setSeedValue(data, key, value, domain = null) {
		def tmpCriteria = [:]
		if(domain) {
			if(value instanceof Map) {
				data[key] = findSeedObject(domain, value)
			} else if(value instanceof List) {
				data[key] = value.collect {
					def tmpSeedMeta = it.remove('meta')
					tmpCriteria = it
					if(tmpSeedMeta && tmpSeedMeta['criteria']==true) {
						it.each{ k, val  ->
							setSeedValue(tmpCriteria,k,val)
						}
					}
					findSeedObject(domain, tmpCriteria)
				}.findAll{it!=null}
			}
//		} else if (value instanceof Map && value.meta) {
		} else if (value instanceof Map) {
			def tmpMatchDomain = value.remove('domainClass')
			def tmpObjectMeta = value.remove('meta')
			if(tmpObjectMeta && tmpObjectMeta['criteria']==true) {
				value.each{ k , val ->
					setSeedValue(tmpCriteria,k,val)
				}
				value = tmpCriteria
			}
			def seedObject = findSeedObject(tmpMatchDomain ?: key, value)
			if(tmpObjectMeta && tmpObjectMeta['useId']==true)
				seedObject = seedObject?.id
			else if(tmpObjectMeta && tmpObjectMeta['property'])
				seedObject = seedObject?."${tmpObjectMeta['property']}"
			data[key] = seedObject
		} else if(value instanceof String) {
			data[key] = new groovy.text.GStringTemplateEngine().createTemplate(value).make(getDomainBindingsForGString()).toString()
		} else {
			data[key] = value
		}
	}

	def getDomainBindingsForGString() {
		def domains = grailsApplication.getArtefacts("Domain")
		def binding = [:]
		domains.each { domain ->
			binding[domain.name] = domain.clazz
		}
		return binding
	}

	def findSeedObject(domain, opts) {
		def rtn = null
		if(domain instanceof String)
			domain = grailsApplication.getArtefactByLogicalPropertyName('Domain', domain)?.getClazz()
		def tmpMeta = opts.remove('meta')
		if(domain) {
			def tmpInstance = domain.newInstance()
			rtn = tmpInstance.findWhere(opts)
			if(tmpMeta?.useId == true)
				rtn = rtn.id
			if(tmpMeta?.containsKey('property')) {
				rtn = rtn."${tmpMeta.property}"
			}
		}
		return rtn
	}

	def getSeedRoot() {
		if(grailsApplication.warDeployed)
			grailsApplication.mainContext.getResource('seed').file.path
		else
			getConfig()?.root ?: 'seed'
	}

	def getSeedPath(name) {
		return getSeedRoot() + name
	}

	def getEnvironmentSeedPath() {
		return getConfig()?.environment
	}

	def getConfig() {
		return grailsApplication.config.grails.plugin.seed
	}

	def createSeed(domain, key, config, opts = [:]) {
		if(!(key instanceof Collection))
			key = [key]
		if(domain instanceof String)
			domain = grailsApplication.getArtefactByLogicalPropertyName('Domain', domain)?.getClazz()
		if(domain) {
			def tmpObj = findSeedObject(domain, key.collect{k -> [k, config[k]]}.collectEntries())
			if(tmpObj) {
				if(opts.update == null || opts.update == true) {
					def tmpChanged = applyChanges(tmpObj, config, key)
					if(tmpChanged == true) {
						tmpObj.save(flush:true)
						if(tmpObj.errors.hasErrors())
							log.error(tmpObj.errors)
					}
				}
			} else {
				tmpObj = domain.newInstance()
				applyChanges(tmpObj, config)
				tmpObj.save(flush:true, insert:true)
				if(tmpObj.errors.hasErrors())
					log.error(tmpObj.errors)
			}
			return tmpObj
		} else {
			log.warn("cound not find domain: ${domain}")
		}
		return null
	}

	def applyChanges(obj, config, excludes = []) {
		def changed = false
		config.keySet().each {
			if(!excludes.contains(it)) {
				def tmpVal = config[it]
				def tmpCompare = obj[it]
				if(tmpVal != tmpCompare) {
					changed = true
					obj[it] = tmpVal
				}
			}
		}
		return changed
	}

	def bindObject(tgt, src, config = null) { //just like bindData
		def tmpBind = new BindDynamicMethod()
		def tmpArgs = [tgt, src]
		if(config) tmpArgs << config
		tmpBind.invoke(tgt, 'bind', (Object[])tmpArgs)
	}

	def getSeedSetName(str) {
		def rtn = str
		def tmpIndex = rtn?.lastIndexOf('.')
		if(tmpIndex > -1)
		  rtn = rtn.substring(0, tmpIndex)
		return rtn
	}

	private isPluginExcluded(name) {
		def excluded = getConfig().excludedPlugins ?: []
		return excluded.find { it == name} ? true : false
	}

	private getSeedPathsByPlugin() {
		def seedPaths = [:]
		if(grailsApplication.warDeployed) {
			//TODO: NEED TO USE getResourcePaths() here for accurate directory listing
			def seedRoot = grailsApplication.mainContext.getResource('seed').file
			if(seedRoot.exists()) {
				seedRoot.eachDir { pluginFolder ->
					if(!isPluginExcluded(pluginFolder.name)) {
						seedPaths[pluginFolder.name] = pluginFolder.path
					}
				}
			}
		} else {
			def seedRoot = getConfig()?.root ?: 'seed'

			for(plugin in GrailsPluginUtils.pluginInfos) {
				if(!isPluginExcluded(plugin.name)) {
					def seedPath = [plugin.pluginDir.getPath(), seedRoot].join(File.separator)
					seedPaths[plugin.name] = seedPath
				}
			}
			seedPaths.application = seedRoot
		}
		return seedPaths
	}


	def getSeedFiles() {
		def tmpEnvironmentFolder = getEnvironmentSeedPath() //configurable seed environment.
		def seedPaths = getSeedPathsByPlugin()
		def env = GrailsUtil.environment
		def seedFiles = []
		if(!seedPaths) {
			log.error "Seed folder '${seedFolder.absolutePath}' not found"
		}
		seedPaths.each { seedPath ->
			def seedFolder = new File(seedPath.value)
			def pluginName = seedPath.key
			if(seedFolder.exists()) {
				seedFolder?.eachFile { tmpFile ->
					if(!tmpFile.isDirectory() && tmpFile.name.endsWith('.groovy'))
						seedFiles << [file: tmpFile, plugin: pluginName]
				}
				seedFolder?.eachDir { tmpFolder ->
					if(tmpFolder.name == env || (tmpFolder.name == tmpEnvironmentFolder)) {
						tmpFolder.eachFile { tmpFile ->
							if(!tmpFile.isDirectory() && tmpFile.name.endsWith('.groovy'))
								seedFiles << [file: tmpFile, plugin: pluginName]
						}
					}
				}
			}
		}

		return seedFiles
	}
}
