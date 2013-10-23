includeTargets << grailsScript("_GrailsBootstrap")

target(runSeed: "Creates a seed file") {
	depends(configureProxy,compile, packageApp, bootstrap)

	def serviceClass = grailsApp.getClassForName("com.procon.nspire.seed.SeedService")
	def serviceClassMethod = serviceClass.metaClass.getMetaMethod("installSeedData")
	def service = appCtx.seedService
	serviceClassMethod.invoke(service, null)
}

setDefaultTarget(runSeed)
