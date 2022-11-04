/*
 * Morgan Stanley makes this available to you under the Apache License, Version 2.0 (the "License"). You may obtain a
 * copy of the License at http://www.apache.org/licenses/LICENSE-2.0. See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

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
