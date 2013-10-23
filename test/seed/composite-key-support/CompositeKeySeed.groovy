println "Seeding meta support for reference objects test data"
seed  = {
	parentHasManyChildren(meta:[key:['code', 'internalId', 'name']],code:'testparent',internalId:'testparent',name:'Test Parent',legacyPrefix:'102')
	parentHasManyChildren(meta:[key:['code', 'internalId', 'name']],code:'testparent',internalId:'testparent',name:'Test Parent',legacyPrefix:'102')
}
