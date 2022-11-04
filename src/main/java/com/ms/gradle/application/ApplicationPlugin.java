/*
 * Morgan Stanley makes this available to you under the Apache License, Version 2.0 (the "License"). You may obtain a
 * copy of the License at http://www.apache.org/licenses/LICENSE-2.0. See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.ms.gradle.application;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.distribution.plugins.DistributionPlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.reflect.TypeOf;
import org.gradle.api.tasks.SourceSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @see <a href="https://github.com/morganstanley/gradle-plugin-application">Morgan Stanley's Application plugin</a>
 */
public class ApplicationPlugin implements Plugin<Project> {

    /**
     * Name of the task group that this plugin uses.
     */
    public static final String TASK_GROUP = Application.APPLICATION;

    /**
     * <p>Name of the project extension ({@link Application} container) that this plugin provides.</p>
     * <p>Usage: {@code project.getExtensions().getByName(ApplicationPlugin.EXTENSION_NAME)}.</p>
     *
     * @see #getApplications()
     * @see #EXTENSION_TYPE
     */
    public static final String EXTENSION_NAME = Application.APPLICATION + "s";

    /**
     * <p>Type of the project extension ({@link Application} container) that this plugin provides.</p>
     * <p>Usage: {@code project.getExtensions().getByType(ApplicationPlugin.EXTENSION_TYPE)}.</p>
     *
     * @see #getApplications()
     * @see #EXTENSION_NAME
     */
    public static final TypeOf<NamedDomainObjectContainer<Application>> EXTENSION_TYPE =
            new TypeOf<NamedDomainObjectContainer<Application>>() {};

    @Nullable
    private NamedDomainObjectContainer<Application> applications;

    @Override
    public void apply(@Nonnull Project project) {
        if (this.applications != null) {
            throw new IllegalStateException("Plugin instance was already applied to a project");
        }

        // Finalize applications (based on: project.afterEvaluate in DistributionPlugin.apply)
        // - We must register our afterEvaluate callback before the plugins we rely on have a chance to register theirs:
        //   this way, ours will run first, allowing theirs to see the finalized configuration
        // - We use configureEach to avoid forcing creation of any registered objects (configuration avoidance)
        project.afterEvaluate(preparedProject -> {
            preparedProject.getExtensions().getByType(EXTENSION_TYPE).configureEach(Application::finalizeProperties);
            preparedProject.getTasks().withType(ApplicationJar.class).configureEach(ApplicationJar::finalizeProperties);
        });

        // Apply plugins we depend on
        project.getPluginManager().apply(JavaPlugin.class);
        project.getPluginManager().apply(DistributionPlugin.class);

        // Make sure to re-apply ApplicationJar archive file defaults (see the docs of the method we register)
        project.getTasks().withType(ApplicationJar.class).configureEach(ApplicationJar::configureArchiveDefaults);

        // Set up application container
        applications = project.getObjects().domainObjectContainer(Application.class,
                name -> project.getObjects().newInstance(Application.class, project, name));
        project.getExtensions().add(EXTENSION_TYPE, EXTENSION_NAME, applications);

        // Set up main application
        applications.create(Application.MAIN_APPLICATION_NAME, mainApplication -> mainApplication.fromSourceSet(
                Utils.sourceSets(project).named(SourceSet.MAIN_SOURCE_SET_NAME)));
    }

    /**
     * <p>This method allows type-safe programmatic access to the {@link Application} container used by this plugin.</p>
     * <p>Usage: {@code project.getPlugins().getPlugin(ApplicationPlugin.class).getApplications()}.</p>
     *
     * @return The {@link Application} container used by this plugin.
     * @see #EXTENSION_NAME
     * @see #EXTENSION_TYPE
     */
    @Nonnull
    public NamedDomainObjectContainer<Application> getApplications() {
        if (this.applications == null) {
            throw new IllegalStateException("Plugin instance was not applied to a project");
        }
        return applications;
    }
}
