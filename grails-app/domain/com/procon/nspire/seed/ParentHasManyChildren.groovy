package com.procon.nspire.seed

/**
 * @author WChu
 */
class ParentHasManyChildren {
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
