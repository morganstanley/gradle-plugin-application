package com.ms.gradle.application;

import org.assertj.core.api.Assertions;
import org.gradle.api.reflect.TypeOf;
import org.junit.jupiter.api.Test;

/**
 * Simple tests for public {@link ApplicationPlugin} constants.
 */
class ApplicationPluginConstantsTest {

    @Test
    void testApplicationConstants() {
        Assertions.assertThat(Application.APPLICATION).isEqualTo("application");
        Assertions.assertThat(Application.MAIN_APPLICATION_NAME).isEqualTo("main");
        Assertions.assertThat(Application.MAIN_APPLICATION_JAR_TASK_NAME).isEqualTo("applicationJar");
        Assertions.assertThat(Application.MAIN_APPLICATION_CONFIGURATION_NAME).isEqualTo("application");
        Assertions.assertThat(Application.LIBRARY_ELEMENTS_APPLICATION_JAR).isEqualTo("application-jar");
    }

    @Test
    void testApplicationJarConstants() {
        Assertions.assertThat(ApplicationJar.APPLICATION_DIRECTORY_NAME).isEqualTo("application");
        Assertions.assertThat(ApplicationJar.DEPENDENCY_DIRECTORY_NAME).isEqualTo("lib");
    }

    @Test
    void testApplicationPluginConstants() throws NoSuchMethodException {
        Assertions.assertThat(ApplicationPlugin.TASK_GROUP).isEqualTo("application");
        Assertions.assertThat(ApplicationPlugin.EXTENSION_NAME).isEqualTo("applications");
        Assertions.assertThat(ApplicationPlugin.EXTENSION_TYPE).isEqualTo(TypeOf.typeOf(
                ApplicationPlugin.class.getMethod("getApplications").getGenericReturnType()));
    }
}
