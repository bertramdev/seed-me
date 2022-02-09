package seedme

import grails.util.Environment
import groovy.text.GStringTemplateEngine

import java.security.MessageDigest
import java.security.DigestInputStream
import java.util.zip.*
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
	static zipPackageExtensions = ['zip', 'morpkg', 'mpkg', 'mopkg']
	static packageManifestName = 'package-manifest.json'
	
	//triggers processing of seed files included inside the application
	void installSeedData() {
		def (seedFiles, seedTemplates) = getSeedFiles()
		def seedOpts = [:]
		installSeedData(seedFiles, seedTemplates, seedOpts)
	}

	//triggers processing of specified target seed file included inside the application
	void installSeedData(String target) {
		def (seedFiles, seedTemplates) = getSeedFiles()
		def seedOpts = [target:target]
		installSeedData(seedFiles, seedTemplates, seedOpts)
	}

	//triggers processing of already loaded files - for external app managing where to load from
	void installSeedData(Collection seedFiles, Collection seedTemplates, Map opts) {
		log.info("seedService.installSeedData")
		log.info("seedService - processing ${seedFiles?.size()} files")
		def startTime = new Date().time
		def (seedSets, seedSetByPlugin, seedSetsByName) = buildSeedSets(seedFiles, opts)
		//setup run list
		def seedRunSets
		// make a copy so we can remove items from one list as they are processed, another to iterate through
		def seedRunningSets
		if(opts.target) {
			seedRunSets = [:]
			def seedTarget = opts.target
			if(seedTarget.contains('.')) {
				def plugin = seedTarget.substring(0, seedTarget.indexOf('.'))
				String seedName = seedTarget.substring(plugin.length() + 1)
				plugin = plugin.replaceAll(/\B[A-Z]/) { '-' + it }.toLowerCase()
				String seedAddress = plugin + '.' + seedName
				seedRunSets."$seedAddress" = seedSets."$seedAddress"
				seedRunningSets = seedRunSets.clone()
			} else { // in case of ambiguity find all seeds with matching target
				def sets = seedSetsByName[seedTarget]
				sets.each { name, set ->
					seedRunSets["${set.plugin}.${set.name}"] = set
				}
				seedRunningSets = seedRunSets.clone()
			}
		} else {
			seedRunSets = seedSets
			seedRunningSets = seedRunSets.clone()
		}
		seedRunSets.each { name, set ->
			seedSetProcess(set, seedRunningSets, seedSetByPlugin, seedSetsByName, seedTemplates)
		}
		log.info("installSeedData completed in {}ms", new Date().time - startTime)
	}

	/**
	* Allows creating seed by passing in a string of content
	* @param String seedContent - seed file contents as a string
	*/
	def installExternalSeed(seedContent) {
		try {
			def tmpSet = buildSeedSet('external', seedContent, 'external', 'groovy', false)
			log.info("processing external seed")
			tmpSet.seedList?.each { tmpSeed ->
				try {
					processSeedItem(tmpSet, tmpSeed)
				} catch(e) {
					log.error("error processing seed item ${tmpSeed}", e)
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
			def tmpSet = [seedList:[], dependsOn:[], name:'name', plugin:null, seedVersion:null]
			def tmpBuilder = new SeedBuilder()
			tmpBuilder.seed(seedData)
			tmpSet.dependsOn = tmpBuilder.dependsOn
			tmpSet.seedVersion = tmpBuilder.seedVersion
			tmpSet.seedList.addAll(tmpBuilder.seedList)
			log.info("processing inline seed")
			tmpSet.seedList?.each { tmpSeed ->
				try {
					processSeedItem(tmpSet, tmpSeed)
				} catch(e) {
					log.error("error processing seed item ${tmpSeed}", e)
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
		return buildSeedSets(seedFiles, [:])
	}

	private buildSeedSets(seedFiles, Map opts) {
		def seedSets = [:]
		def byPlugin = [:]
		def byName = [:]
		def filesChanged = false
		if(opts?.force == true) {
			filesChanged = true
		} else {
			//check for changes
			for(row in seedFiles) {
				def tmpFile = row.file
				def tmpSeedName = getSeedSetName(row.name)
				def pluginName = row.plugin ?: 'application'
				def tmpSetKey = buildSeedSetKey(tmpSeedName, pluginName)
				def checksum = row.checksum ?: (tmpFile ? getMD5FromStream(tmpFile.newInputStream()) : (row.content ? getMD5FromContent(row.content) : null))
				if(checkChecksum(tmpSetKey).checksum != checksum) {
					filesChanged = true
					//have a change - break to process
					break
				}
			}
		}
		//if changes do the processing
		if(filesChanged) {
			for(row in seedFiles) {
				//change to call below method
				def tmpFile = row.file
				def pluginName = row.plugin ?: 'application'
				def tmpContent = row.content ?: tmpFile?.getText()
				def tmpType = row.type
				def tmpSeedName = getSeedSetName(row.name)
				byPlugin[pluginName] = byPlugin[pluginName] ?: [:]
				byName[tmpSeedName] = byName[tmpSeedName] ?: []
				def tmpSetKey = buildSeedSetKey(tmpSeedName, pluginName)
				def checksum = row.checksum ?: (tmpFile ? getMD5FromStream(tmpFile.newInputStream()) : (row.content ? getMD5FromContent(row.content) : null))
				def seedCheck = checkChecksum(tmpSetKey)
				def checksumMatched = (opts.force != true && seedCheck.checksum == checksum)
				def tmpSeedSet = buildSeedSet(tmpSeedName, tmpContent, pluginName, tmpType, checksumMatched)
				tmpSeedSet.seedFile = row
				tmpSeedSet.seedCheckId = seedCheck.id
				seedSets[tmpSetKey] = tmpSeedSet
				byPlugin[pluginName][tmpSetKey] = tmpSeedSet
				byName[tmpSeedName] << tmpSeedSet	
			}
		}
		//done - return all sets
		return [seedSets, byPlugin, byName]
	}

	@CompileStatic
	private String getMD5FromStream(InputStream istream) {
		def rtn
		DigestInputStream digestStream
		MessageDigest digest
		try {
			byte[] buffer = new byte[1024]
			int nRead
			digest = MessageDigest.getInstance('MD5')
			digestStream = new DigestInputStream(istream, digest)
			while((nRead = digestStream.read(buffer, 0, buffer.length)) != -1) {
				// noop (just to complete the stream)
			}
			rtn = digest.digest().encodeHex().toString() 
		} catch(IOException ioe) {
			//Its ok if the stream is already closed so ignore error
		} finally {
			try { digestStream?.close() } catch(Exception ex) { /*ignore if already closed this reduces open file handles*/ }
		}
		return rtn
	}

	@CompileStatic
	private getMD5FromContent(String digestString) {
		def rtn
		MessageDigest md
		try {
			md = MessageDigest.getInstance('MD5')
			md.update(digestString.bytes)
			byte[] checksum = md.digest()
			rtn = checksum.encodeHex().toString() 
		} catch(e) {
			//failed
		}
		return rtn
	}

	private buildSeedSet(name, seedContent) {
		return buildSeedSet(name, seedContent, null, 'groovy', false)
	}

	private buildSeedSet(name, seedContent, plugin, type, Boolean checksumMatched) {
		def rtn = [seedList:[], dependsOn:[], name:name, plugin:plugin, seedVersion:null]
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
					rtn.seedVersion = tmpBuilder.seedVersion
					rtn.seedList.addAll(tmpBuilder.seedList)
				}
			} else if(type == 'json') {
				if(!checksumMatched) {
					//parse it - expected format: { dependsOn:[], seed:{domainClass:[]}}
					def parsedSeed = seedContent ? new groovy.json.JsonSlurper().parseText(seedContent) : [:]
					rtn.dependsOn = parsedSeed?.dependsOn ?: []
					rtn.seedVersion = parsedSeed.seedVersion
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
					rtn.seedVersion = parsedSeed.seedVersion
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
			log.error("error building seed set ${name}", e)
		}
		return rtn
	}

	def processSeedItem(seedSet, seedItem, templates) {
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
								setSeedValue(seedSet, saveData, key, value, templates, subDomain)
							} else if(value instanceof List) {
								setSeedValue(seedSet, saveData, key, value, templates, subDomain)
							}
						} else if((tmpProp instanceof ToOne) || (tmpProp instanceof OneToOne )) {
							if(value instanceof Map) {
								setSeedValue(seedSet, saveData, key, value, templates, subDomain)
							}
						} else if(tmpProp instanceof ManyToMany) {
							if(value instanceof Map) {
								setSeedValue(seedSet, saveData, key, value, templates, subDomain)
							} else if(value instanceof List) {
								setSeedValue(seedSet, saveData, key, value, templates, subDomain)
							}
						} else if(tmpProp instanceof ManyToOne) {
							if(value instanceof Map) {
								setSeedValue(seedSet, saveData, key, value, templates, subDomain)
							}
						} else if(tmpProp instanceof Basic) {
							setSeedValue(seedSet, saveData, key, value, templates, subDomain)
						} else {
							log.warn "Association is not handled thus this object may not be seeded: ${tmpProp.getName()} type: ${tmpProp.getType()?.getName()}"
						}
					} else if(tmpProp.type.isEnum() && value instanceof String) {
						// if domain class property type is an enum, transform value into the appropriate enum type
						setSeedValue(seedSet, saveData, key, Enum.valueOf(tmpProp.type, value), templates)
					} else {
						//basic value
						setSeedValue(seedSet, saveData, key, value, templates)
					}
				} else {
					setSeedValue(seedSet, saveData, key, value, templates)
				}
			}
			def opts = [:]
			tmpMeta.entrySet().each() { it ->
				if(it.key != "key") opts << it
			}
			createSeed(tmpDomain, tmpMeta.key, saveData, opts)
		}
	}

	def setSeedValue(Map seedSet, Map data, String key, Object value) {
		setSeedValue(seedSet, data, key, value, [], null)
	}

	def setSeedValue(Map seedSet, Map data, String key, Object value, Collection templates) {
		setSeedValue(seedSet, data, key, value, templates, null)
	}

	def setSeedValue(Map seedSet, Map data, String key, Object value, Collection templates, Object domain) {
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
					log.warn("seed: ${seedSet?.name} - unable to locate domain object ${domain} with criteria ${value}")
				}
			} else if(value instanceof List) {
				data[key] = value.collect {
					def tmpSeedMeta = it.clone().remove(getMetaKey())
					tmpCriteria = it
					if(tmpSeedMeta && tmpSeedMeta['criteria']==true) {
						it.each{ k, val  ->
							setSeedValue(seedSet, tmpCriteria, k, val, templates)
						}
					}
					def tmpObj = findSeedObject(domain, tmpCriteria)
					if(!tmpObj) {
						log.warn("seed: ${seedSet?.name} - unable to locate domain object ${domain} with criteria ${tmpCriteria}")
					}
					return tmpObj
				}.findAll{ it!=null }
			}
		//} else if (value instanceof Map && value[getMetaKey()]) {
		} else if(value instanceof Map && value.containsKey('domainClass')) {
			value = value.clone() //Dont want to simply remove keys in case this value is reused elsewhere
			def tmpMatchDomain = value.remove('domainClass')
			def tmpObjectMeta = value.remove(getMetaKey())
			if(tmpObjectMeta && tmpObjectMeta['criteria']==true) {
				value.each{ k , val ->
					setSeedValue(seedSet, tmpCriteria, k, val, templates)
				}
				value = tmpCriteria
			}
			def seedObject = findSeedObject(tmpMatchDomain ?: key, value)
			if(!seedObject && key != getMetaKey()) {
				log.warn("seed: ${seedSet?.name} - unable to locate domain object ${tmpMatchDomain ?: key} with criteria ${value}")
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
			//check the template map
			def templateName = value.value
			def templateMatch = templates?.find{ it.name == templateName }
			if(!templateMatch) {
				//sub folder - built the name
				def seedNameTokens = seedSet?.seedFile.name.tokenize('/')
				if(seedNameTokens.size() > 1) {
					templateName = seedNameTokens[0..-2].join('/') + '/' + templateName
					templateMatch = templates?.find{ it.name == templateName }
				}
			}
			if(templateMatch) {
				def templateContent = templateMatch.content
				if(!templateContent && templateMatch.file) {
					templateContent = templateMatch.file.getText('UTF-8')
				}
				//set the content
				data[key] = templateContent
			} else {
				log.warn("seed value template not found: ${value.value} - templateFile: ${templateName} - ${seedSet?.name}")
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
		return grailsApplication.config.getProperty('grails.plugin.seed',Map,[:])
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

	def getSeedFiles() {
		def (seedFiles, seedTemplates) = getClassPathSeedFiles()
		//exit if we have them from the classpath
		if(seedFiles)
			return [seedFiles, seedTemplates]
		//build the list based on environment
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
				//append the seed files
				appendSeedFiles(seedFiles, seedTemplates, pluginName, seedFolder, null, 0)
			}
		}
		seedFiles = seedFiles.sort{ a, b -> a.name <=> b.name }
		return [seedFiles, seedTemplates]
	}

	def appendSeedFiles(Collection seedFiles, Collection seedTemplates, String pluginName, File seedFolder, String parentPath, Integer level) {
		log.debug("appendSeedFiles: ${pluginName} - ${seedFolder} - ${parentPath}")
		try {
			def tmpEnvironmentFolder = getEnvironmentSeedPath()
			def env = tmpEnvironmentFolder ?: Environment.current.name
			//append files
			seedFolder?.eachFile { tmpFile ->
				if(!tmpFile.isDirectory() && !isSeedFileExcluded(tmpFile.name)) {
					def seedName = parentPath ? "${parentPath}/${tmpFile.name}" : tmpFile.name
					if(tmpFile.name.endsWith('.groovy'))
						seedFiles << [file:tmpFile, name:seedName, plugin:pluginName, type:'groovy']
					else if(tmpFile.name.endsWith('.json'))
						seedFiles << [file:tmpFile, name:seedName, plugin:pluginName, type:'json']
					else if(tmpFile.name.endsWith('.yaml') || tmpFile.name.endsWith('.yml'))
						seedFiles << [file:tmpFile, name:seedName, plugin:pluginName, type:'yaml']
					else if(tmpFile.name.endsWith('.zip') || tmpFile.name.endsWith('.morpkg') || tmpFile.name.endsWith('.mopkg') || tmpFile.name.endsWith('.mpkg'))
						appendPackageFiles(seedFiles, seedTemplates, pluginName, tmpFile)
				}
			}
			//append folders
			seedFolder?.eachDir { tmpFolder ->
				log.debug("appendSeedFolder: ${pluginName} - ${tmpFolder} - ${parentPath}")
				if(tmpFolder.name == env || (tmpFolder.name == 'env-' + env) || //if the name matches for legacy or env- matches..
						(!environmentList.contains(tmpFolder.name) && !tmpFolder.name.startsWith('env-'))) { //process sub folders that environment specific
					//templates?
					if(tmpFolder.name == 'templates' || tmpFolder.name.endsWith('/templates')) {
						//append templates
						def newParentPath = parentPath ? "${parentPath}/${tmpFolder.name}" : tmpFolder.name
						appendSeedTemplates(seedTemplates, pluginName, tmpFolder, newParentPath, level)
					} else if(tmpFolder.name == 'packages' || tmpFolder.name.endsWith('/packages')) {
						//append packages
						appendSeedFiles(seedFiles, seedTemplates, pluginName, tmpFolder, parentPath, level)
					} else {
						//append contents
						def newParentPath = parentPath ? "${parentPath}/${tmpFolder.name}" : tmpFolder.name
						appendSeedFiles(seedFiles, seedTemplates, pluginName, tmpFolder, newParentPath, level + 1)
					}
				}
			}
		} catch(e) {
			log.error("error on append seed files: ${e}", e)
		}
	}

	def appendSeedTemplates(Collection seedTemplates, String pluginName, File templateFolder, String parentPath, Integer level) {
		//append files
		templateFolder?.eachFile { tmpFile ->
			if(!tmpFile.isDirectory()) {
				//set the template name relative to where it was found
				def templateName = parentPath ? "${parentPath}/${tmpFile.name}" : tmpFile.name
				def templateItem = [plugin:pluginName, name:templateName, file:tmpFile]
				seedTemplates << templateItem
			}
		}
	}

	def getClassPathSeedFiles() {
		ClassLoader classLoader = Thread.currentThread().contextClassLoader
		def resources = classLoader.getResources('seeds.list')
		def seedList = []
		def templateList = []
		resources.each { URL res ->
			seedList += res?.text?.tokenize("\n") ?: []
		}
		def tmpEnvironmentFolder = getEnvironmentSeedPath() //configurable seed environment.
		def env = tmpEnvironmentFolder ?: Environment.current.name
		//get environment matching templates
		templateList = seedList.findAll{ item ->
			return item.contains('templates/')
		}
		//get environment matching seed
		seedList = seedList.findAll{ item -> 
			def itemArgs = item.tokenize('/')
			return item.startsWith("${env}/") || item.startsWith("env-${env}/") || itemArgs.size() == 1 || (!environmentList.contains(itemArgs[0]) && !item.contains('templates/'))
		}
		//build the list
		def seedFiles = []
		def seedTemplates = []
		//get templates
		templateList.each { templateName ->
			classLoader.getResources("seed/${templateName}")?.eachWithIndex { row, index ->
				def pluginName = (index == 0 ? null : "classpath:${index}")
				def seedItem = [plugin:pluginName, name:templateName, file:row, fileSource:'package']
				//get the content
				//seedItem.content = row.inputStream.getText('UTF-8')
				seedTemplates << seedItem
			}
		}
		//get seed
		seedList.each { seedName ->
			classLoader.getResources("seed/${seedName}")?.eachWithIndex { res, index ->
				def pluginName = (index == 0 ? null : "classpath:${index}")
				if(seedName.endsWith('.groovy'))
					seedFiles << [file:res, name:seedName, plugin:pluginName, type:'groovy', fileSource:'classLoader']
				else if(seedName.endsWith('.yaml') || seedName.endsWith('.yml'))
					seedFiles << [file:res, name:seedName, plugin:pluginName, type:'yaml', fileSource:'classLoader']
				else if(seedName.endsWith('.json'))
					seedFiles << [file:res, name:seedName, plugin:pluginName, type:'json', fileSource:'classLoader']	
				else if(seedName.endsWith('.zip') || seedName.endsWith('.morpkg') || seedName.endsWith('.mopkg') || seedName.endsWith('.mpkg'))
					appendPackageFiles(seedFiles, seedTemplates, pluginName, res)
			}
		}
		seedFiles = seedFiles.sort{ a, b -> a.name <=> b.name }
		return [seedFiles, seedTemplates]
	}

	def appendPackageFiles(Collection seedFiles, Collection seedTemplates, String pluginName, URL packageResource) {
		Map packageData = loadPackageFiles(packageResource)
		appendPackageFiles(seedFiles, seedTemplates, pluginName, packageData)
	}

	def appendPackageFiles(Collection seedFiles, Collection seedTemplates, String pluginName, File packageFile) {
		Map packageData = loadPackageFiles(packageFile)
		appendPackageFiles(seedFiles, seedTemplates, pluginName, packageData)
	}

	def appendPackageFiles(Collection seedFiles, Collection seedTemplates, String pluginName, Map packageData) {
		//load the package
		if(packageData.success == true) {
			def packageCode = packageData.manifest.code
			def packageFolder = packageData.manifest.folderName
			packageData.files.each { row ->
				def seedName = packageFolder ? packageFolder + '/' + row.name : row.name
				def seedItem = [plugin:pluginName, name:seedName, content:row.content, fileSource:'package']
				//get the type
				if(seedItem.name.endsWith('.groovy'))
        	seedItem.type = 'groovy'
        else if(seedItem.name.endsWith('.json'))
        	seedItem.type = 'json'
        else if(seedItem.name.endsWith('.yaml') || seedItem.name.endsWith('.yml'))
        	seedItem.type = 'yaml'
        //add to the file or template list
				if(row.name?.startsWith('templates/')) {
					//it's a template
					seedTemplates << seedItem
				} else {
					//its seed file - add it
					seedFiles << seedItem
				}
			}
		}
	}

	def loadPackageFiles(URL packageResource) {
		return loadPackageFiles(packageResource.newInputStream())
	}

	def loadPackageFiles(File packageFile) {
		return loadPackageFiles(packageFile.newInputStream())
	}

	def loadPackageFiles(InputStream inputStream) {
		//map of parsed data
		Map rtn = [success:false, files:[], manifest:null]
		def zipInput
		try {
			zipInput = new ZipInputStream(inputStream)
			//iterate the files
      ZipEntry zipEntry
      while((zipEntry = zipInput.getNextEntry()) != null) { // get next file and continue only if file is not null
        if(zipEntry.getName().equals(packageManifestName)) {
        	def manifestBytes = writeStreamToByteArray(zipInput)
        	def manifestContent = new String(manifestBytes)
        	def manifestData = new groovy.json.JsonSlurper().parseText(manifestContent)
        	rtn.manifest = manifestData
        	rtn.success = true
        } else {
        	def fileBytes = writeStreamToByteArray(zipInput)
        	def fileContent = new String(fileBytes)
        	def fileRow = [name:zipEntry.getName(), content:fileContent]
        	rtn.files << fileRow
				}
      }
	  } catch(e) {
			log.error("error loading package files: ${e}", e)
	  } finally{
	    if(zipInput)
	      zipInput.close()
	  }
	  log.debug("load package results: {}", rtn)
	  return rtn
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
	private seedSetProcess(Map set, Map seedSetsLeft, Map seedSetsByPlugin, Map seedSetsByName) {
		seedSetProcess(set, seedSetsLeft, seedSetsByPlugin, seedSetsByName, [], [])
	}

	private seedSetProcess(Map set, Map seedSetsLeft, Map seedSetsByPlugin, Map seedSetsByName, Collection templates) {
		seedSetProcess(set, seedSetsLeft, seedSetsByPlugin, seedSetsByName, templates, [])
	}

	private seedSetProcess(Map set, Map seedSetsLeft, Map seedSetsByPlugin, Map seedSetsByName, Collection templates, Collection seedOrder) {
		if(!set) return
		def setKey = buildSeedSetKey(set.name, set.plugin)
		// if this set has dependencies, process them first
		if(set.dependsOn) {
			log.debug("\tdependencies : ${set.dependsOn.join(', ')}")
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
					log.warn("cannot resolve dependency: (${depSeed})")
				}
				deps.each { dep ->	seedSetProcess(seedSetsLeft[dep], seedSetsLeft, seedSetsByPlugin, seedSetsByName, templates, seedOrder) }
			}
		}
		if(!set.checksumMatched) {
			//if this seed set is in the list, run it
			//def seedCheck = checkChecksum(setKey)
			if(seedSetsLeft[setKey]) { // && (seedCheck?.checksum != set.checksum)) {
				log.info("processing: ${setKey}")
				def seedTask = task {
					SeedMeChecksum.withNewSession { session ->
						SeedMeChecksum.withTransaction {
							try {
								set.seedList.each { seedItem ->
									processSeedItem(set, seedItem, templates)
								}
								updateChecksum(set.seedCheckId, set.checksum, setKey, set.seedVersion)
								seedSetsLeft[setKey] = null
								seedSetsLeft.remove(setKey)
								seedOrder << set.name
								gormFlush(session)
							} catch(setError) {
								log.error("error processing seed set ${set.name}", setError)
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
			rtn = cache?.find{ seed -> seed.seedName == seedName }
			if(!rtn) {
				rtn = new SeedMeChecksum(seedName:seedName)
			}
		} catch(e) {
			log.warn("Warning during Seed CheckSum Verification ${e.getMessage()}")
		}
		return rtn
	}

	private updateChecksum(seedCheckId, newChecksum, setKey, setVersion) {
		// again, don't require that the SeedMeChecksum domain be around
		try {
			def seedCheck
			if(!seedCheckId) {
				seedCheck = new SeedMeChecksum(seedName:setKey)
			} else {
				seedCheck = SeedMeChecksum.get(seedCheckId)
			}
			seedCheck.checksum = newChecksum
			seedCheck.seedVersion = setVersion
			seedCheck.save(flush:true)
		} catch(e) {
			log.warn("Error updating seed checksum record... ${e}", e)
		}
	}

	private buildSeedSetKey(name, pluginName) {
		// dependency names may already contain the plugin name, so check first
		if(name.contains('.')) return name
		else "${pluginName ? "${pluginName}." : ''}${name}"
	}

	private byte[] writeStreamToByteArray(InputStream inputStream) {
		def bytesOut = new ByteArrayOutputStream()
		writeStreamToOut(inputStream, bytesOut)
		return bytesOut.toByteArray()
	}

	private void writeStreamToOut(InputStream inputStream, OutputStream out) {
		byte[] buffer = new byte[102400]
		int len
		while((len = inputStream.read(buffer)) != -1) {
			out.write(buffer, 0, len)
		}
	}

	private lookupDomain(domainClassName) {
		def capitalized = domainClassName.capitalize()
		def domains = grailsDomainClassMappingContext.persistentEntities*.name
		grailsDomainClassMappingContext.getPersistentEntity(domains.find { it.endsWith(".${capitalized}") })
	}

}
