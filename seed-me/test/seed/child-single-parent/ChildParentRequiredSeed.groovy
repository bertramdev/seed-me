println "Seeding parent with single child test data"
seed  = {
	parentHasManyChildren(meta:[key:'code'],code:'testparent',internalId:'testparent',name:'Test Parent',legacyPrefix:'102')

	childParentRequired(meta:[key:'code'],code:'child',name:'Child',enabled:true,referenceType:'AppSystem',parent:[code:'testparent',domainClass:'parentHasManyChildren'],template:true,referenceId:'')

}
