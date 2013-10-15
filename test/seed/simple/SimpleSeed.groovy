println "Seeding simple test data"
seed  = {
	parentHasManyChildren(meta:[key:'code'],code:'vehicleFinance',internalId:'vehicleFinance',name:'Vehicle Finance 2.0',legacyPrefix:'102')
	parentHasManyChildren(meta:[key:'code',update:false],code:'fleetLocateTransportation',internalId:'fleetLocateTransportation',name:'FleetLocate Transportation',legacyPrefix:'103')

	child(meta:[key:'code'],code:'leasingCustomer',name:'Leasing Customer',enabled:true,referenceType:'AppSystem',referenceId:[meta:[useId:true],code:'fleetLocateTransportation',domainClass:'parentHasManyChildren'],template:true)
}
