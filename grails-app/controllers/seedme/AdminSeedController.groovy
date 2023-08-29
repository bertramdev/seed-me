package seedme

class AdminSeedController {

	def seedService

	def index() { 
	
	}

	def install() {
		seedService.installSeedData()
		chain(controller:'adminSeed', action:'index')
	}

	def process() {
		try {
			def tmpData = params.seedFile
			seedService.installExternalSeed(tmpData)
			flash.message = "External seed installation successful."
		} catch(e) {
			log.error(e)
			flash.error = "Error during seed installation: ${e.message}"
		}
		chain(controller:'adminSeed', action:'index')
	}

}
