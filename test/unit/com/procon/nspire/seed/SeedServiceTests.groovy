package com.procon.nspire.seed

import grails.buildtestdata.mixin.Build
import grails.test.mixin.Mock
import grails.test.mixin.TestFor
import seedme.SeedService

/**
 * See the API for {@link grails.test.mixin.services.ServiceUnitTestMixin} for usage instructions
 */
@TestFor(SeedService)
@Build([ParentHasOneChild, Child, ParentHasManyChildren, ChildParentRequired])
@Mock([ParentHasOneChild, Child, ParentHasManyChildren, ChildParentRequired])
class SeedServiceTests {

	private SeedService seedService = new SeedService()

	void testFindSeedObjectWithResult() {
		ParentHasManyChildren.build(code:'vehicleFinance')

		def parents = ParentHasManyChildren.findByCode("vehicleFinance")

		assert parents != null : "Test data was not built"

		seedService.grailsApplication = grailsApplication

		def opts = [code: 'vehicleFinance']

		def domain = 'parentHasManyChildren'

		domain = grailsApplication.getArtefactByLogicalPropertyName('Domain', domain)?.getClazz()

		def seedObject = seedService.findSeedObject(domain, opts)

		assert seedObject != null : 'The seed object should have been created!'
	}

	void testFindSeedObjectWithNoResult() {

		seedService.grailsApplication = grailsApplication

		def opts = [code: 'vehicleFinance']

		def domain = 'parentHasManyChildren'

		domain = grailsApplication.getArtefactByLogicalPropertyName('Domain', domain)?.getClazz()

		def seedObject = seedService.findSeedObject(domain, opts)

		assert seedObject == null : 'There should not be any data!'
	}

	void testInstallSeedDataSimpleSeed() {
		def parents = ParentHasManyChildren.findAll()
		def children = Child.findAll()

		assert parents.isEmpty() : 'There should be no parent data!'
		assert children.isEmpty() : 'There should be no children data!'

		grailsApplication.config.grails.plugin.seed.root = 'test/seed/simple'

		seedService.grailsApplication = grailsApplication

		seedService.installSeedData()

		parents = ParentHasManyChildren.findAll()
		children = Child.findAll()

		assert parents.size() == 2 : "There should be 2 parent records not ${parents.size()}"
		assert children.size() == 1 : "There should be 1 child record not ${children.size()}"
	}

	void testInstallSeedDataMetaSeed() {
		def parents = ParentHasManyChildren.findAll()
		def children = Child.findAll()

		assert parents.isEmpty() : 'There should be no parent data!'
		assert children.isEmpty() : 'There should be no children data!'

		grailsApplication.config.grails.plugin.seed.root = 'test/seed/meta-object-support'

		seedService.grailsApplication = grailsApplication

		seedService.installSeedData()

		parents = ParentHasManyChildren.findAll()
		children = Child.findAll()

		def correctParent = ParentHasManyChildren.findByCode('fleetLocateTransportation')

		assert children[0].referenceId == "${correctParent.id}" : "The reference id should be ${correctParent.id} not ${children[0].referenceId}"

		assert parents.size() == 2 : "There should be 2 parent records not ${parents.size()}"
		assert children.size() == 1 : "There should be 1 child record not ${children.size()}"
	}

	void testInstallSeedWithHasMany() {
		def parents = ParentHasManyChildren.findAll()
		def children = Child.findAll()

		assert parents.isEmpty() : 'There should be no parent data!'
		assert children.isEmpty() : 'There should be no children data!'

		grailsApplication.config.grails.plugin.seed.root = 'test/seed/has-many-support'

		seedService.grailsApplication = grailsApplication

		seedService.installSeedData()

		parents = ParentHasManyChildren.findAll()
		children = Child.findAll()

		assert children.size() == 3 : "There should be 3 children records not ${children.size()}"
		assert parents.size() == 2 : "There should be 2 parent records ${parents.size()}"

		parents.each { p ->
			p.brands.each { println it }
		 }

		/// assert that the right children got set
		def flParent = parents.find{it.code == 'FleetLocate'}
		assert flParent.brands.size() == 2 : 'FleetLocate should have 2 child brands'
		assert flParent.brands.find{it.code == 'FL Trailer'} : 'Child brand FL Trailer was not assigned to FleetLocate system'
		assert flParent.brands.find{it.code == 'FL Enterprise'} : 'Child brand FL Enterprise was not assigned to FleetLocate system'

		def asgParent = parents.find{it.code == 'ASG'}
		assert asgParent.brands.size() == 1 : 'ASG should have 1 child brand'
		assert asgParent.brands.find{it.code == 'Goldstar'} : 'Child brand Goldstar was not assigned to ASG system'
	}

	void testParentWithRequiredChild() {
		def parents = ParentHasOneChild.findAll()
		def children = Child.findAll()

		assert parents.isEmpty() : 'There should be no parent data!'
		assert children.isEmpty() : 'There should be no children data!'

		grailsApplication.config.grails.plugin.seed.root = 'test/seed/parent-single-child'

		seedService.grailsApplication = grailsApplication

		seedService.installSeedData()

		parents = ParentHasOneChild.findAll()
		children = Child.findAll()

		assert parents.size() == 1 : "There should be 1 parent record not ${parents.size()}"
		assert children.size() == 1 : "There should be 1 child record not ${children.size()}"
	}

	void testChildWithParentRequired() {
		def parents = ParentHasManyChildren.findAll()
		def children = ChildParentRequired.findAll()

		assert parents.isEmpty() : 'There should be no parent data!'
		assert children.isEmpty() : 'There should be no children data!'

		grailsApplication.config.grails.plugin.seed.root = 'test/seed/child-single-parent'

		seedService.grailsApplication = grailsApplication

		seedService.installSeedData()

		parents = ParentHasManyChildren.findAll()
		children = ChildParentRequired.findAll()

		assert parents.size() == 1 : "There should be 1 parent record not ${parents.size()}"
		assert children.size() == 1 : "There should be 1 child record not ${children.size()}"
	}

	void testLookupOfSeedItemWithCompositeKey() {
		def parents = ParentHasManyChildren.findAll()

		assert parents.isEmpty() : 'There should be no parent data!'

		grailsApplication.config.grails.plugin.seed.root = 'test/seed/composite-key-support'

		seedService.grailsApplication = grailsApplication

		seedService.installSeedData()

		parents = ParentHasManyChildren.findAll()

		assert parents.size() == 1 : "There should be 1 parent record not ${parents.size()}"
	}
}
