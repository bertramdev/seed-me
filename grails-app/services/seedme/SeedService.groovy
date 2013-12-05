package seedme

import grails.util.Environment
import groovy.text.GStringTemplateEngine
import org.codehaus.groovy.grails.plugins.GrailsPluginUtils
import org.codehaus.groovy.grails.web.metaclass.BindDynamicMethod
import org.codehaus.groovy.grails.plugins.DomainClassGrailsPlugin

class SeedService {

	static transactional = false

	def grailsApplication
	def sessionFactory
	def propertyInstanceMap = DomainClassGrailsPlugin.PROPERTY_INSTANCE_MAP

	void installSeedData() {
		// GRAILS 2.3.1 ISSUE?
		// Caused by MissingPropertyException: No such property: log for class: seedme.SeedService
		//log.info("seedService.installSeedData")
		def seedFiles = getSeedFiles()
		//log.info("seedService - processing ${seedFiles?.size()} files")
		def seedSets    = buildSeedSets(seedFiles)
		def newSeedSets = orderSeedSetsByDepends(seedSets)
		//def seedList = []
		newSeedSets?.each { tmpSet ->
			try {
				println("processing seed - ${tmpSet.name}")
				tmpSet.seedList?.each { tmpSeed ->
					//println("processing seed file ${tmpSeed.name}")
					try {
						processSeedItem(tmpSeed)
					} catch(e) {
						println("error processing seed item ${tmpSeed} - ${e}")
						throw e
					}
				}
				gormFlush()
				//seedList.addAll(tmpSet.seedList)
			} catch(setError) {
				println("error processing seed set ${tmpSet?.name} - ${setError}")
			}
		}
		//log.debug("processing: ${seedList}")
		println("installSeedData complete")
	}

	def installExternalSeed(seedContent) {
		try {
			def tmpSet = buildSeedSet('external', seedContent)
			println("processing external seed")
			tmpSet.seedList?.each { tmpSeed ->
				try {
					processSeedItem(tmpSeed)
				} catch(e) {
					println("error processing seed item ${tmpSeed} - ${e}")
					throw e
				}
			}
			processSeedItem(tmpSet)
		} catch(e) {
			//log.error(e)
			throw e
		}
	}

	private orderSeedSetsByDepends(seedSets) {
		//sort them by depends on
		def noDepends = seedSets.findAll{it.dependsOn == null || it.dependsOn.size() < 1}
		def yesDepends = seedSets.findAll{it.dependsOn != null && it.dependsOn.size() >= 1}
		def rtnSets = noDepends
		println("noDepends: ${noDepends.collect{it.name}}")
		println("yesDepends: ${yesDepends.collect{it.name}}")
		def dependsMap = [:]
		yesDepends.sort{it.dependsOn.size()}
		yesDepends?.each { tmpSet ->
			def maxIndex = 0
			def yesIndex = yesDepends.findIndexOf{it.name == tmpSet.name}
			tmpSet.dependsOn.each { tmpDepends ->
				def tmpMatch
				if(tmpDepends.contains('.')) {
					def dependsArgs = tmpDepends.split(".")
					def pluginName  = dependsArgs[0]
					def seedName    = dependsArgs[1]
					tmpMatch = yesDepends.findIndexOf{it.name == seedName && it.plugin == pluginName}
				} else {
					def matchCount = yesDepends.findAll{it.name == tmpDepends}
					if(matchCount.size() > 1) {
						tmpMatch = yesDepends.findIndexOf{it.name == tmpDepends && it.plugin == 'application'}
					} else {
						tmpMatch = yesDepends.findIndexOf{it.name == tmpDepends}
					}
				}
				if(tmpMatch > -1) {
					maxIndex = tmpMatch
				}
			}
			tmpSet.maxIndex = maxIndex
		}
		yesDepends.sort{it.maxIndex}
		rtnSets.addAll(yesDepends)
		println("rtnSets: ${rtnSets.collect{it.name}}")
		return rtnSets
		/*seedSets.sort{it.dependsOn?.size()}
		def newSeedSets = seedSets.clone()
		seedSets?.each { tmpSet ->
			if(tmpSet?.dependsOn?.size() > 0) {
				def maxIndex = 0
				def myIndex = newSeedSets.findIndexOf{it.name == tmpSet.name}
				tmpSet.dependsOn.each { tmpDepends ->
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
					if(tmpMatch > -1) {
						maxIndex = tmpMatch
					}
				}
				if(myIndex < maxIndex) {
					def tmpOut = newSeedSets.remove(myIndex)
					newSeedSets.putAt(maxIndex - 1, tmpOut)
				}
			}
		}
		return newSeedSets*/
	}

	private buildSeedSets(seedFiles) {
		def seedSets = []
		seedFiles.each { seedFile ->
			//change to call below method
			def tmpFile    = seedFile.file
			def pluginName = seedFile.plugin
			def tmpContent = tmpFile.getText()
			if(tmpContent) {
				def tmpSeedSet = buildSeedSet(getSeedSetName(tmpFile.name), tmpContent, pluginName)
				if(tmpSeedSet)
					seedSets << tmpSeedSet
			}
		}
		return seedSets
	}

