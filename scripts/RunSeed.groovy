includeTargets << grailsScript("_GrailsBootstrap")

target(runSeed: "Installs seed files") {
	depends(configureProxy,compile, packageApp, bootstrap)

    def persistenceInterceptor = appCtx.containsBean('persistenceInterceptor') ? appCtx.persistenceInterceptor : null
    persistenceInterceptor?.init()

	appCtx.seedService.installSeedData()
}

setDefaultTarget(runSeed)
