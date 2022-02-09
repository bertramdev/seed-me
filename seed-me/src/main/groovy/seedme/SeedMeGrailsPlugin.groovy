package seedme

import grails.plugins.*
import grails.util.Environment
import java.lang.management.ManagementFactory
import java.lang.management.RuntimeMXBean

class SeedMeGrailsPlugin extends Plugin {

    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "3.3.0 > *"
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
        "grails-app/views/error.gsp"
    ]

    // TODO Fill in these fields
    def title = "Seed Me" // Headline display name of the plugin
    def author = "Your name"
    def authorEmail = ""
    def description = '''\
Brief summary/description of the plugin.
'''
    def profiles = ['web']

    // URL to the plugin's documentation
    def documentation = "http://grails.org/plugin/seed-me"

    // Extra (optional) plugin metadata

    // License: one of 'APACHE', 'GPL2', 'GPL3'
//    def license = "APACHE"

    // Details of company behind the plugin (if there is one)
//    def organization = [ name: "My Company", url: "http://www.my-company.com/" ]

    // Any additional developers beyond the author specified above.
//    def developers = [ [ name: "Joe Bloggs", email: "joe@bloggs.net" ]]

    // Location of the plugin's issue tracker.
//    def issueManagement = [ system: "JIRA", url: "http://jira.grails.org/browse/GPMYPLUGIN" ]

    // Online location of the plugin's browseable source code.
//    def scm = [ url: "http://svn.codehaus.org/grails-plugins/" ]

    Closure doWithSpring() { {->
            // TODO Implement runtime spring config (optional)
        }
    }

    void doWithDynamicMethods() {
        // TODO Implement registering dynamic methods to classes (optional)
    }

    void doWithApplicationContext() {
        // TODO Implement post initialization spring config (optional)
    }

    def watchedResources = "file:./seed/*.groovy"

	@Override
    void onChange(Map<String, Object> event) {
        def autoSeed = grailsApplication.config.getProperty('grails.plugin.seed.autoSeed', Boolean, true)
        if(!(autoSeed instanceof Boolean)) {
            autoSeed = true
        }
        if(autoSeed && event.source && event.ctx) {
            event.ctx.seedService.installSeedData()
        }
    }


	@Override
    void onStartup(Map<String, Object> event) {
	    def seedService = applicationContext['seedService']
        def autoSeed = grailsApplication.config.getProperty('grails.plugin.seed.autoSeed', Boolean, false)
        if(!(autoSeed instanceof Boolean)) {
            autoSeed = false
        }
	    if(!Environment.isDevtoolsRestart() && (autoSeed == true || System.getProperty('autoSeed', 'false') == 'true')) {
            SeedMeChecksum.withNewSession { session ->
                seedService.installSeedData()
            }
            
        }
    }


    void onConfigChange(Map<String, Object> event) {
        // TODO Implement code that is executed when the project configuration changes.
        // The event is the same as for 'onChange'.
    }

    void onShutdown(Map<String, Object> event) {
        // TODO Implement code that is executed when the application shuts down (optional)
    }
}
