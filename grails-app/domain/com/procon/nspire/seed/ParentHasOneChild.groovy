package com.procon.nspire.seed
/**
 * Created with IntelliJ IDEA.
 * User: WChu
 * Date: 8/20/13
 * Time: 3:31 PM
 * To change this template use File | Settings | File Templates.
 */
class ParentHasOneChild {
	String code
	String internalId
	String name
	Long defaultBrandId
	Boolean active = true
	Boolean visible = true
	Boolean syncLandmarks = false
	Boolean syncGeofences = false
	Boolean syncEvents = false
	String legacyPrefix //no longer used
	String extraSync //no longer used
	Child onlyChild

	static hasMany = [brands:Child]

	static mapping = {
		cache true
	}

	static constraints = {
		code()
		name()
		internalId(nullable:true)
		defaultBrandId(nullable:true)
		legacyPrefix(nullable:true)
		extraSync(nullable:true)
		syncLandmarks(nullable:true)
		syncGeofences(nullable:true)
		syncEvents(nullable:true)
//		onlyChild(nullable:true)
	}
}
