includeTargets << grailsScript("_GrailsBootstrap")

target(runSeed: "Installs seed files") {
	depends(configureProxy,compile, packageApp, bootstrap)

	appCtx.seedService.installSeedData()
}

setDefaultTarget(runSeed)
