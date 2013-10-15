package com.procon.nspire.seed

/**
 * A Groovy builder to generate seed data maps
 * @author bdwheeler
 */
class SeedBuilder extends BuilderSupport {

	def seedList
	def currentRow
	def seedItem = null
	def dependsOn = []
	def seeding = false

	@Override
	protected Object createNode(Object name) {
		println("name: ${name}")
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
	protected Object createNode(Object name, Object value ) {
		if(name == 'meta')
			currentRow.meta = value
		else if(curentRow == null && name == 'dependsOn')
			dependsOn = value
		else
			currentRow.data[name] = value
		return name
	}

	@Override
	protected Object createNode( Object name, Map attribs ) {
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
	protected Object createNode(Object arg0, Map arg1, Object arg2) {
		return null
	}

	@Override
	protected void setParent(Object arg0, Object arg1) {

	}

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