
withConfig(configuration) {
    inline(phase: 'CONVERSION') { source, context, classNode ->
        classNode.putNodeMetaData('projectVersion', '4.2.0-snapshot')
        classNode.putNodeMetaData('projectName', 'seed-me')
        classNode.putNodeMetaData('isPlugin', 'true')
    }
}
