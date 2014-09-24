package seedme

import grails.util.Environment
import groovy.text.GStringTemplateEngine
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty

import java.security.MessageDigest
import javax.xml.bind.DatatypeConverter
import org.codehaus.groovy.grails.plugins.GrailsPluginUtils
import org.codehaus.groovy.grails.web.metaclass.BindDynamicMethod
import org.codehaus.groovy.grails.plugins.DomainClassGrailsPlugin
import groovy.util.logging.Log4j
import org.apache.commons.io.FilenameUtils as FNU

@Log4j
class SeedService {

	static transactional = false

	def grailsApplication
	def sessionFactory
	def messageSource
	def propertyInstanceMap = DomainClassGrailsPlugin.PROPERTY_INSTANCE_MAP

	void installSeedData() {
		// GRAILS 2.3.1 ISSUE?
		// Caused by MissingPropertyException: No such property: log for class: seedme.SeedService
		log.info("seedService.installSeedData")
		def seedFiles = getSeedFiles()
		log.info("seedService - processing ${seedFiles?.size()} files")
		def (seedSets, seedSetByPlugin, seedSetsByName)    = buildSeedSets(seedFiles)
		// make a copy so we can remove items from one list as they are processed, another to
		// iterate through
		def seedSetsToRun = seedSets.clone()

		seedSets.each { name, set ->
			seedSetProcess(set, seedSetsToRun, seedSetByPlugin, seedSetsByName)
		}

		println("installSeedData complete")
	}

    void installSeedData(String name) {
        def requestedSets = [:]

        def seedFiles = getSeedFiles()
        def (seedSets, seedSetByPlugin, seedSetsByName)    = buildSeedSets(seedFiles)

        if(name.contains('.')) {
            def plugin = name.substring(0, name.indexOf('.'))
            String seedName = name.substring(plugin.length() + 1)
            plugin = plugin.replaceAll(/\B[A-Z]/) { '-' + it }.toLowerCase()

            String seedAddress = plugin + "." + seedName
            requestedSets."$seedAddress" = seedSets."$seedAddress"
        } else { // in case of ambiguity find all seeds with matching name
            def sets = seedSetsByName[name]
            requestedSets = sets.collect { "${it.plugin}.${it.name}" }
        }

        log.info("installSeedData: " + name)

        def seedSetsToRun = requestedSets + [:]

        requestedSets.each { setName, set ->
            seedSetProcess(set, seedSetsToRun, seedSetByPlugin, seedSetsByName)
        }

        log.info("installSeedData complete")
    }


	def installExternalSeed(seedContent) {
		try {
			def tmpSet = buildSeedSet('external', seedContent)
			println("processing external seed")
			tmpSet.seedList?.each { tmpSeed ->
				try {
					processSeedItem(tmpSeed)
				} catch(e) {
					log.error("error processing seed item ${tmpSeed}",e)
					throw e
				}
			}
			processSeedItem(tmpSet)
		} catch(e) {
			//log.error(e)
			throw e
		}
	}


	/**
	* Allows a closure of seed DSL to be passed to execute, Useful for tests
	* @param Closure seedData - Expects a Closure to be passed in with the seed me DSL
	*/
	def installSeed(seedData) {
		try {
			def tmpSet = [seedList:[], dependsOn:[], name:'name', plugin:null]
			def tmpBuilder = new SeedBuilder()
			tmpBuilder.seed(seedData)
			tmpSet.dependsOn = tmpBuilder.dependsOn
			tmpSet.seedList.addAll(tmpBuilder.seedList)
			log.info("processing inline seed")
			tmpSet.seedList?.each { tmpSeed ->
				try {
					processSeedItem(tmpSeed)
				} catch(e) {
					log.error("error processing seed item ${tmpSeed}",e)
					throw e
				}
			}
			processSeedItem(tmpSet)
		} catch(e) {
			//log.error(e)
			throw e
		}
	}

	private buildSeedSets(seedFiles) {
		def seedSets = [:], byPlugin = [:], byName = [:]
		seedFiles.each { seedFile ->
			//change to call below method
			def tmpFile    = seedFile.file
			def pluginName = seedFile.plugin ?: 'application'
			def tmpContent = tmpFile.getText()

			def tmpSeedName = getSeedSetName(tmpFile.name)
			byPlugin[pluginName] = byPlugin[pluginName] ?: [:]
			byName[tmpSeedName] = byName[tmpSeedName] ?: []
			def tmpSeedSet = buildSeedSet(tmpSeedName, tmpContent, pluginName)
			def tmpSetKey = buildSeedSetKey(tmpSeedName, pluginName)
			seedSets[tmpSetKey] = tmpSeedSet
			byPlugin[pluginName][tmpSetKey] = tmpSeedSet
			byName[tmpSeedName] << tmpSeedSet

		}

		return [seedSets, byPlugin, byName]
	}

