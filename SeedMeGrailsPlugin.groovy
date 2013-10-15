class ProconSeedGrailsPlugin {

    // the plugin version
    def version = "0.2-SNAPSHOT"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "2.2 > *"
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
        "grails-app/views/error.gsp",
		"grails-app/domain/com/procon/nspire/seed/Child.groovy",
		"grails-app/domain/com/procon/nspire/seed/ChildParentRequired.groovy",
		"grails-app/domain/com/procon/nspire/seed/ParentHasManyChildren.groovy",
		"grails-app/domain/com/procon/nspire/seed/ParentHasOneChild.groovy"
    ]

    // TODO Fill in these fields
    def title = "SeedMe Plugin" // Headline display name of the plugin
    def author = "bdwheeler"
    def authorEmail = "bdwheeler@gmail.com"
    def description = "Implements a standard convetion for adding seed data to your application."

    // URL to the plugin's documentation
    def documentation = "http://grails.org/plugin/procon-seed"



    def license = "APACHE"


    def organization = [ name: "Bertram Capital", url: "http://www.bertramcapital.com/" ]


    def developers = [ [ name: "Brian Wheeler", email: "bwheeler@bcap.com" ], [name: 'David Estes', email: 'destes@bcap.com']]

    // Location of the plugin's issue tracker.
//    def issueManagement = [ system: "JIRA", url: "http://jira.grails.org/browse/GPMYPLUGIN" ]

    // Online location of the plugin's browseable source code.
    def scm = [ url: "https://github.com/bertramdev/seed-me" ]

    def doWithWebDescriptor = { xml ->
        // TODO Implement additions to web.xml (optional), this event occurs before
    }

    def doWithSpring = {
        // TODO Implement runtime spring config (optional)
    }

    def doWithDynamicMethods = { ctx ->
        // TODO Implement registering dynamic methods to classes (optional)
    }

    def doWithApplicationContext = { applicationContext ->
        // TODO Implement post initialization spring config (optional)
        def runOnLoad = application.config.grails.proconSeed.containsKey('runOnLoad') ? application.config.grails.proconSeed.runOnLoad : true
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

    def onConfigChange = { event ->
        // TODO Implement code that is executed when the project configuration changes.
        // The event is the same as for 'onChange'.
    }

    def onShutdown = { event ->
        // TODO Implement code that is executed when the application shuts down (optional)
    }
}
