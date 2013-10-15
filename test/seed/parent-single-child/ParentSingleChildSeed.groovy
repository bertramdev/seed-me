println "Seeding parent with single child test data"
seed  = {
	parentHasManyChildren(meta:[key:'code'],code:'vehicleFinance',internalId:'vehicleFinance',name:'Vehicle Finance 2.0',legacyPrefix:'102')
	child(meta:[key:'code'],code:'leasingCustomer',name:'Leasing Customer',enabled:true,referenceType:'AppSystem',referenceId:[meta:[useId:true],code:'vehicleFinance',domainClass:'parentHasManyChildren'],template:true)

	parentHasOneChild(meta:[key:'code'],code:'vehicleFinance',internalId:'vehicleFinance',name:'Vehicle Finance 2.0',legacyPrefix:'102',onlyChild:[code:'leasingCustomer', domainClass: 'child'])
}
