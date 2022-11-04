/*
 * Morgan Stanley makes this available to you under the Apache License, Version 2.0 (the "License"). You may obtain a
 * copy of the License at http://www.apache.org/licenses/LICENSE-2.0. See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

import com.github.spotbugs.snom.SpotBugsTask
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.RepositoryBuilder
import java.net.URI

buildscript {
    dependencies {
        classpath("org.eclipse.jgit:org.eclipse.jgit:6.3.0.202209071007-r")
    }
}

// --- Gradle infrastructure setup: Gradle distribution to use (run *twice* after modifications) ---
tasks.wrapper {
    // When changing this version, update `ApplicationPluginFunctionalTest.GRADLE_VERSIONS_SUPPORTED` as well
    // See: https://gradle.org/releases/
    gradleVersion = "7.5.1"
    distributionType = Wrapper.DistributionType.ALL
}
// --- ========== ---

plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish").version("1.0.0")
    checkstyle
    pmd
    id("com.github.spotbugs").version("5.0.13")
    jacoco
}

group = "com.ms.gradle"
version = "2.0.0"

val pluginId = "com.ms.gradle.application"
val pluginClass = "com.ms.gradle.application.ApplicationPlugin"
require(pluginId == "${project.group}.${project.name}") { "Inconsistent naming: pluginId" }
require(pluginClass == "${pluginId}.${project.name.capitalize()}Plugin") { "Inconsistent naming: pluginClass" }

val productVendor = "Morgan Stanley"
val productTitle = "Application plugin for Gradle"
val productDescription = "Allows packaging your Java-based applications for distribution, much like " +
        "Gradle's built-in Application plugin does, but in a more standard and more flexible way."
val productTags = setOf("application", "executable", "jar", "java", "jvm")
val productUrl = "https://github.com/morganstanley/gradle-plugin-application"

fun <T> queryGit(query: (Git) -> T): T =
    RepositoryBuilder().setGitDir(file(".git")).setMustExist(true).build().use { query(Git(it)) }

data class GitStatus(val commit: String, val clean: Boolean) {
    fun asCommit() = if (clean) commit else "${commit}-dirty"
}
val gitStatus = try {
    queryGit { git ->
        val headOid = checkNotNull(git.repository.resolve(Constants.HEAD)) { "Could not resolve HEAD commit" }
        val status = git.status().call()!!
        GitStatus(headOid.name!!, status.isClean)
    }
} catch (ex: RepositoryNotFoundException) {
    null
}

gradlePlugin {
    plugins.create(project.name) {
        id = pluginId
        implementationClass = pluginClass
        displayName = productTitle
        description = productDescription
    }
}

pluginBundle {
    website = productUrl
    vcsUrl = productUrl
    pluginTags = mapOf(project.name to productTags)
}

val manifestAttributes by lazy {
    mapOf(
        "Automatic-Module-Name" to pluginId,
        "Implementation-Title" to productTitle,
        "Implementation-Version" to project.version,
        "Implementation-Vendor" to productVendor,
        "Build-Jdk" to System.getProperty("java.version"),
        "Build-Jdk-Spec" to java.targetCompatibility,
        "Build-Scm-Commit" to (gitStatus?.asCommit() ?: "unknown"),
        "Build-Scm-Url" to URI("${productUrl}/tree/").resolve(URI(null, null, "v${project.version}", null)),
    )
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    // If we call these here, `PublishPlugin.forceJavadocAndSourcesJars` will throw an exception when it does the same
    // withJavadocJar()
    // withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    val spotbugsAnnotations = spotbugs.toolVersion.map { "com.github.spotbugs:spotbugs-annotations:${it}" }
    compileOnly(spotbugsAnnotations)
    testCompileOnly(spotbugsAnnotations)

    testImplementation("org.junit.jupiter:junit-jupiter:5.9.1")
    testImplementation("org.assertj:assertj-core:3.23.1")
    testImplementation("org.apache.commons:commons-lang3:3.12.0")
    testImplementation("commons-io:commons-io:2.11.0")
}

tasks.withType<Jar> {
    manifest.attributes(manifestAttributes)
    from(files("LICENSE", "NOTICE"))
}

checkstyle {
    toolVersion = "10.4"
    maxErrors = 0
    maxWarnings = 0
}

pmd {
    ruleSets = listOf("category/java/bestpractices.xml")
}

tasks.withType<SpotBugsTask> {
    reports.register("html");
    reports.register("xml");
}

tasks.withType<Test> {
    useJUnitPlatform()
}
tasks.test {
    inputs.dir(file("test-data"))

    // Pass the Jacoco Agent JVM argument into the tests so that TestKit can apply it to the JVMs it spawns
    extensions.getByType<JacocoTaskExtension>().let { jacoco ->
        fun absoluteAgentJvmArg(relativeAgentJvmArg: String): String {
            // See: https://www.jacoco.org/jacoco/trunk/doc/agent.html
            // Example input: "-javaagent:build/tmp/.../jacocoagent.jar=destfile=build/jacoco/test.exec,append=true,..."
            val regex = Regex("""^(-javaagent:)([^=]*)(=(?:[^=]*=[^,]*,)*)(destfile=)([^,]*)((?:,[^=]*=[^,]*)*)$""")
            val groups = regex.matchEntire(relativeAgentJvmArg)!!.groups
            fun group(index: Int) = groups[index]!!.value
            fun absolutePath(path: String) = workingDir.resolve(path).absolutePath
            return group(1) + absolutePath(group(2)) + group(3) +
                    group(4) + absolutePath(group(5)) + group(6)
        }
        systemProperty("testEnv.jacocoAgentJvmArg", absoluteAgentJvmArg(jacoco.asJvmArg))
        // Wait for the execution data output file to be released by TestKit JVMs
        doLast {
            val executionData = jacoco.destinationFile!!
            for (count in 1..20) {
                if (!executionData.exists() || executionData.renameTo(executionData)) {
                    break
                }
                Thread.sleep(250)
            }
        }
    }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports.html.required.set(true)
    reports.xml.required.set(true)
}

tasks.validatePlugins {
    enableStricterValidation.set(true)
    ignoreFailures.set(false)
    failOnWarning.set(true)
}

tasks.publishPlugins {
    dependsOn(tasks.build)
    doFirst {
        val localStatus = checkNotNull(gitStatus) { "Could not query local Git repository" }
        val releaseTag = "v${project.version}"
        val releaseCommit = queryGit { git ->
            val remoteTags = git.lsRemote().setRemote("${productUrl}.git").setTags(true).callAsMap()
            val releaseRef = checkNotNull(remoteTags.get("refs/tags/${releaseTag}")) {
                "Release tag \"${releaseTag}\" does not exist in root repository"
            }
            val releaseOid = checkNotNull(releaseRef.run { peeledObjectId ?: objectId }) {
                "Could not resolve release tag \"${releaseTag}\""
            }
            releaseOid.name!!
        }
        check(localStatus.commit == releaseCommit) {
            "Local Git repository must have release tag \"${releaseTag}\" checked out for publication"
        }
        check(localStatus.clean) {
            "Local Git repository must be in a clean state for publication"
        }
    }
}
