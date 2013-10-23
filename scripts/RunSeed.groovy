includeTargets << grailsScript("_GrailsBootstrap")

target(runSeed: "Creates a seed file") {
	depends(configureProxy,compile, packageApp, bootstrap)

	appCtx.seedService.installSeedData()
}

setDefaultTarget(runSeed)
