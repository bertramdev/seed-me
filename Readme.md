SeedMe
------

SeedMe is a simple plugin that was created to provide an easy way to add config and test data to the system.

Release Notes
-------------
* __4.1.4__: Actually Updated the ReadME Sorry! Added YAML,JSON seed template support
* __0.6.5__: Fixed meta support to respect the update attribute
* __0.6.2__: Fixing the ability to use map types as domain properties which are not associations
* __0.6.1__: Added support for [enums](#EnumSupport)


Configuring for Development
---------------------------
SeedMe does not require any configuration, but there are several configuration options:

```groovy
grails.plugin.seed.autoSeed=false
grails.plugin.seed.skipPlugins=false
grails.plugin.seed.excludedPlugins=[] // plugins to exclude
grails.plugin.seed.excludedSeedFiles=[] // Seed files to exclude
grails.plugin.seed.root='src/seed' //
grails.plugin.seed.metaKey='meta' // seed map key for meta information
grails.plugin.seed.environment='[Grails environment name]'
```

Grails 4
--------
_build.gradle_
```groovy
buildscript {

    dependencies {
        classpath 'com.bertramlabs.plugins:seed-me:4.1.4'
    }
}

apply plugin: "seed-me"

dependencies {
  runtimeOnly 'com.bertramlabs.plugins:seed-me:4.1.4'
}
```

Grails 3
--------

For grails3 a mod has to be made to the users build.gradle file such that the seed files are copied into the war file appropriately.

```groovy
project.tasks.processResources.dependsOn(project.task.create(Copy,"Copy Seed"){
  from "src/seed"
  into "seed"
})
```

Details
---------------------------
SeedMe looks for `.groovy`, `.yaml`, and `.json` seed files in the project `src/seed` folder and in all included plugins.  Any files at the root of seed folder will be processed as well as one level deaper.

**NOTE:** If the folder name contains the word `templates/` this is reserved for template inclusion when dealing with large blobs

SeedMe also checks for a folder in the seed folder with a name that matches the current running environment or `env-${currentEnvironment}` and will process any files found in that folder. The plugin also, only runs seeds that have not previously been run by maintaining a checksum of the seed files in the database.

Seed DSL

Seed Examples
---------------------------
This example is for a device.
```groovy
seed  = {
  device(meta:[key:'uniqueId', update:false], uniqueId:'5555', account:[uniqueId:'testaccount'], name:'voyagerTest1108',
      deviceType:[code:'ion'], serialNumber:'5555', imei:'0000000000000')
}

```

or now in yaml:

```yaml
# dependencies
dependsOn: []
# seed entries
seed:
  device:
  - meta:
      key: code
      update: false
    uniqueId: 5555
    account:
      uniqueId: 'testaccount'
    name: voyagerTest1108
    deviceType: ion
    serialNumber: 5555
    imei: '0000000000000'
```

Usage
----
The seed file must start with a seed closure and within the closure you will add an entry for each seed item. Each entry within the seed closure must begin with the `Artefact` name of the `Domain`.

**Specifying Options**

The seed plugin supports several optional attributes to make it easier to generate data. The first of which is the `meta` property

```groovy
domainClass(meta:[key:'uniqueId', update: false])
```

The `meta` property allows the specification of the unique finder key. When a domain is seeded, the seed service will first attempt to find an instance of the domain by this property based on the seed value. This `key` property can be either a single property value or a map of properties to find by. Also, if the seed does exist, you can optionally set `update:false`. This will prevent the record from being restored/updated if it already exists.

**Assigning properties by association**

If a property is an instance of a domain class, the property may be assignable by passing a map of the fields with which to look up this domain. i.e. Given 2 domains `Book` and `Author` with `Book` belonging to `Author`:

```groovy
seed = {
  author(meta:[key:'name'], name: 'David', description: 'Author Bio Here')

  book(meta:[key:'name'], name: 'How to seed your database', date: new Date(), author: [name: 'David'])
}
```

**Assigning Values by Closure**

In some cases, it may be necessary to generate the values within context of the seed run. (For example, using enumerators or string values that are combined from queries to other domains).

```groovy
seed = {
  author(meta:[key:'name'], name: 'David', description: 'Author Bio Here')

  book(meta:[key:'name'], name: 'How to seed your database', date: new Date(), author: [name: 'David'],
    status: { domain ->
      return domain.ACTIVE
    }
  )
}
```

<a name="EnumSupport"></a>
**Enum Support**

Enum type values in domain classes are supported in two variations.  Through direct reference inside your DSL or by allowing the seed service to derive the enum from the domain class itself.

Given enum and domain class defined as:
```groovy
package com.mypackage

enum SomeEnum {
    value1,
    value2,
    value3
}

class SomeDomain {
    String name
    SomeEnum someEnum
    Date dateCreated
}
```

You can use this in form A:
```groovy
import com.mypackage.SomeEnum
seed = {
    someDomain(meta:[key:'name'], name:'bob', someEnum:SomeEnum.value1)
}
```

Form B:
```groovy
seed = {
    someDomain(meta:[key:'name'], name:'bob', someEnum:'value2')
}
```

**Running Seeds**

By default, the seeds do not execute at startup. This can be enabled by setting `grails.plugin.seed.autoSeed = true` or using system property with startup `-DautoSeed=true`. This allows you to selectivly control how/when your seeds are executed for particular environments.


**Assigning properties by domain**

In some cases (mainly legacy db schemas) an association may not directly exist between 2 domains however they are associated by a property. If a specific property needs to be the result of finding another domain with that property you may use a map with the following syntax:

```groovy
seed = {
  author(meta:[key:'name'], name: 'David', description: 'Author Bio Here')

  book(meta:[key:'name'], name: 'How to seed your database', date: new Date(), authorIdFk: [domainClass: 'author', meta: [property:'id'], name: 'David'])
}
```
This will look for the specified domain of `Author` and find the author with the specified name. The resultant record will then assign the property `id` to `authorIdFk`.


**Assigning domains to a hasMany property**

For one-to-many or many-to-many relationships, specify a list of maps. The map shall contain the fields by which to look up the sub domains.  For instance, for the following `Book` domain class with many `Authors`

```groovy
class Book {
  String name
  Date date
  static hasMany = [authors:Author]
}
```

The seed closure would look like

```groovy
seed = {
  author(meta:[key:'name'], name: 'David', description: 'Author Bio Here')
  author(meta:[key:'name'], name: 'John', description:'John is a great author')

  book(meta:[key:'name'], name: 'How to seed your database', date: new Date(), authors: [[name: 'David'], [name: 'John']])
}
```

This will look for the two `Author` domains by the `name` field.

**Controlling Seed Order**

SeedMe supports the ability to control seed load order across all your plugins with a dependsOn directive. An example may look like this:

```groovy
seed = {
  dependsOn(['Authors'])
  book(meta:[key:'name'], name: 'How to seed your database', date: new Date(), authors: [[name: 'David'], [name: 'John']])
}
```

**NOTE**: If 2 seed files exist with the same name across plugins, dependency order can be controlled by specifying the plugin name before the seed file name. i.e.:

```groovy
seed = {
  dependsOn(['AuthorCore.Authors'])
  book(meta:[key:'name'], name: 'How to seed your database', date: new Date(), authors: [[name: 'David'], [name: 'John']])
}
```

**Using in Integration Tests**

SeedMe provides an interface for writing the seed-me DSL right into your tests. Simply use the seedService and call

```groovy
seedService.installSeed {
  author(meta:[key:'name'], name: 'John', description:'John is a great author')
}
```
