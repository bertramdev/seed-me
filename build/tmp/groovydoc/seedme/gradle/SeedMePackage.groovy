package seedme.gradle

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import groovy.io.FileType
/**
 * Created by davydotcom on 4/21/16.
 */
@CompileStatic
class SeedMePackage extends DefaultTask {
    private String destinationDirectoryPath
    @Delegate SeedMeExtension seedMeExtension = new SeedMeExtension()

    @Input
    File getSeedDir() {
        def path = seedMeExtension.seedPath
        return path ? new File(path) : null
    }

    void setSeedDir(File seedDir) {
        seedMeExtension.seedPath = seedDir.absolutePath
    }

    @OutputDirectory
    File getDestinationDir() {
        destinationDirectoryPath ? new File(destinationDirectoryPath) : null
    }

    void setDestinationDir(File dir) {
        destinationDirectoryPath = dir.canonicalPath
    }

    @InputFiles
    FileTree getSource() {
        FileTree src = getProject().files(this.seedDir).getAsFileTree();
        return src
    }

    @TaskAction
    @CompileDynamic
    void compile() {
        def manifestNames = []
        File seedDestDir = new File(destinationDir,"seed")
        if(seedDestDir.exists()) {
            seedDestDir.deleteDir()
            seedDestDir.mkdirs()
        } else {
            seedDestDir.mkdirs()
        }
        seedDir.eachFileRecurse(FileType.FILES) { file ->
            def relativePath = relativePathToResolver(file.canonicalPath, seedDir.canonicalPath)
            if(relativePath.indexOf('.') == 0) {
                println "ignoring hidden file: ${relativePath} at ${file} and not adding to seeds.list"
            } else {
                manifestNames << relativePath
            }
            File outputFile = new File(seedDestDir,relativePath)
            if(!outputFile.exists()) {
                outputFile.parentFile.mkdirs()
                outputFile.createNewFile()
            }
            InputStream sourceStream
            OutputStream outputStream
            try {
                sourceStream = file.newInputStream()
                outputStream = outputFile.newOutputStream()

                outputStream << sourceStream
            } finally {
                try {
                    sourceStream.close()
                } catch(ex1) {
                    //silent fail
                }
                try {
                    outputStream.flush()
                    outputStream.close()
                } catch(ex) {
                    //silent fail
                }

            }
        }
        File seedList = new File(destinationDir, "seeds.list")
        if(!seedList.exists()) {
            seedList.parentFile.mkdirs()
            seedList.createNewFile()
        }
        OutputStream seedListOs
        try {
            seedListOs = seedList.newOutputStream()
            seedListOs <<  manifestNames.join("\n");
        } finally {
            seedListOs.flush()
            seedListOs.close()
        }
        

    }

    protected String relativePathToResolver(String filePath, String scanDirectoryPath) {
        if(filePath.startsWith(scanDirectoryPath)) {
            return filePath.substring(scanDirectoryPath.size() + 1).replace(File.separator, '/')
        }
        return filePath
    }
}
