println "Seeding simple test data"
seed  = {
	parentHasManyChildren(meta:[key:'code'],code:'testparent',internalId:'testparent',name:'Test Parent',legacyPrefix:'102')
	parentHasManyChildren(meta:[key:'code',update:false],code:'testparent2',internalId:'testparent2',name:'Test Parent 2',legacyPrefix:'103')

	child(meta:[key:'code'],code:'child',name:'Child',enabled:true,referenceType:'AppSystem',referenceId:[meta:[useId:true],code:'testparent2',domainClass:'parentHasManyChildren'],template:true)
}
