println "Seeding test someOtherDomainObject"
seed  = {
    someOtherDomainObject(meta:[key:'extId'], extId:'1', somethingElse:'foo', someEnum:'value2')
    someOtherDomainObject(meta:[key:'extId'], extId:'2', somethingElse:'bar', someEnum:'value1')
}
