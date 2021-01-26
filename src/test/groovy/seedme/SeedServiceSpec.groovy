package seedme

import grails.gorm.annotation.Entity
import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

enum SomeEnumType {
	value1,
	value2,
	value3
}

@Entity
class SomeDomainObject {
    String id
    String version

    String code
    String something
    Date dateCreated
    Date lastUpdated
	SomeEnumType someEnum
}

@Entity
class SomeOtherDomainObject {
    String id
    String version

    String extId
    String somethingElse
    Date dateCreated
    Date lastUpdated
	SomeEnumType someEnum
}

@Entity
class MapOfStringsDomainObject {
    String id
    String version
    String name
    Map attributes
    static hasMany = [attributes:String]
}

class SeedServiceSpec extends Specification implements ServiceUnitTest<SeedService>, DataTest {

    def setup() {
    }

    def cleanup() {
    }

    def testInstallSeedData() {
        given:
        mockDomain(SeedMeChecksum)
        mockDomain(SomeDomainObject)
        mockDomain(SomeOtherDomainObject)
        mockDomain(MapOfStringsDomainObject)

        def someDomains = SomeDomainObject.findAll()
        def someOtherDomains = SomeOtherDomainObject.findAll()
        def someMapOfStringsDomains = MapOfStringsDomainObject.findAll()
        assert 0 == someDomains.size()
        assert 0 == someOtherDomains.size()
        assert 0 == someMapOfStringsDomains.size()

        when:
        service.installSeedData()

        someDomains = SomeDomainObject.findAll()
        someOtherDomains = SomeOtherDomainObject.findAll()
        someMapOfStringsDomains = MapOfStringsDomainObject.findAll()

        then:
        2 == someDomains.size()
        2 == someOtherDomains.size()
        println someMapOfStringsDomains
        1 == someMapOfStringsDomains.size()
        'poopy' == someMapOfStringsDomains[0].attributes['poop']
	    SomeEnumType.value1 == SomeDomainObject.findByCode('a').someEnum
	    SomeEnumType.value3 == SomeDomainObject.findByCode('b').someEnum
	    SomeEnumType.value2 == SomeOtherDomainObject.findByExtId('1').someEnum
	    SomeEnumType.value1 == SomeOtherDomainObject.findByExtId('2').someEnum
    }

    def testInstallNamedSeed() {
        given:
        mockDomain(SeedMeChecksum)
        mockDomain(SomeDomainObject)
        mockDomain(SomeOtherDomainObject)

        def someDomains = SomeDomainObject.findAll();
        def someOtherDomains = SomeOtherDomainObject.findAll()
        assert 0 == someDomains.size()
        assert 0 == someOtherDomains.size()

        when:
        service.installSeedData("application.SomeDomainObjectSeed")
        someDomains = SomeDomainObject.findAll();
        someOtherDomains = SomeOtherDomainObject.findAll()

        then:
        2 == someDomains.size()
        0 == someOtherDomains.size()
    }
}