	private buildSeedSet(name, seedContent, plugin = null) {
		def rtn
		try {
			if(seedContent) {
				def tmpBinding = new Binding()
				def tmpConfig = new GroovyShell(tmpBinding).evaluate(seedContent)
				def tmpBuilder = new SeedBuilder()
				tmpBuilder.seed(tmpBinding.getVariable('seed'))
				if(tmpBuilder.seedList) {
					rtn = [seedList:[], dependsOn:tmpBuilder.dependsOn, name:name, plugin:plugin]
					rtn.seedList.addAll(tmpBuilder.seedList)
				}
			}
		} catch(e) {
			//log.error(e)
			println("error building seed set ${name} - ${e}")
		}

		return rtn
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
							//log.warn "association is not handled thus this object may not be seeded"
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
				def tmpObj = findSeedObject(domain, value) 
				if(tmpObj)
					data[key] = tmpObj
			} else if(value instanceof List) {
				data[key] = value.collect {
					def tmpSeedMeta = it.clone().remove(getMetaKey())
					tmpCriteria = it
					if(tmpSeedMeta && tmpSeedMeta['criteria']==true) {
						it.each{ k, val  ->
							setSeedValue(tmpCriteria,k,val)
						}
					}
					findSeedObject(domain, tmpCriteria)
				}.findAll{it!=null}
			}
			//} else if (value instanceof Map && value.meta) {
		} else if (value instanceof Map) {
			value = value.clone() //Dont want to simply remove keys in case this value is reused elsewhere
			def tmpMatchDomain = value.remove('domainClass')
			def tmpObjectMeta = value.remove(getMetaKey())
			if(tmpObjectMeta && tmpObjectMeta['criteria']==true) {
				value.each{ k , val ->
					setSeedValue(tmpCriteria,k,val)
				}
				value = tmpCriteria
			}
			def seedObject = findSeedObject(tmpMatchDomain ?: key, value)
			if(tmpObjectMeta && tmpObjectMeta['useId']==true)
				seedObject = seedObject?.id
			else if(tmpObjectMeta && tmpObjectMeta['useValue'])
				seedObject = seedObject[tmpObjectMeta['useValue']]
			else if(tmpObjectMeta && tmpObjectMeta['useClosure'])
				seedObject = tmpObjectMeta['useClosure'](seedObject)
			else if(tmpObjectMeta && tmpObjectMeta['property'])
				seedObject = seedObject?."${tmpObjectMeta['property']}"
			if(seedObject)
				data[key] = seedObject
		} else if(value instanceof CharSequence) {
			data[key] = new GStringTemplateEngine().createTemplate(value.toString()).make(getDomainBindingsForGString()).toString()
		} else {
			data[key] = value
		}
	}

	def getDomainBindingsForGString() {
		def binding = [:]
		for (domain in grailsApplication.domainClasses) {
			binding[domain.name] = domain.clazz
		}
		return binding
	}

	def findSeedObject(domain, opts) {
		def rtn
		if(domain instanceof CharSequence)
			domain = grailsApplication.getArtefactByLogicalPropertyName('Domain', domain.toString())?.getClazz()
		def tmpMeta = opts.remove(getMetaKey())
		if(domain) {
			def tmpInstance = domain.newInstance()
			def tmpOpts = opts.clone()
			tmpOpts.remove('useId')
			tmpOpts.remove('useField')
			tmpOpts.remove('useClosure')
			rtn = tmpInstance.findWhere(opts)
			if(tmpMeta?.useId == true)
				rtn = rtn.id
			if(tmpMeta?.containsKey('property')) {
				rtn = rtn."${tmpMeta.property}"
			}
		}
		return rtn
	}

	String getSeedRoot() {
		if(grailsApplication.warDeployed)
			grailsApplication.mainContext.getResource('seed').file.path
		else
			getConfig()?.root ?: 'src/seed'
	}

	String getSeedPath(name) {
		return getSeedRoot() + name
	}

	String getMetaKey() {
		getConfig()?.metaKey ?: 'meta'
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
		if(domain instanceof CharSequence)
			domain = grailsApplication.getArtefactByLogicalPropertyName('Domain', domain.toString())?.getClazz()
		if(domain) {
			def tmpObj = findSeedObject(domain, key.collect{k -> [k, config[k]]}.collectEntries())
			if(tmpObj) {
				if(opts.update == null || opts.update == true) {
					def tmpChanged = applyChanges(tmpObj, config, key)
					if(tmpChanged == true) {
						tmpObj.save(flush:true)
						if(tmpObj.errors.hasErrors()) {
							println(tmpObj.errors)
							//log.error(tmpObj.errors)
						}
					}
				}
			} else {
				tmpObj = domain.newInstance()
				applyChanges(tmpObj, config)

				tmpObj.save(flush:true, insert:true)
				if(tmpObj.errors.hasErrors()) {
					println(tmpObj.errors)
					//log.error(tmpObj.errors)
				}

			}
			return tmpObj
		} else {
			//log.warn("cound not find domain: ${domain}")
		}
		return null
	}

	boolean applyChanges(obj, config, excludes = []) {
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

	String getSeedSetName(str) {
		def rtn = str
		def tmpIndex = rtn?.lastIndexOf('.')
		if(tmpIndex > -1)
		  rtn = rtn.substring(0, tmpIndex)
		return rtn
	}

	private boolean isPluginExcluded(name) {
		def excluded = getConfig().excludedPlugins ?: []
		return excluded.find { it == name}
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
			def seedRoot = getConfig()?.root ?: 'src/seed'
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
		def env = tmpEnvironmentFolder ?: Environment.current.name
		def seedFiles = []
		if(!seedPaths) {
			//log.error "Seed folder '${seedFolder.absolutePath}' not found"
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
					if(tmpFolder.name == env) {
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

	def gormFlush() {
		def session = sessionFactory.currentSession
		session.flush()
		propertyInstanceMap.get().clear()
	}

}
