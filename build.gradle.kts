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
    checkstyle
    pmd
    id("com.github.spotbugs").version("5.0.13")
    jacoco
    `maven-publish`
}

group = "com.ms.gradle"
version = "2.0.0"

val pluginId = "com.ms.gradle.application"
val pluginClass = "com.ms.gradle.application.ApplicationPlugin"
val pluginTitle = "Application plugin for Gradle"
val pluginDescription = "Allows packaging your Java-based application for distribution, much like " +
        "Gradle's built-in Application plugin does, but in a more standard and more flexible way."
val pluginUrl = "https://github.com/morganstanley/gradle-plugin-application"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    withJavadocJar()
    withSourcesJar()
}

gradlePlugin {
    plugins.create(project.name) {
        id = pluginId
        implementationClass = pluginClass
        displayName = pluginTitle
        description = pluginDescription
    }
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

checkstyle {
    toolVersion = "9.3"
    maxErrors = 0
    maxWarnings = 0
}

pmd {
    ruleSets = listOf("category/java/bestpractices.xml")
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask> {
    reports.register("html");
    reports.register("xml");
}

tasks.withType<Test> {
    useJUnitPlatform()
}
tasks.test {
    inputs.dir(projectDir.resolve("test-data"))

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

tasks.withType<ValidatePlugins> {
    enableStricterValidation.set(true)
    ignoreFailures.set(false)
    failOnWarning.set(true)
}

publishing {
    repositories.maven(buildDir.resolve("publish-maven"))
    publications.withType<MavenPublication> {
        pom {
            name.set(pluginTitle)
            description.set(pluginDescription)
            url.set(pluginUrl)
        }
    }
}
