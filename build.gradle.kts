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
import java.io.Serializable
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files

buildscript {
    dependencies {
        classpath("org.eclipse.jgit:org.eclipse.jgit:6.7.0.202309050840-r")
    }
}

// --- Gradle infrastructure setup: Gradle distribution to use (run *twice* after modifications) ---
tasks.wrapper {
    // When new Gradle versions become available, update `ApplicationPluginFunctionalTest.supportedGradleVersions` too
    // See: https://gradle.org/releases/
    gradleVersion = "8.4"
    distributionType = Wrapper.DistributionType.ALL
}
// --- ========== ---

plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish").version("1.2.1")
    checkstyle
    pmd
    id("com.github.spotbugs").version("5.2.3")
    jacoco
}

group = "com.ms.gradle"
version = "2.0.1"

val pluginId = "com.ms.gradle.application"
val pluginClass = "com.ms.gradle.application.ApplicationPlugin"
fun toPackageName(str: String) = str.replace("-", "")
fun toClassName(str: String) = str.replace(Regex("(^|-).")) { it.value.last().titlecase() }
require(pluginId == "${project.group}.${project.name}") { "Inconsistent pluginId" }
require(pluginClass == "${toPackageName(pluginId)}.${toClassName(project.name)}Plugin") { "Inconsistent pluginClass" }

val productVendor = "Morgan Stanley"
val productTitle = "Application plugin for Gradle"
val productDescription = "Allows packaging your Java-based applications for distribution, much like " +
        "Gradle's built-in Application plugin does, but in a more standard and more flexible way."
val productTags = setOf("application", "executable", "jar", "java", "jvm")
val productUrl = "https://github.com/morganstanley/gradle-plugin-application"

// When adding support for new Java versions, update `ApplicationPluginFunctionalTest.MINIMUM_GRADLE_VERSIONS` too
val supportedJavaVersions = sortedSetOf(JavaVersion.VERSION_1_8, JavaVersion.VERSION_11, JavaVersion.VERSION_17)
val sourceJavaVersion = supportedJavaVersions.minOf { it }
val toolsJavaVersion = JavaVersion.VERSION_11

fun toolchainSpec(javaVersion: JavaVersion) = { toolchain: JavaToolchainSpec ->
    toolchain.vendor.set(JvmVendorSpec.AZUL)
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion.majorVersion))
}

fun <T> queryGit(query: (Git) -> T): T = RepositoryBuilder()
    .setGitDir(layout.projectDirectory.dir(".git").asFile).setMustExist(true)
    .build().use { query(Git(it)) }

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
    website.set(productUrl)
    vcsUrl.set("${productUrl}.git")
    plugins.create(project.name) {
        id = pluginId
        implementationClass = pluginClass
        displayName = productTitle
        description = productDescription
        tags.set(productTags)
    }
}

val manifestAttributes by lazy {
    val compileTask = tasks.compileJava.get()
    mapOf(
        "Automatic-Module-Name" to pluginId,
        "Implementation-Title" to productTitle,
        "Implementation-Version" to project.version,
        "Implementation-Vendor" to productVendor,
        "Build-Jdk" to compileTask.javaCompiler.get().metadata.run { "${javaRuntimeVersion} (${vendor})" },
        "Build-Jdk-Spec" to compileTask.options.release.get(),
        "Build-Scm-Commit" to (gitStatus?.asCommit() ?: "unknown"),
        "Build-Scm-Url" to "${productUrl}/tree/v${project.version}",
    )
}

java {
    toolchain(toolchainSpec(toolsJavaVersion))
    // Not really used by Gradle, only added for better IDE integration
    sourceCompatibility = sourceJavaVersion
    withJavadocJar()
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    val spotbugsAnnotations = spotbugs.toolVersion.map { "com.github.spotbugs:spotbugs-annotations:${it}" }
    compileOnly(spotbugsAnnotations)
    testCompileOnly(spotbugsAnnotations)

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("org.apache.commons:commons-lang3:3.14.0")
    testImplementation("commons-io:commons-io:2.15.0")
}

tasks.withType<JavaCompile> {
    options.release.set(sourceJavaVersion.majorVersion.toInt())
}

