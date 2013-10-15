println "Seeding for has many support"
seed = {
	child(meta:[key:'code'],code:'FL Trailer',name:'Trailer',referenceId:1,referenceType:'type1')
	child(meta:[key:'code'],code:'FL Enterprise',name:'Enterprise',referenceId:1,referenceType:'type1')
	child(meta:[key:'code'],code:'Goldstar',name:'Goldstar',referenceId:2,referenceType:'type2')

	parentHasManyChildren(meta:[key:'code'],code:'FleetLocate',name:'FleetLocate System',brands:[[code:'FL Trailer'], [code:'FL Enterprise']])
	parentHasManyChildren(meta:[key:'code'],code:'ASG', name:'FleetLocate System',brands:[[code:'Goldstar']])
}