package com.ms.gradle.application;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Utility methods.
 */
final class Utils {

    /**
     * This is a utility class with static methods only.
     */
    private Utils() {}

    static void argument(boolean isValid, @Nonnull String format, @Nullable Object... args) {
        if (!isValid) {
            throw new IllegalArgumentException(String.format(format, args));
        }
    }

    @Nonnull
    @SuppressFBWarnings(value = "NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE",
            justification = "This method validates that a @Nullable value is in fact @Nonnull")
    static <T> T nonNull(@Nullable T value, @Nonnull String name) {
        if (value == null) {
            throw new NullPointerException(name + " must not be null");
        }
        return value;
    }

    @Nonnull
    @SuppressFBWarnings(value = "NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE",
            justification = "This method validates that a @Nullable value is in fact @Nonnull")
    static String nonEmpty(@Nullable String value, @Nonnull String name) {
        nonNull(value, name);
        argument(!value.isEmpty(), "%s must not be empty", name);
        return value;
    }

    @Nonnull
    @SuppressFBWarnings(value = "NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE",
            justification = "This method validates that a @Nullable value is in fact @Nonnull")
    static <T, C extends Iterable<T>> C nonNullElements(@Nullable C values, @Nonnull String name) {
        nonNull(values, name);
        for (T value : values) {
            nonNull(value, "Elements of " + name);
        }
        return values;
    }

    @Nonnull
    static String capitalize(@Nonnull String str) {
        // Use the same approach as the Gradle Distribution plugin (org.apache.commons.lang.StringUtils.capitalize)
        return str.isEmpty() ? str : Character.toTitleCase(str.charAt(0)) + str.substring(1);
    }

    @Nonnull
    static <T> Optional<T> getFinalizedValue(@Nonnull Property<T> property) {
        property.finalizeValue();
        return Optional.ofNullable(property.getOrNull());
    }

    static void finalizeValues(@Nonnull Property<?>... properties) {
        for (Property<?> property : properties) {
            property.finalizeValue();
        }
    }

    @Nonnull
    static SourceSetContainer sourceSets(@Nonnull Project project) {
        return project.getExtensions().getByType(SourceSetContainer.class);
    }

    @Nonnull
    static SourceSet inProject(@Nonnull Project project, @Nonnull SourceSet sourceSet) {
        argument(sourceSets(project).contains(sourceSet), "The source set must be in the given project");
        return sourceSet;
    }
}
