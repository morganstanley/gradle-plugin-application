/*
 * Morgan Stanley makes this available to you under the Apache License, Version 2.0 (the "License"). You may obtain a
 * copy of the License at http://www.apache.org/licenses/LICENSE-2.0. See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.ms.gradle.application;

import org.apache.commons.io.FileUtils;
import org.assertj.core.api.Assertions;
import org.assertj.core.data.MapEntry;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Named;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.distribution.Distribution;
import org.gradle.api.distribution.DistributionContainer;
import org.gradle.api.distribution.plugins.DistributionPlugin;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.bundling.Tar;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

/**
 * Unit tests for {@link ApplicationPlugin}.
 */
class ApplicationPluginTest {

    private static final String TEST_NAME = ApplicationPluginTest.class.getSimpleName();
    private static final String PLUGIN_ID = "com.ms.gradle.application";

    @Nonnull
    private final Project project;

    ApplicationPluginTest() {
        project = ProjectBuilder.builder().withName(TEST_NAME).build();
        project.getPluginManager().apply(PLUGIN_ID);
    }

    /**
     * Trigger {@link Project#afterEvaluate} hooks.
     */
    private void finalizeProject() {
        ProjectInternal projectInternal = (ProjectInternal) project;
        projectInternal.evaluate();
    }

    @Test
    void testPluginsApplied() {
        PluginContainer plugins = project.getPlugins();
        Assertions.assertThat(plugins.hasPlugin(ApplicationPlugin.class)).isTrue();
        Assertions.assertThat(plugins.hasPlugin(JavaPlugin.class)).isTrue();
        Assertions.assertThat(plugins.hasPlugin(DistributionPlugin.class)).isTrue();
    }

