includeTargets << grailsScript("_GrailsBootstrap")



target(runSeed: "Creates a seed file!") {
	depends(configureProxy,compile, packageApp, bootstrap)
	def grailsApplication = ApplicationHolder.getApplication()
	def ctx = grailsApplication.mainContext
	def serviceClass = grailsApplication.getClassForName("com.procon.nspire.seed.SeedService")
	def serviceClassMethod = serviceClass.metaClass.getMetaMethod("installSeedData")
	def service = ctx.getBean("seedService")
	serviceClassMethod.invoke(service, null)
}

setDefaultTarget(runSeed)

