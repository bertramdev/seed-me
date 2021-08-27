package seedme

class SeedMeChecksum {
	
	String seedName
	String checksum
	String seedVersion
	Date dateCreated
	Date lastUpdated

	static constraints = {
		seedName(nullable:false, unique:true)
		checksum(nullable:true)
		seedVersion(nullable:true)
	}

}