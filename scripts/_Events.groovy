
eventCreateWarStart = { name, stagingDir ->
	event("StatusUpdate",["Moving Seeds Into War"])

	def conf = config.grails.plugin.seed
	def seedInput = conf?.root ?: 'seed'
	def seedOutputDir = new File(stagingDir, 'seed')
	seedOutputDir.mkdirs()
	//println("copying seed from ${seedInput} to ${seedOutputDir}")
	ant.copy(todir:seedOutputDir.path, verbose:true) {
		fileset dir:seedInput
	}
}
