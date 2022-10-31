package com.ms.gradle.application;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.bundling.Jar;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A specification for building a runnable JAR.
 *
 * @see Application
 * @see ApplicationJar
 */
public interface ApplicationSpec {

    /**
     * @return The project that this {@link ApplicationSpec} belongs to.
     */
    @Internal
    @Nonnull
    Project getProject();

    /**
     * Locates the {@link #getRawJar raw JAR} task associated with the given {@link SourceSet}.
     *
     * @param sourceSet The {@link SourceSet} whose {@link #getRawJar raw JAR} task to locate.
     * @return The {@link SourceSet#getJarTaskName Jar} task of the given {@link SourceSet}.
     * @see #locateRawJar(Provider)
     */
    @Nonnull
    default Provider<Jar> locateRawJar(@Nonnull SourceSet sourceSet) {
        Project project = getProject();
        String jarTaskName = Utils.inProject(project, sourceSet).getJarTaskName();
        return project.getTasks().named(jarTaskName, Jar.class);
    }

    /**
     * Locates the {@link #getRawJar raw JAR} task associated with the given {@link SourceSet}.
     *
     * @param sourceSet The {@link SourceSet} whose {@link #getRawJar raw JAR} task to locate.
     * @return The {@link SourceSet#getJarTaskName Jar} task of the given {@link SourceSet}.
     * @see #locateRawJar(SourceSet)
     */
    @Nonnull
    default Provider<Jar> locateRawJar(@Nonnull Provider<SourceSet> sourceSet) {
        return sourceSet.flatMap(this::locateRawJar);
    }

    /**
     * Locates the {@link #getDependencies dependencies} configuration associated with the given {@link SourceSet}.
     *
     * @param sourceSet The {@link SourceSet} whose {@link #getDependencies dependencies} configuration to locate.
     * @return The {@link SourceSet#getRuntimeClasspathConfigurationName runtime classpath} configuration of the given
     * {@link SourceSet}.
     * @see #locateDependencies(Provider)
     */
    @Nonnull
    default Provider<Configuration> locateDependencies(@Nonnull SourceSet sourceSet) {
        Project project = getProject();
        String configurationName = Utils.inProject(project, sourceSet).getRuntimeClasspathConfigurationName();
        return project.getConfigurations().named(configurationName);
    }

    /**
     * Locates the {@link #getDependencies dependencies} configuration associated with the given {@link SourceSet}.
     *
     * @param sourceSet The {@link SourceSet} whose {@link #getDependencies dependencies} configuration to locate.
     * @return The {@link SourceSet#getRuntimeClasspathConfigurationName runtime classpath} configuration of the given
     * {@link SourceSet}.
     * @see #locateDependencies(SourceSet)
     */
    @Nonnull
    default Provider<Configuration> locateDependencies(@Nonnull Provider<SourceSet> sourceSet) {
        return sourceSet.flatMap(this::locateDependencies);
    }

    /**
     * <p>Configures the application to build from a {@link SourceSet}.</p>
     * <p>Calling this method is equivalent to setting the following properties:</p>
     * <ul>
     * <li>{@link #getRawJar rawJar} {@code =} {@link #locateRawJar(SourceSet)}</li>
     * <li>{@link #getDependencies dependencies} {@code =} {@link #locateDependencies(SourceSet)}</li>
     * </ul>
     *
     * @param sourceSet The {@link SourceSet} to build the application from.
     * @see #fromSourceSet(Provider)
     */
    default void fromSourceSet(@Nullable SourceSet sourceSet) {
        if (sourceSet != null) {
            getRawJar().set(locateRawJar(sourceSet));
            getDependencies().set(locateDependencies(sourceSet));
        } else {
            getRawJar().set((Jar) null);
            getDependencies().set((Configuration) null);
        }
    }

    /**
     * <p>Configures the application to build from a {@link SourceSet}.</p>
     * <p>Calling this method is equivalent to setting the following properties:</p>
     * <ul>
     * <li>{@link #getRawJar rawJar} {@code =} {@link #locateRawJar(SourceSet)}</li>
     * <li>{@link #getDependencies dependencies} {@code =} {@link #locateDependencies(SourceSet)}</li>
     * </ul>
     *
     * @param sourceSet The {@link SourceSet} to build the application from.
     * @see #fromSourceSet(SourceSet)
     */
    default void fromSourceSet(@Nonnull Provider<SourceSet> sourceSet) {
        getRawJar().set(locateRawJar(sourceSet));
        getDependencies().set(locateDependencies(sourceSet));
    }

    /**
     * @return {@link Jar} task whose output to enhance with the application's classpath and main class metadata.
     * @see #locateRawJar(SourceSet)
     * @see #fromSourceSet(SourceSet)
     * @see #fromSourceSet(Provider)
     */
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    @Optional
    @Nonnull
    Property<Jar> getRawJar();

    /**
     * @return {@link Configuration} to resolve the application's dependencies from. The application's classpath will
     * consist of the resolved dependency artifact files.
     * @see #locateDependencies(SourceSet)
     * @see #fromSourceSet(SourceSet)
     * @see #fromSourceSet(Provider)
     */
    @Classpath
    @Optional
    @Nonnull
    Property<Configuration> getDependencies();

    /**
     * @return Name of the directory that will contain the application's dependencies.
     */
    @Input
    @Nonnull
    Property<String> getDependencyDirectoryName();

    /**
     * @return The fully qualified name of the application's main class.
     */
    @Input
    @Nonnull
    Property<String> getMainClass();
}
