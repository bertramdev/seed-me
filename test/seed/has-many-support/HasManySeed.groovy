println "Seeding for has many support"
seed = {
	child(meta:[key:'code'],code:'Child 1',name:'Child 1',referenceId:1,referenceType:'type1')
	child(meta:[key:'code'],code:'Child 2',name:'Child 2',referenceId:1,referenceType:'type1')
	child(meta:[key:'code'],code:'Child 3',name:'Child 3',referenceId:2,referenceType:'type2')

	parentHasManyChildren(meta:[key:'code'],code:'parentOne',name:'Parent One',brands:[[code:'Child 1'], [code:'Child 2']])
	parentHasManyChildren(meta:[key:'code'],code:'parentTwo', name:'Parent Two',brands:[[code:'Child 3']])
}
