
includeTargets << grailsScript("_GrailsBootstrap")
includeTargets << new File(proconSeedPluginDir, "scripts/_SeedMeHelpers.groovy")

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
	emberTemplate templateName: "seed.groovy", destination: [destinationPath,"${params.seedName}.groovy"].join(File.separator(), params: params
}
