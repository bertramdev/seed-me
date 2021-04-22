println "Seeding test of mapOfStringsDomainObject"

seed = {
	mapOfStringsDomainObject(meta:[key:'name', update:true], id:1, version:1,name:'test', attributes:["poop":"poopy"])
}