
class BootStrap {

	def grailsApplication
	def seedService

	def init = { servletContext ->
		def autoSeed = grailsApplication.config.grails.plugin.seed.autoSeed
		if(!(autoSeed instanceof Boolean)) {
			autoSeed = false
		}
		if(autoSeed == true || System.getProperty('autoSeed', 'false') == 'true') {
			seedService.installSeedData()
		}
	}

	def destroy = {
	}

}
