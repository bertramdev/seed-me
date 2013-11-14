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
		} catch(e) {
			log.error(e)
		}
		chain(controller:'adminSeed', action:'index')
	}

}
