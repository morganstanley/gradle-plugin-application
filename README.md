# Application plugin for Gradle

[![Lifecycle](https://img.shields.io/badge/Lifecycle-Active-green)](#application-plugin-for-gradle)
[![Gradle Plugin Portal](https://img.shields.io/maven-metadata/v.svg?label=Gradle+Plugin+Portal&metadataUrl=https://plugins.gradle.org/m2/com/ms/gradle/application/com.ms.gradle.application.gradle.plugin/maven-metadata.xml)](https://plugins.gradle.org/plugin/com.ms.gradle.application)
[![JavaDoc](https://img.shields.io/badge/JavaDoc-latest-blue)](http://opensource.morganstanley.com/gradle-plugin-application)
[![Build Status](https://github.com/morganstanley/gradle-plugin-application/actions/workflows/push.yaml/badge.svg?branch=main)](https://github.com/morganstanley/gradle-plugin-application/actions/workflows/push.yaml?query=branch:main)

[Morgan Stanley](https://github.com/morganstanley)'s Application plugin for Gradle allows you to package your Java-based applications for distribution, much like [Gradle's built-in Application plugin](https://docs.gradle.org/current/userguide/application_plugin.html) does, but in a more standard and more flexible way.

The fundamental difference between the two is how the application gets packaged: while Gradle's built-in plugin generates a start script that can run the correct `java -cp myApp.jar:... my.app.Main` command for the user, this plugin goes back to the Java standard ways for packaging applications, and generates an application JAR that the user can simply execute via the command `java -jar myApp.jar`.

Other than that, this plugin is very much like Gradle's: it applies the [Java](https://docs.gradle.org/current/userguide/java_plugin.html) and [Distribution](https://docs.gradle.org/current/userguide/distribution_plugin.html) plugins the same way, and also requires only a single piece of configuration: the name of the main class.

## Advantages

* Uses Java's standard approach to packaging applications
* Platform-independent: no OS-specific start scripts
  * Less code to understand / maintain
  * Less complexity
* You can write the final `java` command any way you like
  * No need to tunnel any options / flags into the command through environment variables
  * To customize the command further, you don't need to understand and override built-in defaults
* Clean handling of dependencies
  * Nicely structured `lib` directory that contains the original dependency JARs in an easily recognizable layout
  * Easy to tell where loaded classes came from (which dependency JAR)
  * No JAR merging issues (forcing everything into a single fat JAR can lead to problems when multiple dependency JARs happen to contain a file at the same path, e.g. the [Shadow plugin](https://github.com/johnrengelman/shadow) requires [special configuration](https://imperceptiblethoughts.com/shadow/configuration/merging) to correctly merge [files like `META-INF/spring.factories`](https://github.com/spring-projects/spring-boot/issues/1828))
  * Works with standard class loaders (unlike [Spring Boot Executable JARs](https://docs.spring.io/spring-boot/docs/current/reference/html/appendix-executable-jar-format.html) that have a few [restrictions](https://docs.spring.io/spring-boot/docs/current/reference/html/appendix-executable-jar-format.html#executable-jar-restrictions))
  * Retains the order of dependencies on the classpath, as they got resolved by Gradle (unlike the `-cp lib/*` approach)
* No command length issue on Windows (forget the dreaded `The input line is too long` error)
* Switching from Gradle's built-in Application plugin is a breeze

## Usage

To use this plugin:
1. Modify your `build.gradle[.kts]` file (see examples below)
   1. Apply the plugin via the `plugins {}` block
   2. Configure your application(s) using the `applications` extension
2. Run the [tasks defined by the Distribution plugin](https://docs.gradle.org/current/userguide/distribution_plugin.html#sec:distribution_tasks) to build your application(s)
   * If you're not familiar with those tasks, read the [Usage section of the Distribution plugin's documentation](https://docs.gradle.org/current/userguide/distribution_plugin.html#sec:distribution_usage)

The plugin adds an extension named `applications` to the project, which is a container of `Application` objects. It also creates a single application in the `applications` container named `main`. If your build only produces one application, you only need to configure this `main` application; setting the `mainClass` property should be enough in most cases.

## Examples

### Groovy

```groovy
// build.gradle

plugins {
    id "com.ms.gradle.application" version "2.0.1"
}

applications.main {
    mainClass = "my.app.Main"
}
```

For more complex examples, see the [build file of the plugin's functional test](test-data/ApplicationPluginFunctionalTest/build.gradle).

### Kotlin

```kotlin
// build.gradle.kts

plugins {
    id("com.ms.gradle.application").version("2.0.1")
}

applications.main {
    mainClass.set("my.app.Main")
}
```

For more complex examples, see the [build file of the plugin's functional test](test-data/ApplicationPluginFunctionalTest/build.gradle.kts).

## Details

Application JARs generated by this plugin are an exact copy of the artifact built by another `Jar` task, with the two necessary headers, [`Class-Path`](https://docs.oracle.com/javase/tutorial/deployment/jar/downman.html) and [`Main-Class`](https://docs.oracle.com/javase/tutorial/deployment/jar/appman.html) added to `META-INF/MANIFEST.MF`. By default, the JAR is written to `$buildDir/application` (directory and file names are customizable).

Each application JAR then gets added to a [distribution](https://docs.gradle.org/current/userguide/distribution_plugin.html#sec:distribution_contents), together with a `lib` directory containing all the dependency artifacts. Each artifact will be copied to `lib/$group/$artifact`, which is also how the `Class-Path` header will refer to them, using [relative URLs](https://docs.oracle.com/javase/8/docs/technotes/guides/jar/jar.html#classpath).

```
<distribution>
├── lib
│   ├── com.google.code.gson
│   │   └── gson-2.10.jar
│   └── org.slf4j
│       ├── slf4j-api-2.0.3.jar
│       └── slf4j-simple-2.0.3.jar
└── myApp.jar
```

The actual application distributions get assembled and written to disk by the [Distribution plugin's tasks](https://docs.gradle.org/current/userguide/distribution_plugin.html#sec:distribution_tasks).

### The `main` application

The `main` application is created and configured automatically based on the [`main` source set](https://docs.gradle.org/current/userguide/java_plugin.html#source_sets). By default, it will:
* Make a modified copy of the artifact built by the [built-in `jar` task](https://docs.gradle.org/current/userguide/java_plugin.html#sec:java_tasks)
* Use the dependencies defined by the [built-in `runtimeClasspath` configuration](https://docs.gradle.org/current/userguide/java_plugin.html#sec:java_plugin_and_dependency_management)

All you need to set yourself is the `mainClass` property, and you're good to go!

### Configuring applications

* The plugin provides a number of settings to customize each application that gets built. For the available options, please refer to the [Javadoc of the `Application` class](http://opensource.morganstanley.com/gradle-plugin-application/com/ms/gradle/application/Application.html).
* If you need to build multiple applications, you can simply use the `applications` container to set them up.

## Credits

This plugin was written by [Denes Daniel](https://github.com/pantherdd), working for [Morgan Stanley](https://github.com/morganstanley)'s Java Platform Engineering team.
