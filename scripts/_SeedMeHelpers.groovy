import groovy.text.GStringTemplateEngine

seedTemplate = { params ->
	def engine     = new GStringTemplateEngine()
	def template   = new File(proconSeedPluginDir, ["src/templates/$params.templateName"]


	String generatedTemplateText = engine.createTemplate(template.text).make(params.params)
	if(generatedTemplateText && params.destination) {
		def outputFile = new File(basedir, params.destination)
		outputFile.createNewFile()
		outputFile.text = generatedTemplateText
	} else if(generatedTemplateText) {
		return generatedTemplateText
	}
}

injectIntoFile = { params ->
	def outputFile = new File(basedir, params.file)
	if(!outputFile.exists() && !params.autoCreate) {
		throw new RuntimeException("File Not Found For Injection: $params.file")
	}
	if(params.autoCreate) {
		outputFile.createNewFile()
	}
	outputFile.text = params.text + "\n" + outputFile.text
}
