includeTargets << grailsScript("_GrailsBootstrap")

includeTargets << new File(seedMePluginDir, "scripts/_CreateSeedFile.groovy")

target(createSeed: "Creates a seed file") {
	depends(configureProxy, compile, packageApp)

	if(!argsMap.params[0]) {
		println "Usage: grails create-seed [-e=development] [SeedName]"
		return
	}

	def arguments = [
		seedName: argsMap.params[0],
		environment: argsMap.e ?: null
	]
	createSeedFile(arguments)
}

setDefaultTarget(createSeed)
