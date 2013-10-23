println "Seeding parent with single child test data"
seed  = {
	parentHasManyChildren(meta:[key:'code'],code:'testparent',internalId:'testparent',name:'Test Parent',legacyPrefix:'102')
	child(meta:[key:'code'],code:'child',name:'Child',enabled:true,referenceType:'AppSystem',referenceId:[meta:[useId:true],code:'testparent',domainClass:'parentHasManyChildren'],template:true)

	parentHasOneChild(meta:[key:'code'],code:'testparent',internalId:'testparent',name:'Test Parent',legacyPrefix:'102',onlyChild:[code:'child', domainClass: 'child'])
}
