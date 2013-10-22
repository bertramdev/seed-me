class SeedMeGrailsPlugin {
    def version       = "0.2.0"
    def grailsVersion = "2.0 > *"
    def pluginExcludes = [
        "grails-app/views/error.gsp",
		"grails-app/domain/com/procon/nspire/seed/Child.groovy",
		"grails-app/domain/com/procon/nspire/seed/ChildParentRequired.groovy",
		"grails-app/domain/com/procon/nspire/seed/ParentHasManyChildren.groovy",
		"grails-app/domain/com/procon/nspire/seed/ParentHasOneChild.groovy"
    ]

    def title           = "SeedMe Plugin"
    def author          = "Brian Wheeler"
    def authorEmail     = "bdwheeler@gmail.com"
    def description     = "Implements a standard convention for adding seed data to your application."
    def documentation   = "http://github.com/bertramdev/seed-me"
    def license         = "APACHE"
    def organization    = [ name: "Bertram Capital", url: "http://www.bertramcapital.com/" ]
    def developers      = [ [ name: "Brian Wheeler", email: "bwheeler@bcap.com" ], [name: 'David Estes', email: 'destes@bcap.com'], [name: 'Jordon Saardchit',email: 'jsaardchit@bcap.com']]
    def scm             = [ url: "http://github.com/bertramdev/seed-me" ]
    def issueManagement = [ system: "GITHUB", url: "http://github.com/bertramdev/seed-me/issues" ]




    def doWithApplicationContext = { applicationContext ->
        def runOnLoad = application.config.grails.seed.containsKey('runOnLoad') ? application.config.grails.seed.runOnLoad : true
        if(runOnLoad)
            applicationContext.getBean('seedService')?.installSeedData()
    }

    def watchedResources = "file:./seed/*.groovy"

    def onChange = { event ->
        if (event.source) {
            // applicationContext.getBean('seedService')?.installSeedData()
            if (event.ctx) {
                event.ctx.getBean('seedService')?.installSeedData()
            }
        }
    }

}
