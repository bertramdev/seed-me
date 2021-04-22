println "Seeding meta support for reference objects test data"
seed  = {
	parentHasManyChildren(meta:[key:'code'],code:'testparent',internalId:'testparent',name:'Test Parent',legacyPrefix:'102')
	parentHasManyChildren(meta:[key:'code',update:false],code:'testparent2',internalId:'Test Parent 2',name:'Test Parent 2',legacyPrefix:'103')

	child(meta:[key:'code'],code:'child',name:'child1',enabled:true,referenceType:'AppSystem',referenceId:[meta:[useId:true],code:'testparent2',domainClass:'parentHasManyChildren'],template:true)
}
