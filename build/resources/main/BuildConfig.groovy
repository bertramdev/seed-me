grails.project.work.dir = 'target'

grails.project.dependency.resolution = {

	inherits 'global'
	log 'warn'

	repositories {
		inherits true // Whether to inherit repository definitions from plugins
		grailsPlugins()
		grailsHome()
		mavenLocal()
		grailsCentral()
		mavenCentral()
	}

	dependencies {
	}

	plugins {
		build ':release:3.1.2', ':rest-client-builder:1.0.3', {
			export = false
		}
/*
		test(":build-test-data:2.0.6") {
			export = false
		}
*/
		compile(":hibernate:3.6.10.19") {    // hibernate only for integration tests
			export = false                     // don't make this available to the client app
		}
	}
}
