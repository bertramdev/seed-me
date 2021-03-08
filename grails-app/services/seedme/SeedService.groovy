package seedme

import grails.util.Environment
import groovy.text.GStringTemplateEngine

import java.security.MessageDigest
import java.security.DigestInputStream
import javax.xml.bind.DatatypeConverter

import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.Basic
import org.grails.datastore.mapping.model.types.ManyToMany
import org.grails.datastore.mapping.model.types.ManyToOne
import org.grails.datastore.mapping.model.types.OneToMany
import org.grails.datastore.mapping.model.types.OneToOne
import org.grails.datastore.mapping.model.types.ToOne

import groovy.util.logging.Commons
import grails.util.BuildSettings
import org.apache.commons.io.FilenameUtils as FNU
import grails.plugins.GrailsPluginManager
import grails.core.GrailsApplication
import static grails.async.Promises.*
import groovy.util.logging.Slf4j
import groovy.transform.CompileStatic

@Slf4j
class SeedService {

	static transactional = false

	org.grails.datastore.mapping.model.MappingContext grailsDomainClassMappingContext
	
	GrailsApplication grailsApplication
	GrailsPluginManager pluginManager
	def sessionFactory
	def messageSource

	private checkSumLoaded =  new ThreadLocal()
	private checkSums = new ThreadLocal()

	static environmentList = ['dev', 'test', 'prod']

