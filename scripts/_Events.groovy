import org.codehaus.groovy.grails.plugins.GrailsPluginUtils


eventCreateWarStart = { name, stagingDir ->
	event("StatusUpdate",["Moving Seeds Into War"])
	def conf = config.grails.plugin.seed
	def seedRoot = conf?.root ?: 'seed'
	def seedPaths = [:]
	def excluded = conf.excludedPlugins ?: []
	def seedRootFile = new File(seedRoot)
	if(seedRootFile.exists()) {
		seedPaths.application = seedRoot
	}
	for(plugin in GrailsPluginUtils.pluginInfos) {
		if(!excluded.find { it == name}) {
			def seedPath = [plugin.pluginDir.getPath(), seedRoot].join(File.separator)
			def seedDir = new File(seedPath)
			if(seedDir.exists()) {
				seedPaths[plugin.name] = seedPath
			}
		}
	}

	println "Seeding in ${seedPaths}"
	seedPaths.each { seedPath ->
		println "Seeding in ${seedPath.key}"
		def seedOutputDir = new File(stagingDir, "seed/${seedPath.key}")
		seedOutputDir.mkdirs()
		println "Beginning Copy"
		ant.copy(todir:seedOutputDir.path, verbose:true) {
			fileset dir:seedPath.value
		}
	}

}
