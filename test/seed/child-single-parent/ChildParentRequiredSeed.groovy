println "Seeding parent with single child test data"
seed  = {
	parentHasManyChildren(meta:[key:'code'],code:'vehicleFinance',internalId:'vehicleFinance',name:'Vehicle Finance 2.0',legacyPrefix:'102')

	childParentRequired(meta:[key:'code'],code:'leasingCustomer',name:'Leasing Customer',enabled:true,referenceType:'AppSystem',parent:[code:'vehicleFinance',domainClass:'parentHasManyChildren'],template:true,referenceId:'')

}
