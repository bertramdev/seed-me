grails.project.work.dir = 'target'

grails.project.dependency.resolution = {

	inherits 'global'
	log 'warn'

	repositories {
		grailsCentral()
		mavenLocal()
		mavenCentral()
	}

	dependencies {
	}

	plugins {
		build ':release:2.2.1', ':rest-client-builder:1.0.3', {
			export = false
		}

		test(":build-test-data:2.0.6") {
			export = false
		}

		compile(":hibernate:$grailsVersion") {    // hibernate only for integration tests
			export = false                     // don't make this available to the client app
		}
	}
}
