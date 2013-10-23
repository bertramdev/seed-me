grails {
	plugin {
		seed {
			root = 'seed'
			environment = null
		}
	}
}

log4j = {
	error 'org.codehaus.groovy.grails',
			'org.springframework',
			'org.hibernate',
			'net.sf.ehcache.hibernate'
}
