/*
* Copyright 2014 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package seedme.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.UnknownTaskException
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.distribution.DistributionContainer
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.bundling.Jar
import groovy.io.FileType
import org.gradle.language.jvm.tasks.ProcessResources

/**
 * This is the Gradle Plugin implementation of SeedMe.
 *
 * task: seedPackage Compiles your seeds into your build directory
 *
 * @author David Estes
 * @author Graeme Rocher
 * @author Craig Burke 
 */
class SeedMePlugin implements Plugin<Project> {

    void apply(Project project) {
        def defaultConfiguration = project.extensions.create('seedme', SeedMeExtension)
        
        defaultConfiguration.seedPath = "${project.projectDir}/src/seed"
        

        project.tasks.create('seedPackage', SeedMePackage)


        def seedMePackageTask = project.tasks.getByName('seedPackage')
        
        project.afterEvaluate {
            def seedMeConfig = project.extensions.getByType(SeedMeExtension)
            ProcessResources processResources
            
            try {
                processResources = (ProcessResources) project.tasks.processResources
            } catch(UnknownTaskException ex) {
                //we dont care this is just to see if it exists
            }
            
            seedMePackageTask.configure {
                seedDir = project.file(seedMeConfig.seedPath)
                destinationDir = project.file("${processResources?.destinationDir}")
            }

            processResources.dependsOn(seedMePackageTask)
            
        }

    }
}
