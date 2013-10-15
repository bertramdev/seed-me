package com.procon.nspire.seed

import grails.util.GrailsUtil
import org.codehaus.groovy.grails.plugins.DomainClassGrailsPlugin
import org.codehaus.groovy.grails.commons.ConfigurationHolder
import org.codehaus.groovy.grails.web.metaclass.BindDynamicMethod
import org.springframework.web.context.request.RequestContextHolder

class SeedService {

	def grailsApplication

	def installSeedData() {
		log.info("seedService.installSeedData")
		def seedFiles = []
		def seedFolder = new File(getSeedRoot())
		def tmpEnvironmentFolder = getEnvironmentSeedPath() //configurable seed environment.
		if(!seedFolder.exists()) {
			println "Seed folder '${seedFolder.absolutePath}' not found"
			return
		}
		def env = GrailsUtil.environment
		if(seedFolder.exists()) {
			seedFolder?.eachFile { tmpFile ->
				if(!tmpFile.isDirectory() && tmpFile.name.endsWith('.groovy'))
					seedFiles << tmpFile
			}
			seedFolder?.eachDir { tmpFolder ->
				if(tmpFolder.name == env || (tmpFolder.name == tmpEnvironmentFolder)) {
					tmpFolder.eachFile { tmpFile ->
						if(!tmpFile.isDirectory() && tmpFile.name.endsWith('.groovy'))
							seedFiles << tmpFile
					}
				}
			}
			log.info("seedService - processing ${seedFiles?.size()} files")
			def seedSets = []
			def seedList = []
			seedFiles.each { tmpFile ->
				def tmpContent = tmpFile.getText()
				if(tmpContent?.length() > 0) {
					def tmpBinding = new Binding()
					def tmpConfig = new groovy.lang.GroovyShell(tmpBinding).evaluate(tmpContent)
					def tmpBuilder = new SeedBuilder()
					tmpBuilder.seed(tmpBinding.getVariable('seed'))
					if(tmpBuilder.seedList?.size() > 0) {
						def tmpSet = [seedList:[], dependsOn:tmpBuilder.dependsOn, name:getSeedSetName(tmpFile.name)]
						tmpSet.seedList.addAll(tmpBuilder.seedList)
						seedSets << tmpSet
					}

				}
			}
			//sort them by depends on
			def newSeedSets = seedSets.clone()
			seedSets?.each { tmpSet ->
				if(tmpSet?.dependsOn?.size() > 0) {
					def maxIndex = 0
					def myIndex = newSeedSets.findIndexOf{it.name == tmpSet.name}
					tmpSets.dependsOn.each { tmpDepends ->
						def tmpMatch = newSeedSets.findIndexOf{it.name == tmpDepends}
						if(tmpMatch > -1)
							maxIndex = tmpMatch
					}
					if(myIndex < maxIndex) {
						def tmpOut = newSeedSets.remove(myIndex)
						newSeedSets.putAt(maxIndex - 1, tmpOut)
					}
				}
			}
			newSeedSets?.each { tmpSet ->
				seedList.addAll(tmpSet.seedList)
			}
			log.info("processing: ${seedList}")
			seedList?.each { tmpSeed ->
				processSeedItem(tmpSeed)
			}
		}
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
				}
			}
			createSeed(tmpDomain, tmpMeta.key, saveData)
		}
	}

	def setSeedValue(data, key, value, domain = null) {
		def tmpCriteria = [:]
		if(domain) {
			if(value instanceof Map) {
				println("lookup collection: ${key} ${value}")
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
			println("lookup collection: ${key} ${value}")
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
		} else {
			data[key] = value
		}
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

}
