/*
 * Morgan Stanley makes this available to you under the Apache License, Version 2.0 (the "License"). You may obtain a
 * copy of the License at http://www.apache.org/licenses/LICENSE-2.0. See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.ms.gradle.application;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Named;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.distribution.Distribution;
import org.gradle.api.distribution.DistributionContainer;
import org.gradle.api.distribution.plugins.DistributionPlugin;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.util.Path;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * An {@link Application} is a specification for building a runnable JAR (i.e. one that {@code java -jar} is able to
 * execute directly). Based on the specification, Gradle will make an exact copy of an existing {@link Jar} task's
 * output, add the necessary attributes to the copy's {@link Manifest}, and set up a {@link Distribution} to bundle this
 * enhanced JAR with its dependencies.
 *
 * @see ApplicationJar
 * @see <a href="https://docs.oracle.com/javase/tutorial/deployment/jar/run.html">JAR Files as Applications</a>
 */
public abstract class Application implements ApplicationSpec, Named {

    /**
     * Identifier to be used in names.
     */
    public static final String APPLICATION = "application";

    /**
     * Name of the main application.
     */
    public static final String MAIN_APPLICATION_NAME = DistributionPlugin.MAIN_DISTRIBUTION_NAME;

    /**
     * Name of the main application JAR task.
     */
    public static final String MAIN_APPLICATION_JAR_TASK_NAME =
            APPLICATION + Utils.capitalize(JavaPlugin.JAR_TASK_NAME);

    /**
     * Name of the main application configuration.
     */
    public static final String MAIN_APPLICATION_CONFIGURATION_NAME = APPLICATION;

    /**
     * {@link LibraryElements} attribute value indicating a {@link LibraryElements#JAR JAR} artifact that can be started
     * directly as an application (using the {@code java -jar} command).
     *
     * @see <a href="https://docs.oracle.com/javase/tutorial/deployment/jar/run.html">JAR Files as Applications</a>
     * @see LibraryElements#JAR
     */
    public static final String LIBRARY_ELEMENTS_APPLICATION_JAR = APPLICATION + "-" + LibraryElements.JAR;

    @Nonnull
    private final Project project;
    @Nonnull
    private final String name;
    @Nonnull
    private final Path identityPath;
    @Nullable
    private String description;

    @Nonnull
    private final TaskProvider<ApplicationJar> applicationJar;
    @Nonnull
    private final NamedDomainObjectProvider<Configuration> configuration;
    @Nonnull
    private final NamedDomainObjectProvider<Distribution> distribution;

    /**
     * Creates an {@link Application} instance.
     *
     * @param project The project that the application belongs to.
     * @param name The name of the application.
     */
    @Inject
    public Application(@Nonnull Project project, @Nonnull String name) {
        this.project = Utils.nonNull(project, "project");
        this.name = Utils.nonEmpty(name, "name");
        this.identityPath = buildIdentityPath();
        setDescription("The " + this.name + " application.");

        getApplicationBaseName().convention(defaultApplicationBaseName());
        this.applicationJar = registerApplicationJar();
        this.configuration = registerConfiguration();
        this.distribution = registerDistribution();
    }

    @Nonnull
    private Path buildIdentityPath() {
        return Path.path(project.getPath()).child(name);
    }

    /**
     * @return The default base name of this application.
     * @see #getApplicationBaseName()
     */
    @Nonnull
    private String defaultApplicationBaseName() {
        return MAIN_APPLICATION_NAME.equals(name) ?
                project.getName() :
                project.getName() + "-" + name;
    }

    @Nonnull
    private TaskProvider<ApplicationJar> registerApplicationJar() {
        String applicationJarTaskName = MAIN_APPLICATION_NAME.equals(name) ?
                MAIN_APPLICATION_JAR_TASK_NAME :
                name + Utils.capitalize(MAIN_APPLICATION_JAR_TASK_NAME);
        project.getTasks().register(applicationJarTaskName, ApplicationJar.Bound.class, this);
        return project.getTasks().named(applicationJarTaskName, ApplicationJar.class);
    }

    @Nonnull
    private NamedDomainObjectProvider<Configuration> registerConfiguration() {
        String configurationName = MAIN_APPLICATION_NAME.equals(name) ?
                MAIN_APPLICATION_CONFIGURATION_NAME :
                name + Utils.capitalize(MAIN_APPLICATION_CONFIGURATION_NAME);
        return project.getConfigurations().register(configurationName, this::configureConfiguration);
    }

    /**
     * Configures the {@link Configuration} for the application. Based on how {@link JavaPlugin#configureConfigurations}
     * sets up the built-in {@link JavaPlugin#RUNTIME_ELEMENTS_CONFIGURATION_NAME runtimeElements} configuration, with
     * the main difference being that application configurations have their {@link LibraryElements} attribute set to
     * {@value LIBRARY_ELEMENTS_APPLICATION_JAR} instead of {@value LibraryElements#JAR}.
     *
     * @param configuration The {@link Configuration} to configure.
     */
    private void configureConfiguration(@Nonnull Configuration configuration) {
        configuration.setDescription(
                "The " + name + " application's runtime elements (application JAR artifact and its dependencies).");
        configuration.setVisible(false);
        configuration.setCanBeConsumed(true);
        configuration.setCanBeResolved(false);
        // configuration.extendsFrom(...) can't be called yet; we will set that up in finalizeProperties()
        configuration.attributes(attributes -> {
            setAttribute(attributes, Usage.USAGE_ATTRIBUTE, Usage.JAVA_RUNTIME);
            setAttribute(attributes, Category.CATEGORY_ATTRIBUTE, Category.LIBRARY);
            setAttribute(attributes, Bundling.BUNDLING_ATTRIBUTE, Bundling.EXTERNAL);
            // See: https://docs.gradle.org/current/userguide/cross_project_publications.html#sec:variant-aware-sharing
            setAttribute(attributes, LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, LIBRARY_ELEMENTS_APPLICATION_JAR);
        });
        configuration.getOutgoing().artifact(getApplicationJar());
    }

