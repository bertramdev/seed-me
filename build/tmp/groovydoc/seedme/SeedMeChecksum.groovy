package seedme

class SeedMeChecksum {
	String seedName
	String checksum
	Date dateCreated
	Date lastUpdated

	static constraints = {
		seedName(nullable:false, unique:true)
		checksum(nullable:true)
	}
}