package seedme

import grails.test.mixin.TestMixin
import grails.test.mixin.support.GrailsUnitTestMixin
import spock.lang.*

import grails.test.mixin.domain.DomainClassUnitTestMixin

import static org.junit.Assert.*
import grails.test.mixin.TestFor
import grails.test.mixin.*

enum SomeEnumType {
	value1,
	value2,
	value3
}

class SomeDomainObject {
    String id
    String version

    String code
    String something
    Date dateCreated
    Date lastUpdated
	SomeEnumType someEnum
}

class SomeOtherDomainObject {
    String id
    String version

    String extId
    String somethingElse
    Date dateCreated
    Date lastUpdated
	SomeEnumType someEnum
}

class MapOfStringsDomainObject {
    String id
    String version
    String name
    Map attributes
    static hasMany = [attributes:String]
}

/**
 * See the API for {@link grails.test.mixin.support.GrailsUnitTestMixin} for usage instructions
 */
@TestMixin(DomainClassUnitTestMixin)
@TestFor(SeedService)
class SeedServiceSpec extends Specification {

    def setup() {
    }

    def cleanup() {
    }
    def testInstallSeedData() {
        mockDomain(SeedMeChecksum)
        mockDomain(SomeDomainObject)
        mockDomain(SomeOtherDomainObject)
        mockDomain(MapOfStringsDomainObject)

        def someDomains = SomeDomainObject.findAll();
        def someOtherDomains = SomeOtherDomainObject.findAll()
        def someMapOfStringsDomains = MapOfStringsDomainObject.findAll()
        assertEquals(0, someDomains.size())
        assertEquals(0, someOtherDomains.size())
        assertEquals(0, someMapOfStringsDomains.size())

        service.installSeedData()

        someDomains = SomeDomainObject.findAll();
        someOtherDomains = SomeOtherDomainObject.findAll()
        someMapOfStringsDomains = MapOfStringsDomainObject.findAll()
        assertEquals(2, someDomains.size())
        assertEquals(2, someOtherDomains.size())
        println someMapOfStringsDomains
        assertEquals(1, someMapOfStringsDomains.size())
        assertEquals('poopy', someMapOfStringsDomains[0].attributes['poop'])
	    assertEquals(SomeEnumType.value1, SomeDomainObject.findByCode('a').someEnum)
	    assertEquals(SomeEnumType.value3, SomeDomainObject.findByCode('b').someEnum)
	    assertEquals(SomeEnumType.value2, SomeOtherDomainObject.findByExtId('1').someEnum)
	    assertEquals(SomeEnumType.value1, SomeOtherDomainObject.findByExtId('2').someEnum)
    }

    def testInstallNamedSeed() {
        mockDomain(SeedMeChecksum)
        mockDomain(SomeDomainObject)
        mockDomain(SomeOtherDomainObject)

        def someDomains = SomeDomainObject.findAll();
        def someOtherDomains = SomeOtherDomainObject.findAll()
        assertEquals(0, someDomains.size())
        assertEquals(0, someOtherDomains.size())

        service.installSeedData("application.SomeDomainObjectSeed")

        someDomains = SomeDomainObject.findAll();
        someOtherDomains = SomeOtherDomainObject.findAll()
        assertEquals(2, someDomains.size())
        assertEquals(0, someOtherDomains.size())

    }
}