    private <T extends Named> void setAttribute(
            @Nonnull AttributeContainer attributes, @Nonnull Attribute<T> key, @Nonnull String value) {
        attributes.attribute(key, project.getObjects().named(key.getType(), value));
    }

    @Nonnull
    private NamedDomainObjectProvider<Distribution> registerDistribution() {
        DistributionContainer distributions = project.getExtensions().getByType(DistributionContainer.class);
        try {
            return distributions.register(name, this::configureDistribution);
        } catch (InvalidUserDataException e) {
            // For cases when the distribution is already created (e.g. main distribution, see DistributionPlugin.apply)
            return distributions.named(name, this::configureDistribution);
        }
    }

    private void configureDistribution(@Nonnull Distribution distribution) {
        // Avoid second error thrown by DistributionPlugin if the user forces getApplicationBaseName() to null or empty
        Provider<String> distributionBaseName = getApplicationBaseName()
                .map(baseName -> baseName.isEmpty() ? defaultApplicationBaseName() : baseName)
                .orElse(project.getProviders().provider(this::defaultApplicationBaseName));
        distribution.getDistributionBaseName().convention(distributionBaseName);
        distribution.contents(contents -> contents.with(getApplicationJar().get().applicationCopySpec()));
    }

    /**
     * @return The project this application belongs to.
     */
    @Nonnull
    @Override
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "The project is not internal to this class")
    public Project getProject() {
        return project;
    }

    /**
     * @return The name of this application.
     */
    @Nonnull
    @Override
    public String getName() {
        return name;
    }

    /**
     * @return The display name of this {@link Application} that can be used in log and error messages.
     */
    @Nonnull
    @Override
    public String toString() {
        return APPLICATION + " '" + identityPath + "'";
    }

    /**
     * @return The description of the application (for informational purposes).
     */
    @Nullable
    public String getDescription() {
        return description;
    }

    /**
     * @param description The description of the application (for informational purposes).
     */
    public void setDescription(@Nullable String description) {
        this.description = description;
    }

    /**
     * @return The base name of this application, used in naming the application JAR and the distribution archives. The
     * default value is as follows:
     * <ul>
     * <li>For the main application, it is "{@code $project.name}".</li>
     * <li>For other applications, it is "{@code $project.name-$this.name}".</li>
     * </ul>
     * @see Distribution#getDistributionBaseName()
     */
    @Nonnull
    public abstract Property<String> getApplicationBaseName();

    /**
     * @return The {@link ApplicationJar} for the application.
     */
    @Nonnull
    public TaskProvider<ApplicationJar> getApplicationJar() {
        return applicationJar;
    }

    /**
     * Configures the {@link #getApplicationJar ApplicationJar} for the application.
     *
     * @param action Action to configure the {@link ApplicationJar}.
     */
    public void applicationJar(@Nonnull Action<? super ApplicationJar> action) {
        applicationJar.configure(action);
    }

    /**
     * @return The {@link Configuration} for the application, representing its runtime elements
     * ({@linkplain #getApplicationJar application JAR} artifact and its {@linkplain #getDependencies dependencies}).
     * @see #getApplicationJar() applicationJar
     * @see #getDependencies() dependencies
     */
    @Nonnull
    public NamedDomainObjectProvider<Configuration> getConfiguration() {
        return configuration;
    }

    /**
     * Configures the {@link #getConfiguration Configuration} for the application.
     *
     * @param action Action to configure the {@link Configuration}.
     */
    public void configuration(@Nonnull Action<? super Configuration> action) {
        configuration.configure(action);
    }

    /**
     * @return The {@link Distribution} for the application.
     */
    @Nonnull
    public NamedDomainObjectProvider<Distribution> getDistribution() {
        return distribution;
    }

    /**
     * Configures the {@link #getDistribution Distribution} for the application.
     *
     * @param action Action to configure the {@link Distribution}.
     */
    public void distribution(@Nonnull Action<? super Distribution> action) {
        distribution.configure(action);
    }

    /**
     * Finalizes and validates the properties of this application. Any further attempts to make changes will result in
     * an {@code IllegalStateException}.
     *
     * @throws GradleException If the application's configuration is invalid.
     * @see Property#finalizeValue()
     */
    public void finalizeProperties() {
        // Disallow empty application base names (based on: project.afterEvaluate in DistributionPlugin.apply)
        if (Utils.getFinalizedValue(getApplicationBaseName()).orElse("").isEmpty()) {
            throw new GradleException(String.format("%s must have a non-empty applicationBaseName", this));
        }

        // There is no Gradle API to make a Configuration extend another "lazily", i.e. without explicitly specifying
        // the other configuration at the time of the call. Such an API, e.g. `extendsFrom(Provider<Configuration>)`
        // would allow us to set this up during construction, but without it, this is our only option.
        Utils.getFinalizedValue(getDependencies()).ifPresent(dependencies ->
                getConfiguration().configure(configuration -> configuration.extendsFrom(dependencies)));

        // Simply finalize the remaining properties
        Utils.finalizeValues(
                getRawJar(),
                getDependencyDirectoryName(),
                getMainClass());
    }
}
