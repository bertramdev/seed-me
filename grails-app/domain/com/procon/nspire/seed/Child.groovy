package com.procon.nspire.seed

import groovy.json.JsonSlurper

/**
 * @author WChu
 */
class Child {

	static transients = ['configMap']

	String code
	String category
	String name
	Boolean enabled  = true
	String referenceType
	String referenceId
	String styleFile
	String logoFile
	String config
	Boolean template = false

	static mapping = {
		cache true
	}

	static constraints = {
		code()
		category(nullable:true)
		name()
		referenceType()
		referenceId()
		styleFile(nullable:true)
		logoFile(nullable:true)
		config(nullable:true)
	}

	def getConfigMap() {
		return config ? new JsonSlurper().parseText(config) : [:]
	}

	def setConfigMap(configMap) {
		config = configMap ? configMap.encodeAsJSON() : null
	}
}
