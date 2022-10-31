plugins {
    id("com.ms.gradle.application")
}

version = "1.0"

repositories {
    maven {
        url = uri("repo/maven")
    }
}

dependencies {
    implementation(fileTree("repo/local").include("*.jar"))
    implementation("com.ms.test:datetime-current:4.5")
}

tasks.jar {
    manifest {
        attributes(
                "Implementation-Title" to "Application plugin functional test",
                "Implementation-Version" to "1.0.b42")
    }
}

val emptyJar by tasks.creating(Jar::class) {
    archiveBaseName.set("empty")
}

// --- Valid usages ---

applications.main {
    mainClass.set("com.ms.test.app.PrintTimestamp")
}

applications.create("simple") {
    fromSourceSet(sourceSets.main)
    mainClass.set("com.ms.test.app.PrintTimestamp")
}

applications.create("withoutRawJar") {
    dependencies.set(locateDependencies(sourceSets.main))
    mainClass.set("com.ms.test.app.PrintTimestamp")
}

applications.create("withoutDependencies") {
    rawJar.set(locateRawJar(sourceSets.main))
    mainClass.set("com.ms.test.app.PrintTimestamp")
}

applications.create("complex") {
    description = "This is an application showcasing all the possibilities of the DSL."
    applicationBaseName.set("complexApp")
    rawJar.set(tasks.jar)
    dependencies.set(configurations.runtimeClasspath)
    dependencyDirectoryName.set("complexDeps")
    mainClass.set("com.ms.test.app.PrintTimestamp")
    applicationJar {
        destinationDirectory.set(layout.buildDirectory.dir("appsComplex"))
        archiveBaseName.set("complexJar")
    }
    distribution {
        contents.from(file("README.md"))
    }
}

val manualApplicationJar by tasks.creating(com.ms.gradle.application.ApplicationJar::class) {
    fromSourceSet(sourceSets.main)
    mainClass.set("com.ms.test.app.PrintTimestamp")
    archiveBaseName.set("manualJar")
}
distributions {
    create("manual") {
        distributionBaseName.set("manualDist")
        contents {
            with(manualApplicationJar.applicationCopySpec())
            from(file("README.md"))
        }
    }
}

val installFullyManualDist by tasks.creating(Sync::class) {
    into(layout.buildDirectory.dir("install/fullyManual"))
    with(manualApplicationJar.applicationCopySpec().into("app"))
    from(file("README.md"))
    eachFile {
        relativePath = relativePath.prepend("content")
    }
}

// --- Invalid usages ---

applications.create("missingDependencyDirectoryName") {
    fromSourceSet(sourceSets.main)
    dependencyDirectoryName.convention(null as String?).set(null as String?)
    mainClass.set("com.ms.test.app.PrintTimestamp")
}

applications.create("emptyDependencyDirectoryName") {
    fromSourceSet(sourceSets.main)
    dependencyDirectoryName.set("")
    mainClass.set("com.ms.test.app.PrintTimestamp")
}

applications.create("missingMainClass") {
    fromSourceSet(sourceSets.main)
}

applications.create("emptyMainClass") {
    fromSourceSet(sourceSets.main)
    mainClass.set("")
}