tasks.javadoc {
    val fullOptions = options.windowTitle(productTitle).apply {
        encoding = Charsets.UTF_8.name()
        memberLevel = JavadocMemberLevel.PUBLIC
        // See: https://github.com/gradle/gradle/issues/18274
        addStringOption("-release", sourceJavaVersion.majorVersion)
    }

    // Javadoc in Java 9+ requires this `linkoffline` workaround to be able to link to Java 8 docs
    // In case of a package overlap (e.g. `javax.annotation`), the last offline link takes precedence
    // To have control over the situation, we have to use offline links for every dependency
    doFirst {
        val linksDir = temporaryDir.resolve("links")
        delete(linksDir.toPath())
        Files.createDirectories(linksDir.toPath())

        data class OfflineLink(val dir: File, val uri: URI)
        val descriptorFileNames = listOf("element-list", "package-list")

        // For built-in dependencies, download Javadoc descriptors manually from the URI
        val linksForBuiltIns = listOf(
            OfflineLink(linksDir.resolve("java"),
                uri("https://docs.oracle.com/javase/${sourceJavaVersion.majorVersion}/docs/api/")),
            OfflineLink(linksDir.resolve("gradle"),
                uri("https://docs.gradle.org/${GradleVersion.current().baseVersion.version}/javadoc/")),
        ).onEach { offlineLink ->
            val javadocDescriptor = descriptorFileNames.mapNotNull { fileName ->
                with(offlineLink.uri.resolve(fileName).toURL().openConnection() as HttpURLConnection) {
                    instanceFollowRedirects = false
                    inputStream.use { content ->
                        responseCode.takeIf { it == 200 }?.let {
                            Files.createDirectories(offlineLink.dir.toPath())
                            offlineLink.dir.resolve(fileName).apply {
                                Files.copy(content, toPath())
                            }
                        }
                    }
                }
            }.firstOrNull()
            checkNotNull(javadocDescriptor) { "Could not download Javadoc descriptor from ${offlineLink.uri}" }
        }

        // For external modules, extract Javadoc descriptors from the module's Javadoc JAR
        val linksForModules = run {
            val compileComponents = configurations.compileClasspath.get().incoming.resolutionResult.allComponents
            val compileJavadocs = dependencies.createArtifactResolutionQuery()
                .forComponents(compileComponents.map { it.id })
                .withArtifacts(JvmLibrary::class, JavadocArtifact::class)
                .execute()
            compileJavadocs.resolvedComponents.mapNotNull { component ->
                val javadocArtifact =
                    component.getArtifacts(JavadocArtifact::class).singleOrNull() as ResolvedArtifactResult?
                val javadocDescriptor = javadocArtifact?.let { artifact ->
                    val javadocJarTree = zipTree(artifact.file)
                    descriptorFileNames.firstNotNullOfOrNull { fileName ->
                        javadocJarTree.matching { include(fileName) }.singleOrNull()
                    }
                }
                javadocDescriptor?.let { file ->
                    val id = component.id as ModuleComponentIdentifier
                    OfflineLink(file.parentFile, uri("https://javadoc.io/doc/${id.group}/${id.module}/${id.version}/"))
                }
            }
        }

        // Add offline links
        linksForBuiltIns.plus(linksForModules).forEach { offlineLink ->
            fullOptions.linksOffline(offlineLink.uri.toString(), offlineLink.dir.path)
        }
        // Generate correct deep links to Java 8 and Gradle methods (HTML5: `#toString()`, HTML4: `#toString--`)
        fullOptions.addBooleanOption("html4", true)
    }
}

tasks.withType<Jar> {
    manifest.attributes(manifestAttributes)
    from(files("LICENSE", "NOTICE"))
}

checkstyle {
    toolVersion = "10.12.5"
    maxErrors = 0
    maxWarnings = 0
}

pmd {
    ruleSets = listOf("category/java/bestpractices.xml")
}

