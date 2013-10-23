
includeTargets << grailsScript("_GrailsBootstrap")
includeTargets << new File(seedMePluginDir, "scripts/_SeedMeHelpers.groovy")

createSeedFile = { params ->
	def destinationPath = [basedir,"seed"]
	if(params.environment) {
		destinationPath << environment
	}
	destinationPath = destinationPath.join(File.separator)
	def destinationFile = new File(destinationPath)
	if(!destinationFile.exists()) {
		destinationFile.mkdirs()
	}
	seedTemplate templateName: "seed.groovy", destination: [destinationPath,"${params.seedName}.groovy"].join(File.separator(), params: params
}