	void installSeedData() {
		
		log.info("seedService.installSeedData")
		def seedFiles = getSeedFiles()

		log.info("seedService - processing ${seedFiles?.size()} files")
		def startTime = new Date().time
		def (seedSets, seedSetByPlugin, seedSetsByName) = buildSeedSets(seedFiles)
		// make a copy so we can remove items from one list as they are processed, another to
		// iterate through
		def seedSetsToRun = seedSets.clone()

		seedSets.each { name, set ->
			seedSetProcess(set, seedSetsToRun, seedSetByPlugin, seedSetsByName)
		}

		log.info("installSeedData completed in {}ms",new Date().time - startTime)
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
					processSeedItem(tmpSet, tmpSeed)
				} catch(e) {
					log.error("error processing seed item ${tmpSeed}",e)
					throw e
				}
			}
			processSeedItem(tmpSet, tmpSet)
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
					processSeedItem(tmpSet, tmpSeed)
				} catch(e) {
					log.error("error processing seed item ${tmpSeed}",e)
					throw e
				}
			}
			processSeedItem(tmpSet, tmpSet)
		} catch(e) {
			//log.error(e)
			throw e
		}
	}

	private buildSeedSets(seedFiles) {
		def seedSets = [:], byPlugin = [:], byName = [:]
		def filesChanged = false
		for(seedFile2 in seedFiles) {
			def tmpFile    = seedFile2.file
			def tmpSeedName = getSeedSetName(seedFile2.name)
			def pluginName = seedFile2.plugin ?: 'application'
			def tmpSetKey = buildSeedSetKey(tmpSeedName, pluginName)
			def checksum = getMD5FromStream(tmpFile.newInputStream())
			if(checkChecksum(tmpSetKey).checksum != checksum) {
				filesChanged = true
				break
			}
		}

		if(filesChanged) {
			seedFiles.each { seedFile ->
				//change to call below method
				def tmpFile    = seedFile.file
				def pluginName = seedFile.plugin ?: 'application'
				def tmpContent = tmpFile.getText()
				def tmpType = seedFile.type
				def tmpSeedName = getSeedSetName(seedFile.name)
				byPlugin[pluginName] = byPlugin[pluginName] ?: [:]
				byName[tmpSeedName] = byName[tmpSeedName] ?: []
				def tmpSetKey = buildSeedSetKey(tmpSeedName, pluginName)
				def checksum = getMD5FromStream(tmpFile.newInputStream())
				
				def tmpSeedSet
				log.trace "seedFiles: ${seedFiles}"
				if(checkChecksum(tmpSetKey).checksum != checksum) {
					tmpSeedSet = buildSeedSet(tmpSeedName, tmpContent, pluginName, tmpType)	
				} else {
					tmpSeedSet = buildSeedSet(tmpSeedName, tmpContent, pluginName, tmpType, true)	
					// tmpSeedSet = [seedList: [], dependsOn: [], name: tmpSeedName, plugin: pluginName, checksum: checksum]
				}
				tmpSeedSet.seedFile = seedFile
				seedSets[tmpSetKey] = tmpSeedSet
				byPlugin[pluginName][tmpSetKey] = tmpSeedSet
				byName[tmpSeedName] << tmpSeedSet	
			}
		} else {
			return [[],[:],[:]]
		}
		return [seedSets, byPlugin, byName]
	}

	@CompileStatic
	private String getMD5FromStream(InputStream istream) {
		DigestInputStream digestStream
		MessageDigest digest
		try {
			byte[] buffer = new byte[1024]
			int nRead
			digest = MessageDigest.getInstance("MD5")
			digestStream = new DigestInputStream(istream,digest)
			while((nRead = digestStream.read(buffer, 0, buffer.length)) != -1) {
				// noop (just to complete the stream)
			}
		} catch(IOException ioe) {
			// Its ok if the stream is already closed so ignore error
		} finally {
			try { digestStream?.close() } catch(Exception ex) { /*ignore if already closed this reduces open file handles*/ }
		}
		return digest.digest().encodeHex().toString()
		
	}

	private buildSeedSet(name, seedContent, plugin = null, type = 'groovy', Boolean checksumMatched = false) {
		def rtn = [seedList:[], dependsOn:[], name:name, plugin:plugin]
		try {
			//common 
			rtn.checksum = MessageDigest.getInstance('MD5').digest(seedContent.bytes).encodeHex().toString()
			rtn.checksumMatched = checksumMatched
			//if the type is groovy
			if(type == 'groovy') {
				if(!checksumMatched) {
					def tmpBinding = new Binding()
					tmpBinding.setVariable("grailsApplication", grailsApplication)
					def tmpConfig = new GroovyShell(this.class.classLoader, tmpBinding).evaluate(seedContent, plugin ? "${plugin}:${name}" : name)
					def tmpBuilder = new SeedBuilder()
					tmpBuilder.seed(tmpBinding.getVariable('seed'))
					rtn.dependsOn = tmpBuilder.dependsOn
					rtn.checksumMatched = checksumMatched
					rtn.seedList.addAll(tmpBuilder.seedList)
				}
			} else if(type == 'json') {
				if(!checksumMatched) {
					//parse it - expected format: { dependsOn:[], seed:{domainClass:[]}}
					def parsedSeed = seedContent ? new groovy.json.JsonSlurper().parseText(seedContent) : [:]
					rtn.dependsOn = parsedSeed?.dependsOn ?: []
					//iterate the seed
					parsedSeed?.seed?.each { key, value ->
						//key is a domain - value is an array
						value?.each { seedItem ->
							def row = [domainClass:key, meta:[:]]
							if(seedItem.meta) {
								row.meta = seedItem.meta
								seedItem.remove('meta')
							}
							row.data = seedItem
							rtn.seedList << row
						}
					}
				}
			} else if(type == 'yaml') {
				if(!checksumMatched) {
					//parse it - expected format: { dependsOn:[], seed:{domainClass:[]}}
					def parsedSeed = seedContent ? new org.yaml.snakeyaml.Yaml().load(seedContent) : [:]
					rtn.dependsOn = parsedSeed?.dependsOn ?: []
					//iterate the seed
					parsedSeed?.seed?.each { key, value ->
						//key is a domain - value is an array
						value?.each { seedItem ->
							def row = [domainClass:key, meta:[:]]
							if(seedItem.meta) {
								row.meta = seedItem.meta
								seedItem.remove('meta')
							}
							row.data = seedItem
							rtn.seedList << row
						}
					}
				}
			} //TODO - add yaml!
		} catch(e) {
			log.error("error building seed set ${name}",e)
		}
		return rtn
	}

	def processSeedItem(seedSet, seedItem) {
		def tmpDomain = lookupDomain(seedItem.domainClass)
		def tmpMeta = seedItem.meta
		if(tmpDomain && tmpMeta.key) {
			def tmpData = seedItem.data
			def saveData = [:]
			tmpData.each { key, value ->
				def tmpProp = tmpDomain.getPropertyByName(key)
				if(tmpProp) {
					if(tmpProp instanceof Association) {
						def subDomain = tmpProp.associatedEntity
						if(tmpProp instanceof OneToMany) {
							if(value instanceof Map) {
								setSeedValue(seedSet, saveData, key, value, subDomain)
							} else if(value instanceof List) {
								setSeedValue(seedSet, saveData, key, value, subDomain)
							}
						} else if((tmpProp instanceof ToOne) || (tmpProp instanceof OneToOne )) {
							if(value instanceof Map) {
								setSeedValue(seedSet, saveData, key, value, subDomain)
							}
						} else if(tmpProp instanceof ManyToMany) {
							if(value instanceof Map) {
								setSeedValue(seedSet, saveData, key, value, subDomain)
							} else if(value instanceof List) {
								setSeedValue(seedSet, saveData, key, value, subDomain)
							}
						} else if(tmpProp instanceof ManyToOne) {
							if(value instanceof Map) {
								setSeedValue(seedSet, saveData, key, value, subDomain)
							}
						} else if(tmpProp instanceof Basic) {
							setSeedValue(seedSet, saveData, key, value, subDomain)
						} else {
							log.warn "Association is not handled thus this object may not be seeded: ${tmpProp.getName()} type: ${tmpProp.getType()?.getName()}"
						}
					}
					// if domain class property type is an enum, transform value into the appropriate enum type
					else if (tmpProp.type.isEnum() && value instanceof String) {
						setSeedValue(seedSet, saveData, key, Enum.valueOf(tmpProp.type, value))
					}
					else {
						setSeedValue(seedSet, saveData, key, value)
					}
				} else {
					setSeedValue(seedSet, saveData, key, value)
				}
			}
			def opts = [:]
			tmpMeta.entrySet().each() { it ->
				if(it.key != "key") opts << it
			}
			createSeed(tmpDomain, tmpMeta.key, saveData, opts)
		}
	}

	def setSeedValue(seedSet, data, key, value, domain = null) {
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
							setSeedValue(seedSet, tmpCriteria,k,val)
						}
					}
					def tmpObj = findSeedObject(domain, tmpCriteria)
					if(!tmpObj) {
						log.warn("Seed: Unable to locate domain Object ${domain} with criteria ${tmpCriteria}");
					} 
					return tmpObj
				}.findAll{it!=null}
			}
		//} else if (value instanceof Map && value[getMetaKey()]) {
		} else if(value instanceof Map && value.containsKey('domainClass')) {
			value = value.clone() //Dont want to simply remove keys in case this value is reused elsewhere
			def tmpMatchDomain = value.remove('domainClass')
			def tmpObjectMeta = value.remove(getMetaKey())
			if(tmpObjectMeta && tmpObjectMeta['criteria']==true) {
				value.each{ k , val ->
					setSeedValue(seedSet, tmpCriteria,k,val)
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
		} else if(value instanceof Map && value._literal == true) {
			data[key] = value.value
		} else if(value instanceof Map && value._template == true) {
			//load the content from a file
			if(seedSet?.seedFile?.file) {
				def parentFile = seedSet.seedFile.file.getParentFile()
				def templateFile = new File(parentFile, value.value)
				//println("loading seed template: ${templateFile.getPath()} - ${templateFile.exists()}")
				if(templateFile.exists()) {
					data[key] = templateFile.getText()
				} else {
					log.warn("seed value template not found: ${value.value}")
				}
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
			rtn = tmpInstance.findWhere(tmpOpts)
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
			BuildSettings.BASE_DIR + "/" + (getConfig()?.root ?: 'src/seed')
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

	String getSeedSetName(str, plugin='') {
		plugin = plugin ? "${plugin}." : ''
		def rtn = str
		def tmpIndex = rtn?.lastIndexOf('.')
		if(tmpIndex > -1)
		  rtn = rtn.substring(0, tmpIndex)
		return rtn
	}

	private boolean isPluginExcluded(name) {
		if(name == 'application'){
			return false
		}
		
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
			pluginManager.getAllPlugins()?.each { plugin ->
				try {
					if(!isPluginExcluded(plugin.name)) {
						def seedPath = [plugin.pluginDir.getPath(), seedRoot].join(File.separator)
						seedPaths[plugin.name] = seedPath
					}	
				} catch(exc) {
					//binary plugins wont work in grails 3 with current design
				}
                
            }
			
			seedPaths.application = seedRoot
		}
		return seedPaths
	}

	def getClassPathSeedFiles() {
		ClassLoader classLoader = Thread.currentThread().contextClassLoader
		def resources = classLoader.getResources('seeds.list')
		def seedList = []
        resources.each { URL res ->
            seedList += res?.text?.tokenize("\n") ?: []
        }
        def tmpEnvironmentFolder = getEnvironmentSeedPath() //configurable seed environment.
        def env = tmpEnvironmentFolder ?: Environment.current.name

        seedList = seedList.findAll{ item -> item.startsWith("${env}/") || item.indexOf('/') == -1 }

        def seedFiles = []
        seedList.each { seedName ->
        	classLoader.getResources("seed/${seedName}")?.eachWithIndex {res, index ->
        		seedFiles << [file: res, name: seedName, plugin: index == 0 ? null : "classpath:${index}"]
        	}
        }
        seedFiles = seedFiles.sort{ a,b -> a.name <=> b.name}
        return seedFiles
	}

	def getSeedFiles() {
		def seedFiles = getClassPathSeedFiles()
		if(seedFiles) {
			return seedFiles
		}
		def tmpEnvironmentFolder = getEnvironmentSeedPath() //configurable seed environment.
		def seedPaths = getSeedPathsByPlugin()
		def env = tmpEnvironmentFolder ?: Environment.current.name
		if(!seedPaths) {
			//log.error "Seed folder '${seedFolder.absolutePath}' not found"
		}
		seedPaths.each { seedPath ->
			def seedFolder = new File(seedPath.value)
			def pluginName = seedPath.key
			if(seedFolder.exists()) {
				seedFolder?.eachFile { tmpFile ->
					if(!tmpFile.isDirectory() && !isSeedFileExcluded(tmpFile.name)) {
						if(tmpFile.name.endsWith('.groovy'))
							seedFiles << [file:tmpFile, name:tmpFile.name, plugin:pluginName, type:'groovy']
						else if(tmpFile.name.endsWith('.json'))
							seedFiles << [file:tmpFile, name:tmpFile.name, plugin:pluginName, type:'json']
						else if(tmpFile.name.endsWith('.yaml'))
							seedFiles << [file:tmpFile, name:tmpFile.name, plugin:pluginName, type:'yaml']
					}
				}
				seedFolder?.eachDir { tmpFolder ->
					if(tmpFolder.name == env || (tmpFolder.name == 'env-' + env) || //if the name matches for legacy or env- matches..
							(!environmentList.contains(tmpFolder.name) && !tmpFolder.name.startsWith('env-'))) { //process sub folders that environment specific
						tmpFolder.eachFile { tmpFile ->
							if(!tmpFile.isDirectory() && !isSeedFileExcluded(tmpFile.name)) {
								if(tmpFile.name.endsWith('.groovy'))
									seedFiles << [file:tmpFile, name:tmpFile.name, plugin:pluginName, type:'groovy']
								else if(tmpFile.name.endsWith('.json'))
									seedFiles << [file:tmpFile, name:tmpFile.name, plugin:pluginName, type:'json']
								else if(tmpFile.name.endsWith('.yaml'))
									seedFiles << [file:tmpFile, name:tmpFile.name, plugin:pluginName, type:'yaml']
							}
						}
					}
				}
			}
		}
		seedFiles = seedFiles.sort{ a, b -> a.name <=> b.name }
		return seedFiles
	}

	def gormFlush(session) {
		session = session ?: sessionFactory.currentSession
		session.clear()
		session.flush()
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
		if(!set.checksumMatched) {
			// if this seed set is in the list, run it
			def seedCheck = checkChecksum(setKey)
			if(seedSetsLeft[setKey] && 
				 (seedCheck?.checksum != set.checksum)) {
				log.info "Processing $setKey"
				def seedTask = task {
					SeedMeChecksum.withNewSession { session ->
						SeedMeChecksum.withTransaction {
							try {
								set.seedList.each { seedItem ->
									processSeedItem(set, seedItem)
								}
								updateChecksum(seedCheck?.id, set.checksum, setKey)
								seedSetsLeft[setKey] = null
								seedSetsLeft.remove(setKey)
								seedOrder << set.name
								gormFlush(session)
							} catch(setError) {
								log.error("error processing seed set ${set.name}",setError)
							}
						}
					}
					return true
				}
				
				waitAll(seedTask)
			}
		}

		
	}

	private checkChecksum(seedName) {
		def rtn = [checksum:null]
		try {
			Boolean loaded = checkSumLoaded.get()
			if(!loaded) {
				checkSums.set(SeedMeChecksum.list())
				checkSumLoaded.set(true as Boolean)
			}
			def cache = checkSums.get()
			rtn = cache?.find{seed -> seed.seedName == seedName}
			if(!rtn) {
				rtn = new SeedMeChecksum(seedName: seedName)
			}
		} catch(e) {
			log.warn("Warning during Seed CheckSum Verification ${e.getMessage()}")
		}
		return rtn
	}

	private updateChecksum(seedCheckId, newChecksum, setKey) {
		// again, don't require that the SeedMeChecksum domain be around
		try {
			def seedCheck
			if(!seedCheckId) {
				seedCheck = new SeedMeChecksum(seedName: setKey)
			}
			else {
				seedCheck = SeedMeChecksum.get(seedCheckId)
			}
			
			seedCheck.checksum = newChecksum
			seedCheck.save(flush:true)
		} catch(e) {
			log.warn("Error updating seed checksum record... ${e}",e)
		}
	}

	private buildSeedSetKey(name, pluginName) {
		// dependency names may already contain the plugin name, so check first
		if(name.contains('.')) return name
		else "${pluginName ? "${pluginName}." : ''}${name}"
	}

	private lookupDomain(domainClassName) {
		def capitalized = domainClassName.capitalize()
		def domains = grailsDomainClassMappingContext.persistentEntities*.name
		grailsDomainClassMappingContext.getPersistentEntity(domains.find { it.endsWith(".${capitalized}") })
	}
}