    @Test
    void testPluginCantBeAppliedAgain() {
        ApplicationPlugin plugin = project.getPlugins().getPlugin(ApplicationPlugin.class);
        Assertions.assertThatThrownBy(() -> plugin.apply(project)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testPluginUnusableWhenNotApplied() {
        ApplicationPlugin plugin = new ApplicationPlugin();
        Assertions.assertThatThrownBy(plugin::getApplications).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testApplicationsConfigured() {
        NamedDomainObjectContainer<Application> apps = getApplications(project);
        Assertions.assertThat(apps).isNotNull()
                .singleElement().extracting(Application::getName).isEqualTo(Application.MAIN_APPLICATION_NAME);
    }

    @Test
    void testApplicationsExposedAsExtension() {
        NamedDomainObjectContainer<Application> apps = getApplications(project);
        ExtensionContainer extensions = project.getExtensions();
        Assertions.assertThat(extensions.getByName(ApplicationPlugin.EXTENSION_NAME)).isSameAs(apps);
        Assertions.assertThat(extensions.getByType(ApplicationPlugin.EXTENSION_TYPE)).isSameAs(apps);
    }

    @Test
    void testFailureIfApplicationBaseNameIsNull() {
        Application app = getApplications(project).getByName(Application.MAIN_APPLICATION_NAME);
        app.getApplicationBaseName().convention((String) null).set((String) null);
        Assertions.assertThatThrownBy(this::finalizeProject)
                .hasRootCauseInstanceOf(GradleException.class)
                .hasRootCauseMessage("%s must have a non-empty applicationBaseName", app);
    }

    @Test
    void testFailureIfApplicationBaseNameIsEmpty() {
        Application app = getApplications(project).getByName(Application.MAIN_APPLICATION_NAME);
        app.getApplicationBaseName().set("");
        Assertions.assertThatThrownBy(this::finalizeProject)
                .hasRootCauseInstanceOf(GradleException.class)
                .hasRootCauseMessage("%s must have a non-empty applicationBaseName", app);
    }

    @Test
    void testMainApplicationProperties() {
        Application app = getApplications(project).getByName(Application.MAIN_APPLICATION_NAME);
        SourceSet sourceSet = getSourceSets(project).getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        Jar jar = getEnabledTask(project, Jar.class, sourceSet.getJarTaskName());
        Configuration runtimeClasspath = getConfiguration(project, sourceSet.getRuntimeClasspathConfigurationName());

        Assertions.assertThat(app.getProject()).isSameAs(project);
        Assertions.assertThat(app.getName()).isEqualTo(Application.MAIN_APPLICATION_NAME);
        Assertions.assertThat(app.toString())
                .isEqualTo("%s ':%s'", Application.APPLICATION, Application.MAIN_APPLICATION_NAME);
        Assertions.assertThat(app.getDescription()).contains(Application.MAIN_APPLICATION_NAME);
        Assertions.assertThat(app.getApplicationBaseName().getOrNull()).isEqualTo(TEST_NAME);
        Assertions.assertThat(app.getRawJar().getOrNull()).isSameAs(jar);
        Assertions.assertThat(app.getDependencies().getOrNull()).isSameAs(runtimeClasspath);
        Assertions.assertThat(app.getDependencyDirectoryName().getOrNull())
                .isEqualTo(ApplicationJar.DEPENDENCY_DIRECTORY_NAME);
        Assertions.assertThat(app.getMainClass().getOrNull()).isNull();
    }

    @Test
    void testCustomApplicationProperties() {
        SourceSet sourceSet = getSourceSets(project).getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        Jar jar = getEnabledTask(project, Jar.class, sourceSet.getJarTaskName());
        Configuration runtimeClasspath = getConfiguration(project, sourceSet.getRuntimeClasspathConfigurationName());

        Application app = getApplications(project).create("customApp", application -> {
            application.setDescription("Custom description");
            application.fromSourceSet(sourceSet);
            application.getMainClass().set("custom.Main");
        });

        Assertions.assertThat(app.getProject()).isSameAs(project);
        Assertions.assertThat(app.getName()).isEqualTo("customApp");
        Assertions.assertThat(app.toString())
                .isEqualTo("%s ':%s'", Application.APPLICATION, "customApp");
        Assertions.assertThat(app.getDescription()).isEqualTo("Custom description");
        Assertions.assertThat(app.getApplicationBaseName().getOrNull()).isEqualTo(TEST_NAME + "-customApp");
        Assertions.assertThat(app.getRawJar().getOrNull()).isSameAs(jar);
        Assertions.assertThat(app.getDependencies().getOrNull()).isSameAs(runtimeClasspath);
        Assertions.assertThat(app.getDependencyDirectoryName().getOrNull())
                .isEqualTo(ApplicationJar.DEPENDENCY_DIRECTORY_NAME);
        Assertions.assertThat(app.getMainClass().getOrNull()).isEqualTo("custom.Main");

        app.fromSourceSet((SourceSet) null);
        Assertions.assertThat(app.getRawJar().getOrNull()).isNull();
        Assertions.assertThat(app.getDependencies().getOrNull()).isNull();

        app.fromSourceSet(sourceSet);
        app.getRawJar().set((Jar) null);
        Assertions.assertThat(app.getRawJar().getOrNull()).isNull();
        Assertions.assertThat(app.getDependencies().getOrNull()).isSameAs(runtimeClasspath);

        app.fromSourceSet(sourceSet);
        app.getDependencies().set((Configuration) null);
        Assertions.assertThat(app.getRawJar().getOrNull()).isSameAs(jar);
        Assertions.assertThat(app.getDependencies().getOrNull()).isNull();
    }

    @Test
    void testMainApplicationJar() {
        Application app = getApplications(project).getByName(Application.MAIN_APPLICATION_NAME);
        TaskProvider<ApplicationJar> appJarRetrieved = app.getApplicationJar();
        AtomicReference<ApplicationJar> appJarConfigured = captureConfigured(app::applicationJar);

        ApplicationJar appJar =
                getEnabledTask(project, ApplicationJar.class, Application.MAIN_APPLICATION_JAR_TASK_NAME);

        Assertions.assertThat(appJarRetrieved).isNotNull();
        Assertions.assertThat(appJarRetrieved.getOrNull()).isSameAs(appJar);
        Assertions.assertThat(appJarConfigured.get()).isSameAs(appJar);
    }

    @Test
    void testMainApplicationJarProperties() {
        Application app = getApplications(project).getByName(Application.MAIN_APPLICATION_NAME);
        ApplicationJar appJar = app.getApplicationJar().get();

        SourceSet sourceSet = getSourceSets(project).getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        Jar jar = getEnabledTask(project, Jar.class, sourceSet.getJarTaskName());
        Configuration runtimeClasspath = getConfiguration(project, sourceSet.getRuntimeClasspathConfigurationName());

        Assertions.assertThat(appJar.getProject()).isSameAs(project);
        Assertions.assertThat(appJar.getName()).isEqualTo(Application.MAIN_APPLICATION_JAR_TASK_NAME);
        Assertions.assertThat(appJar.getDescription()).contains(Application.MAIN_APPLICATION_NAME);
        Assertions.assertThat(appJar.getRawJar()).isSameAs(app.getRawJar());
        Assertions.assertThat(appJar.getRawJar().getOrNull()).isSameAs(jar);
        Assertions.assertThat(appJar.getDependencies()).isSameAs(app.getDependencies());
        Assertions.assertThat(appJar.getDependencies().getOrNull()).isSameAs(runtimeClasspath);
        Assertions.assertThat(appJar.getDependencyDirectoryName()).isSameAs(app.getDependencyDirectoryName());
        Assertions.assertThat(appJar.getDependencyDirectoryName().getOrNull())
                .isEqualTo(ApplicationJar.DEPENDENCY_DIRECTORY_NAME);
        Assertions.assertThat(appJar.getMainClass()).isSameAs(app.getMainClass());
        Assertions.assertThat(appJar.getMainClass().getOrNull()).isNull();
        Assertions.assertThat(appJar.getDestinationDirectory().getAsFile().getOrNull())
                .isEqualTo(new File(project.getBuildDir(), ApplicationJar.APPLICATION_DIRECTORY_NAME));
        Assertions.assertThat(appJar.getArchiveBaseName().getOrNull()).isEqualTo(TEST_NAME);
    }

    @Test
    void testCustomApplicationJarProperties() {
        SourceSet sourceSet = getSourceSets(project).getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        Jar jar = getEnabledTask(project, Jar.class, sourceSet.getJarTaskName());
        Configuration runtimeClasspath = getConfiguration(project, sourceSet.getRuntimeClasspathConfigurationName());

        ApplicationJar appJar = project.getTasks().create("customAppJar", ApplicationJar.class, applicationJar -> {
            applicationJar.setDescription("Custom description");
            applicationJar.fromSourceSet(sourceSet);
            applicationJar.getMainClass().set("custom.Main");
        });

        Assertions.assertThat(appJar.getProject()).isSameAs(project);
        Assertions.assertThat(appJar.getName()).isEqualTo("customAppJar");
        Assertions.assertThat(appJar.getDescription()).isEqualTo("Custom description");
        Assertions.assertThat(appJar.getRawJar().getOrNull()).isSameAs(jar);
        Assertions.assertThat(appJar.getDependencies().getOrNull()).isSameAs(runtimeClasspath);
        Assertions.assertThat(appJar.getDependencyDirectoryName().getOrNull())
                .isEqualTo(ApplicationJar.DEPENDENCY_DIRECTORY_NAME);
        Assertions.assertThat(appJar.getMainClass().getOrNull()).isEqualTo("custom.Main");
        Assertions.assertThat(appJar.getDestinationDirectory().getAsFile().getOrNull())
                .isEqualTo(new File(project.getBuildDir(), ApplicationJar.APPLICATION_DIRECTORY_NAME));
        Assertions.assertThat(appJar.getArchiveBaseName().getOrNull()).isEqualTo(TEST_NAME);

        appJar.fromSourceSet((SourceSet) null);
        Assertions.assertThat(appJar.getRawJar().getOrNull()).isNull();
        Assertions.assertThat(appJar.getDependencies().getOrNull()).isNull();

        appJar.fromSourceSet(sourceSet);
        appJar.getRawJar().set((Jar) null);
        Assertions.assertThat(appJar.getRawJar().getOrNull()).isNull();
        Assertions.assertThat(appJar.getDependencies().getOrNull()).isSameAs(runtimeClasspath);

        appJar.fromSourceSet(sourceSet);
        appJar.getDependencies().set((Configuration) null);
        Assertions.assertThat(appJar.getRawJar().getOrNull()).isSameAs(jar);
        Assertions.assertThat(appJar.getDependencies().getOrNull()).isNull();
    }

    @Test
    void testCustomApplicationJarCopySpec() throws IOException {
        project.getRepositories().mavenCentral();
        DependencyHandler dependencies = project.getDependencies();
        SourceSet sourceSet = getSourceSets(project).getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        dependencies.add(sourceSet.getImplementationConfigurationName(), "com.google.code.gson:gson:2.10");
        dependencies.add(sourceSet.getImplementationConfigurationName(), "org.slf4j:slf4j-api:2.0.3");
        dependencies.add(sourceSet.getRuntimeOnlyConfigurationName(), "org.slf4j:slf4j-simple:2.0.3");

        ApplicationJar appJar = project.getTasks().create("customAppJar", ApplicationJar.class, applicationJar -> {
            applicationJar.getArchiveFileName().set("myApp.jar");
            applicationJar.getDependencies().set(applicationJar.locateDependencies(sourceSet));
            applicationJar.getMainClass().set("custom.Main");
        });

        // Run the `ApplicationJar` task
        Files.createDirectories(appJar.getDestinationDirectory().get().getAsFile().toPath());
        appJar.copy();

        // Run a `Sync` task that uses `ApplicationJar.applicationCopySpec`
        Map<String, RelativePath> pathsInSyncSpec = new TreeMap<>();
        Map<String, RelativePath> pathsInAppCopySpec = new TreeMap<>();
        FileUtils.write(project.file("README.md"), "Read me!", StandardCharsets.UTF_8);
        WorkResult syncResult = project.sync(syncSpec -> syncSpec
                .from(project.file("README.md"))
                .into(project.getLayout().getBuildDirectory().dir("syncTest"))
                .eachFile(copyDetails -> putPathEntry(pathsInSyncSpec, copyDetails))
                .with(appJar.applicationCopySpec()
                        .into("app")
                        .eachFile(copyDetails -> putPathEntry(pathsInAppCopySpec, copyDetails))));

        // Verify what the `eachFile` actions saw (see docs of `ApplicationJar.applicationCopySpec`)
        Assertions.assertThat(syncResult.getDidWork()).isTrue();
        Assertions.assertThat(pathsInSyncSpec).containsOnly(
                pathEntry("README.md", "README.md"),
                pathEntry("myApp.jar", "app/myApp.jar"),
                pathEntry("gson-2.10.jar", "app/gson-2.10.jar"),
                pathEntry("slf4j-api-2.0.3.jar", "app/slf4j-api-2.0.3.jar"),
                pathEntry("slf4j-simple-2.0.3.jar", "app/slf4j-simple-2.0.3.jar"));
        Assertions.assertThat(pathsInAppCopySpec).containsOnly(
                pathEntry("myApp.jar", "app/myApp.jar"),
                pathEntry("gson-2.10.jar", "app/lib/com.google.code.gson/gson-2.10.jar"),
                pathEntry("slf4j-api-2.0.3.jar", "app/lib/org.slf4j/slf4j-api-2.0.3.jar"),
                pathEntry("slf4j-simple-2.0.3.jar", "app/lib/org.slf4j/slf4j-simple-2.0.3.jar"));
    }

    @Test
    void testMainApplicationConfiguration() {
        Application app = getApplications(project).getByName(Application.MAIN_APPLICATION_NAME);
        NamedDomainObjectProvider<Configuration> appConfRetrieved = app.getConfiguration();
        AtomicReference<Configuration> appConfConfigured = captureConfigured(app::configuration);

        Configuration appConf = getConfiguration(project, Application.MAIN_APPLICATION_CONFIGURATION_NAME);

        Assertions.assertThat(appConfRetrieved).isNotNull();
        Assertions.assertThat(appConfRetrieved.getOrNull()).isSameAs(appConf);
        Assertions.assertThat(appConfConfigured.get()).isSameAs(appConf);
    }

    @Test
    void testMainApplicationConfigurationProperties() {
        Application app = getApplications(project).getByName(Application.MAIN_APPLICATION_NAME);
        Configuration appConf = app.getConfiguration().get();

        ApplicationJar appJar = app.getApplicationJar().get();
        SourceSet sourceSet = getSourceSets(project).getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        Configuration runtimeClasspath = getConfiguration(project, sourceSet.getRuntimeClasspathConfigurationName());

        Assertions.assertThat(appConf.getName()).isEqualTo(Application.MAIN_APPLICATION_CONFIGURATION_NAME);
        Assertions.assertThat(appConf.getDescription()).contains(Application.MAIN_APPLICATION_NAME);
        Assertions.assertThat(appConf.isVisible()).isFalse();
        Assertions.assertThat(appConf.isCanBeConsumed()).isTrue();
        Assertions.assertThat(appConf.isCanBeResolved()).isFalse();
        // appConf.getExtendsFrom() can't be verified yet; we will do it after finalizeProject()
        Assertions.assertThat(appConf.getAttributes()).satisfies(attributes -> {
            Assertions.assertThat(attributes.keySet()).hasSize(5);
            Assertions.assertThat(getAttribute(attributes, Usage.USAGE_ATTRIBUTE)).isEqualTo(Usage.JAVA_RUNTIME);
            Assertions.assertThat(getAttribute(attributes, Category.CATEGORY_ATTRIBUTE)).isEqualTo(Category.LIBRARY);
            Assertions.assertThat(getAttribute(attributes, Bundling.BUNDLING_ATTRIBUTE)).isEqualTo(Bundling.EXTERNAL);
            Assertions.assertThat(getAttribute(attributes, LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE))
                    .isEqualTo(Application.LIBRARY_ELEMENTS_APPLICATION_JAR);
            Assertions.assertThat(getAttribute(attributes, ApplicationName.APPLICATION_NAME_ATTRIBUTE))
                    .isEqualTo(Application.MAIN_APPLICATION_NAME);
        });
        Assertions.assertThat(appConf.getArtifacts()).singleElement().satisfies(publishArtifact -> {
            Assertions.assertThat(publishArtifact.getName()).isEqualTo(TEST_NAME);
            Assertions.assertThat(publishArtifact.getExtension()).isEqualTo(Jar.DEFAULT_EXTENSION);
            Assertions.assertThat(publishArtifact.getType()).isEqualTo(Jar.DEFAULT_EXTENSION);
            Assertions.assertThat(publishArtifact.getClassifier()).isEmpty();
            Assertions.assertThat(publishArtifact.getFile()).isEqualTo(appJar.getArchiveFile().get().getAsFile());
        });

        finalizeProject();
        Assertions.assertThat(appConf.getExtendsFrom()).containsOnly(runtimeClasspath);
    }

    @Test
    void testMainApplicationDistribution() {
        Application app = getApplications(project).getByName(Application.MAIN_APPLICATION_NAME);
        NamedDomainObjectProvider<Distribution> appDistRetrieved = app.getDistribution();
        AtomicReference<Distribution> appDistConfigured = captureConfigured(app::distribution);

        Distribution appDist = getDistributions(project).getByName(Application.MAIN_APPLICATION_NAME);

        Assertions.assertThat(appDistRetrieved).isNotNull();
        Assertions.assertThat(appDistRetrieved.getOrNull()).isSameAs(appDist);
        Assertions.assertThat(appDistConfigured.get()).isSameAs(appDist);
    }

    @Test
    void testMainApplicationDistributionProperties() {
        Application app = getApplications(project).getByName(Application.MAIN_APPLICATION_NAME);
        Distribution appDist = app.getDistribution().get();

        Assertions.assertThat(appDist.getName()).isEqualTo(Application.MAIN_APPLICATION_NAME);
        Assertions.assertThat(appDist.getDistributionBaseName().getOrNull()).isEqualTo(TEST_NAME);
        getEnabledTask(project, Sync.class, DistributionPlugin.TASK_INSTALL_NAME);
        getEnabledTask(project, Zip.class, "distZip"); // DistributionPlugin.TASK_DIST_ZIP_NAME
        getEnabledTask(project, Tar.class, "distTar"); // DistributionPlugin.TASK_DIST_TAR_NAME
    }

    @Nonnull
    private static <T> AtomicReference<T> captureConfigured(@Nonnull Consumer<Action<T>> configureMethod) {
        AtomicReference<T> reference = new AtomicReference<>();
        configureMethod.accept(subject -> {
            Assertions.assertThat(subject).isNotNull();
            // The supplied action should only be executed once
            boolean subjectSaved = reference.compareAndSet(null, subject);
            Assertions.assertThat(subjectSaved).isTrue();
        });
        return reference;
    }

    private static void putPathEntry(@Nonnull Map<String, RelativePath> paths, @Nonnull FileCopyDetails copyDetails) {
        paths.put(copyDetails.getSourceName(), copyDetails.getRelativePath());
    }

    @Nonnull
    private static MapEntry<String, RelativePath> pathEntry(@Nonnull String sourceName, @Nonnull String relativePath) {
        return Assertions.entry(sourceName, RelativePath.parse(true, relativePath));
    }

    @Nonnull
    private static <T extends Task> T getEnabledTask(
            @Nonnull Project project, @Nonnull Class<T> type, @Nonnull String name) {
        T task = project.getTasks().withType(type).getByName(name);
        Assertions.assertThat(task.getEnabled()).isTrue();
        return task;
    }

    @Nonnull
    private static Configuration getConfiguration(@Nonnull Project project, @Nonnull String name) {
        return project.getConfigurations().getByName(name);
    }

    @Nonnull
    private static String getAttribute(
            @Nonnull AttributeContainer attributes, @Nonnull Attribute<? extends Named> attribute) {
        Named value = attributes.getAttribute(attribute);
        Assertions.assertThat(value).isNotNull();
        return value.getName();
    }

    @Nonnull
    private static NamedDomainObjectContainer<Application> getApplications(@Nonnull Project project) {
        return project.getPlugins().getPlugin(ApplicationPlugin.class).getApplications();
    }

    @Nonnull
    private static SourceSetContainer getSourceSets(@Nonnull Project project) {
        return project.getExtensions().getByType(SourceSetContainer.class);
    }

    @Nonnull
    private static DistributionContainer getDistributions(@Nonnull Project project) {
        return project.getExtensions().getByType(DistributionContainer.class);
    }
}
