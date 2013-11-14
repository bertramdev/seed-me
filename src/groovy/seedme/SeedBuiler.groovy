package seedme

/**
 * A Groovy builder to generate seed data maps
 * @author bdwheeler
 */
class SeedBuilder extends BuilderSupport {

	def seedList
	def currentRow
	def seedItem
	def dependsOn = []
	def seeding = false

	@Override
	protected createNode(name) {
		if(name == 'build')
			return name
		if(name == 'seed') {
			seedList = []
			seeding = true
			return name
		}
		return null
	}

	@Override
	protected createNode(name, value ) {
		if(name == 'meta')
			currentRow.meta = value
		else if(currentRow == null && name == 'dependsOn')
			dependsOn = value
		else
			currentRow.data[name] = value
		return name
	}

	@Override
	protected createNode( name, Map attribs ) {
		if(seeding) {
			if(seedItem == null) {
				if(name == 'dependsOn') {
					dependsOn = attribs
				} else {
					currentRow = [domainClass:name, data:attribs, meta:[:]]
					if(attribs.meta) {
						currentRow.meta = currentRow.data.remove('meta')
					}
					seedItem = name
				}
			} else {
				if(name == 'meta')
					currentRow.meta = attribs
				else
					currentRow.data[name] = attribs
			}
		}
		return name
	}

	@Override
	protected createNode(name, Map attributes, value) {}

	@Override
	protected void setParent(parent, child) {}

	@Override
	void nodeCompleted( parent, child ) {
		if(seeding && (child == seedItem)) {
			seedList << currentRow
			seedItem = null
		} else if (child == 'seed' ) {
			seeding = false
		}
	}
}
