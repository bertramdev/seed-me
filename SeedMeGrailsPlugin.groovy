class SeedMeGrailsPlugin {

	def version        = "0.6.6"
	def grailsVersion  = "2.0 > *"
	def pluginExcludes = [
		"src/seed/*",
		"grails-app/domain/seedme/Child.groovy",
		"grails-app/domain/seedme/ChildParentRequired.groovy",
		"grails-app/domain/seedme/ParentHasManyChildren.groovy",
		"grails-app/domain/seedme/ParentHasOneChild.groovy"
	]

	def title           = "SeedMe Plugin"
	def description     = "Implements a standard convention for adding seed data to your application."
	def documentation   = "http://github.com/bertramdev/seed-me"
	def license         = "APACHE"
	def organization    = [ name: "Bertram Capital", url: "http://www.bertramcapital.com/" ]
	def developers      = [
		[name: 'Brian Wheeler', email: 'bwheeler@bcap.com'],
		[name: 'David Estes', email: 'destes@bcap.com'],
		[name: 'Jordon Saardchit', email: 'jsaardchit@bcap.com'],
		[name: 'Jeremy Crosbie', email: 'jcrosbie@bcap.com'],
		[name: 'Jeremy Leng', email: 'jleng@bcap.com'],
		[name:' William Chu', email: 'wchu@bcap.com']]

	def scm             = [ url: "http://github.com/bertramdev/seed-me" ]
	def issueManagement = [ system: "GITHUB", url: "http://github.com/bertramdev/seed-me/issues" ]

	def doWithApplicationContext = { applicationContext ->

	}

	def watchedResources = "file:./seed/*.groovy"

	def onChange = { event ->
		def autoSeed = application.config.grails.seed.autoSeed
		if(!(autoSeed instanceof Boolean)) {
			autoSeed = true
		}
		if(autoSeed && event.source && event.ctx) {
			event.ctx.seedService.installSeedData()
		}
	}

}