	private buildSeedSet(name, seedContent, plugin = null) {
		def rtn = [seedList:[], dependsOn:[], name:name, plugin:plugin]
		try {
			def tmpBinding = new Binding()
			tmpBinding.setVariable("grailsApplication", grailsApplication)
			def tmpConfig = new GroovyShell(this.class.classLoader, tmpBinding).evaluate(seedContent,plugin ? "${plugin}:${name}" : name)
			rtn.checksum = DatatypeConverter.printBase64Binary(MessageDigest.getInstance('MD5').digest(seedContent.bytes))
			def tmpBuilder = new SeedBuilder()
			tmpBuilder.seed(tmpBinding.getVariable('seed'))
			rtn.dependsOn = tmpBuilder.dependsOn
			rtn.seedList.addAll(tmpBuilder.seedList)

		} catch(e) {
			log.error("error building seed set ${name}",e)
		}

		return rtn
	}

	def processSeedItem(seedItem) {
		GrailsDomainClass tmpDomain = grailsApplication.getArtefactByLogicalPropertyName('Domain', seedItem.domainClass)
		def tmpMeta = seedItem.meta
		if(tmpDomain && tmpMeta.key) {
			def tmpData = seedItem.data
			def saveData = [:]
			tmpData.each { key, value ->
				GrailsDomainClassProperty tmpProp = tmpDomain.getPersistentProperty(key)
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
							log.warn "Association is not handled thus this object may not be seeded"
						}
					}
					// if domain class property type is an enum, transform value into the appropriate enum type
					else if (tmpProp.isEnum() && value instanceof String) {
						setSeedValue(saveData, key, Enum.valueOf(tmpProp.referencedPropertyType, value))
					}
					else {
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
				value = value.clone()
				if(value.containsKey('domainClass')) {
					value.remove('domainClass')
				}
				def tmpObj = findSeedObject(domain, value) 
				if(tmpObj) {
					data[key] = tmpObj
				} else {
					log.warn("Seed: Unable to locate domain Object ${domain} with criteria ${value}");
				}
			} else if(value instanceof List) {
				data[key] = value.collect {
					def tmpSeedMeta = it.clone().remove(getMetaKey())
					tmpCriteria = it
					if(tmpSeedMeta && tmpSeedMeta['criteria']==true) {
						it.each{ k, val  ->
							setSeedValue(tmpCriteria,k,val)
						}
					}
					def tmpObj = findSeedObject(domain, tmpCriteria)
					if(!tmpObj) {
						log.warn("Seed: Unable to locate domain Object ${domain} with criteria ${tmpCriteria}");
					} 
					return tmpObj
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
			if(!seedObject && key != getMetaKey()) {
				log.warn("Seed: Unable to locate domain Object ${tmpMatchDomain ?: key} with criteria ${value}");
			}
			if(tmpObjectMeta && tmpObjectMeta['useId']==true)
				seedObject = seedObject?.id
			else if(tmpObjectMeta && tmpObjectMeta['useValue'])
				seedObject = seedObject[tmpObjectMeta['useValue']]
			else if(tmpObjectMeta && tmpObjectMeta['useClosure'])
				seedObject = tmpObjectMeta['useClosure'](seedObject)
			else if(tmpObjectMeta && tmpObjectMeta['property'])
				seedObject = seedObject?."${tmpObjectMeta['property']}"
			if(seedObject) {
				data[key] = seedObject
			} 
		} else if(value instanceof CharSequence && value.toString().indexOf('$') >= 0) {
			data[key] = new GStringTemplateEngine().createTemplate(value.toString()).make(getDomainBindingsForGString()).toString()
		} else if(value instanceof Closure) {
			data[key] = value.call(data)
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
			tmpOpts.remove('domainClass')
			rtn = tmpInstance.findWhere(opts,[readOnly:true])
			if(rtn) {
				if(tmpMeta?.useId == true)
					rtn = rtn.id
				if(tmpMeta?.containsKey('property')) {
					rtn = rtn."${tmpMeta.property}"
				}
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
						if(!tmpObj.save(flush:true)) {
							def errors = []
							if(tmpObj.errors.hasErrors()) {
								errors = tmpObj.errors.allErrors.collect {err->
									" - " + messageSource.getMessage(err,Locale.ENGLISH) // need to get real local
						        }
							}
							log.error("Seed Error Saving ${tmpObj.toString()}\n${errors.join("\n")}")
						}
						
					}
					tmpObj.discard()
					
				}
			} else {
				tmpObj = domain.newInstance()
				applyChanges(tmpObj, config)

				if(!tmpObj.save(flush:true, insert:true)) {
					def errors = []
					if(tmpObj.errors.hasErrors()) {
						errors = tmpObj.errors.allErrors.collect {err->
							" - " + messageSource.getMessage(err,Locale.ENGLISH) // need to get real local
				        }
					}
					log.error("Seed Error Inserting ${tmpObj.toString()}\n${errors.join("\n")}")
				}
				tmpObj.discard()
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

	String getSeedSetName(str, plugin='') {
		plugin = plugin ? "${plugin}." : ''
		def rtn = str
		def tmpIndex = rtn?.lastIndexOf('.')
		if(tmpIndex > -1)
		  rtn = rtn.substring(0, tmpIndex)
		return rtn
	}

	private boolean isPluginExcluded(name) {
		if(getConfig().skipPlugins) {
			return true
		}
		def excluded = getConfig().excludedPlugins ?: []
		return excluded.find { it == name}
	}

	private boolean isSeedFileExcluded(name) {
		def excluded = getConfig().excludedSeedFiles ?: []
		return excluded.find { it == name || it == FNU.getBaseName(name) || it == FNU.getName(name)}
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
					if(!tmpFile.isDirectory() && tmpFile.name.endsWith('.groovy') && !isSeedFileExcluded(tmpFile.name))
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

	def gormFlush(session) {
		session = session ?: sessionFactory.currentSession
		session.clear()
		session.flush()
		propertyInstanceMap.get().clear()
	}

	/**
	 * Depth-first traversal of seed dependencies.
	 * @param set the seed set being processed.
	 * @param seedSetsLeft the seed sets left to process.  As a seed is
	 * processed it is removed from the list to avoid duplicate processing.
	 * @param seedOrder an array built as the process runs.  Contains the
	 * order in which the seed files were processed.
	 */
	private seedSetProcess(set, seedSetsLeft, seedSetsByPlugin, seedSetsByName, seedOrder=[]) {
		if(!set) return
		def setKey = buildSeedSetKey(set.name, set.plugin)
		// if this set has dependencies, process them first
		if(set.dependsOn) {
			// println "\tdependencies : ${set.dependsOn.join(', ')}"
			set.dependsOn.each { depSeed ->
				def deps = []
				// if the plugin is specified, construct the correct key
				if(depSeed.contains('.')) {
					def plugin = depSeed.substring(0, depSeed.indexOf('.'))
					deps = [buildSeedSetKey(plugin, depSeed.substring(plugin.length() + 1))]
				} else { // in case of ambiguity find all seeds with matching name
					def sets = seedSetsByName[depSeed]
					deps = sets.collect { "${it.plugin}.${it.name}" }
				}
				if(!deps) {
					log.warn("Cannot Resolve Dependency (${depSeed})")
				}
				deps.each {dep ->	seedSetProcess(seedSetsLeft[dep], seedSetsLeft, seedSetsByPlugin, seedSetsByName, seedOrder)}
			}
		}

		// if this seed set is in the list, run it
		def seedCheck = checkChecksum(setKey)
		if(seedSetsLeft[setKey] && 
			 (seedCheck?.checksum != set.checksum)) {
			log.info "Processing $setKey"
			SeedMeChecksum.withNewSession { session -> 
				try {
					set.seedList.each this.&processSeedItem
					updateChecksum(seedCheck, set.checksum)
					seedSetsLeft[setKey] = null
					seedSetsLeft.remove(setKey)
					seedOrder << set.name
					gormFlush(session)
				} catch(setError) {
					log.error("error processing seed set ${set.name}",setError)
				}
			}
			
		}
	}

	private checkChecksum(seedName) {
		def rtn = [checksum:null]
		// don't require that the domain be available, e.g. if the user of seed doesn't have
		// create privileges don't blow up
		try {
			rtn = SeedMeChecksum.findOrCreateWhere(seedName: seedName)
		} catch(e) {
		}
	}

	private updateChecksum(seedCheck, newChecksum) {
		// again, don't require that the SeedMeChecksum domain be around
		try {
			seedCheck.checksum = newChecksum
			seedCheck.save()
		} catch(e) {
		}
	}

	private buildSeedSetKey(name, pluginName) {
		// dependency names may already contain the plugin name, so check first
		if(name.contains('.')) return name
		else "${pluginName ? "${pluginName}." : ''}${name}"
	}
}