tasks.withType<SpotBugsTask> {
    reports.register("html")
    reports.register("xml")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
// Simple helper class to be able to set system properties whose value is calculated lazily (fixes a bug during `clean`)
// See: https://github.com/gradle/gradle/issues/25752#issuecomment-1721311612
class LazyString(private val source: Lazy<String>) : Serializable {
    constructor(source: () -> String) : this(lazy(source))
    constructor(source: Provider<String>) : this(source::get)
    override fun toString() = source.value
}
supportedJavaVersions.forEach { javaVersion ->
    // Unfortunately we need to realize the tasks so that we can call `JacocoReportBase.executionData(Task)` below
    // See: https://github.com/gradle/gradle/issues/8794
    val testTask =
        if (javaVersion == sourceJavaVersion) tasks.test.get()
        else tasks.create<Test>("testOnJava${javaVersion.majorVersion}")
    with(testTask) {
        group = JavaBasePlugin.VERIFICATION_GROUP
        description = "Runs the test suite on Java ${javaVersion.majorVersion}."
        javaLauncher.set(javaToolchains.launcherFor(toolchainSpec(javaVersion)))

        val testDataDir = layout.projectDirectory.dir("test-data")
        systemProperty("testEnv.testDataDir", testDataDir.asFile.absolutePath)
        inputs.dir(testDataDir)

        val testKitDir = layout.buildDirectory.dir("testKit")
        systemProperty("testEnv.testKitDir", LazyString(testKitDir.map { it.asFile.absolutePath }))
        doFirst {
            Files.createDirectories(testKitDir.get().asFile.toPath())
        }

        val testJacoco = extensions.getByType<JacocoTaskExtension>()
        testJacoco.sessionId = "${project.name}-${name}"
        // Pass the Jacoco Agent JVM argument into the tests so that TestKit can apply it to the JVMs it spawns
        systemProperty("testEnv.jacocoAgentJvmArg", LazyString {
            // `JacocoTaskExtension.getAsJvmArg` returns a string with relative paths for both the agent JAR and the
            // destination file; we need to make those absolute so that TestKit JVMs can locate them
            fun absolutePath(path: String) = workingDir.resolve(path).absolutePath
            // Example input: "-javaagent:build/tmp/.../jacocoagent.jar=destfile=build/jacoco/test.exec,append=true,..."
            // See: https://www.jacoco.org/jacoco/trunk/doc/agent.html
            val regex = Regex("""^(-javaagent:)([^=]*)(=(?:[^=]*=[^,]*,)*)(destfile=)([^,]*)((?:,[^=]*=[^,]*)*)$""")
            val groups = regex.matchEntire(testJacoco.asJvmArg)!!.groups
            fun group(index: Int) = groups[index]!!.value
            group(1) + absolutePath(group(2)) + group(3) + group(4) + absolutePath(group(5)) + group(6)
        })
        // Wait for the execution data output file to be released by TestKit JVMs, even when some tests fail
        addTestListener(object : TestListener {
            override fun beforeSuite(suite: TestDescriptor) {}
            override fun beforeTest(testDescriptor: TestDescriptor) {}
            override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {}
            override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                // Do this only at the end of the whole `Test` task (see `AbstractTestTask.afterSuite` docs)
                if (suite.parent == null) {
                    fun isLocked(file: File) = file.exists() && !file.renameTo(file)
                    val executionData = testJacoco.destinationFile!!
                    for (count in 20 downTo 0) {
                        if (!isLocked(executionData)) {
                            break
                        } else if (count > 0) {
                            Thread.sleep(500)
                        } else {
                            logger.error("${suite} keeps JaCoCo execution data file locked: ${executionData}")
                        }
                    }
                }
            }
        })
        finalizedBy(tasks.jacocoTestReport)
    }
    tasks.jacocoTestReport {
        executionData(testTask)
    }
    tasks.jacocoTestCoverageVerification {
        executionData(testTask)
    }
}
tasks.check {
    dependsOn(tasks.withType<Test>())
}

tasks.jacocoTestReport {
    reports.html.required.set(true)
    reports.xml.required.set(true)
}
tasks.jacocoTestCoverageVerification {
    mustRunAfter(tasks.jacocoTestReport)
    violationRules.rule {
        limit {
            minimum = "1.000".toBigDecimal()
        }
    }
}
tasks.check {
    dependsOn(tasks.withType<JacocoReport>(), tasks.withType<JacocoCoverageVerification>())
}

tasks.validatePlugins {
    enableStricterValidation.set(true)
    ignoreFailures.set(false)
    failOnWarning.set(true)
}

publishing {
    repositories.maven(layout.buildDirectory.dir("local-publish"))
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
