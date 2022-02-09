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

import org.gradle.api.tasks.Input

/**
 * Allows configuration of the Gradle plugin
 *
 * @author David Estes
 */
class SeedMeExtension {

	private String seedPath = 'src/seed'
	private String compileDir = 'build/seed'

	@Input
	String getSeedPath() {
		return this.seedPath
	}

	void setSeedPath(String path) {
		this.seedPath = path
	}

	@Input
	String getCompileDir() {
		return this.compileDir
	}

	void setCompileDir(String dir) {
		this.compileDir = dir
	}

    Map toMap() {
        return [seedPath: seedPath, compileDir: compileDir]
    }
}
