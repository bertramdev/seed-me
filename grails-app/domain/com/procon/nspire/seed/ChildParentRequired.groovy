package com.procon.nspire.seed
/**
 * Created with IntelliJ IDEA.
 * User: WChu
 * Date: 8/20/13
 * Time: 3:31 PM
 * To change this template use File | Settings | File Templates.
 */
class ChildParentRequired {

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
	ParentHasManyChildren parent

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
		return config ? new groovy.json.JsonSlurper().parseText(config) : [:]
	}

	def setConfigMap(configMap) {
		config = configMap ? configMap.encodeAsJSON() : null
	}

}
