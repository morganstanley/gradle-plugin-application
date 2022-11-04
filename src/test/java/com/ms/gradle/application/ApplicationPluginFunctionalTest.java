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
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.Assertions;
import org.assertj.core.util.Preconditions;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.gradle.util.GradleVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Functional tests for {@link ApplicationPlugin}.
 */
class ApplicationPluginFunctionalTest {

    private static final String TEST_NAME = ApplicationPluginFunctionalTest.class.getSimpleName();
    private static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";

    // See: `build.gradle.kts` and https://gradle.org/releases/
    private static final String GRADLE_VERSION_CURRENT = GradleVersion.current().getVersion();
    private static final String[] GRADLE_VERSIONS_SUPPORTED = {
            GRADLE_VERSION_CURRENT, "7.4.2", "7.3.3", "7.2", "7.1.1", "7.0.2",
            "6.9.3", "6.8.3", "6.7.1", "6.6.1", "6.5.1", "6.4.1", "6.3", "6.2.2", "6.1.1", "6.0.1"};

    @Nonnull
    private static Stream<String> gradleVersions() {
        return Arrays.stream(GRADLE_VERSIONS_SUPPORTED);
    }

    @Nonnull
    private final Path testKitDir;

    ApplicationPluginFunctionalTest() throws IOException {
        testKitDir = Files.createDirectories(Paths.get("build", "testKit").toAbsolutePath());
    }

    @Nonnull
    @SuppressFBWarnings(value = "NP_NONNULL_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR",
            justification = "@TempDir is not supported on constructor parameters")
    private Path projectDir;

    @BeforeEach
    void beforeEach(@TempDir Path tempDir) throws IOException {
        projectDir = Files.createDirectory(tempDir.resolve(TEST_NAME).toAbsolutePath());
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    void testProjectUsingGroovyDsl(@Nonnull String gradleVersion) throws IOException {
        prepareProjectDir("build.gradle");
        executeAndValidate(gradleVersion);
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    void testProjectUsingKotlinDsl(@Nonnull String gradleVersion) throws IOException {
        prepareProjectDir("build.gradle.kts");
        executeAndValidate(gradleVersion);
    }

    private void prepareProjectDir(@Nonnull String buildFileName) throws IOException {
        Path testDataDir = Paths.get("test-data", TEST_NAME).toAbsolutePath();
        FileUtils.copyDirectory(testDataDir.toFile(), projectDir.toFile(), FileFilterUtils.or(
                FileFilterUtils.directoryFileFilter(),
                FileFilterUtils.notFileFilter(FileFilterUtils.prefixFileFilter("build.gradle")),
                FileFilterUtils.nameFileFilter(buildFileName)));
        assertIsDirectoryContainingOnly(projectDir,
                projectDir.resolve("repo"), projectDir.resolve("src"),
                projectDir.resolve(buildFileName), projectDir.resolve("README.md"));

        String jacocoAgentJvmArg = Utils.nonNull(System.getProperty("testEnv.jacocoAgentJvmArg"), "jacocoAgentJvmArg");
        try (Writer propertiesWriter = new OutputStreamWriter(
                Files.newOutputStream(projectDir.resolve("gradle.properties")),
                StandardCharsets.UTF_8)) {
            Properties properties = new Properties();
            properties.put("org.gradle.jvmargs", jacocoAgentJvmArg);
            properties.store(propertiesWriter, "Properties for Gradle TestKit builds");
        }
    }

    @Nonnull
    private GradleRunner makeGradleRunner(@Nonnull String gradleVersion) {
        GradleRunner runner = GradleRunner.create()
                .withTestKitDir(testKitDir.toFile())
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath();
        return GRADLE_VERSION_CURRENT.equals(gradleVersion) ? runner : runner.withGradleVersion(gradleVersion);
    }

    private void executeAndValidate(@Nonnull String gradleVersion) throws IOException {
        BuildResult result = makeGradleRunner(gradleVersion)
                .withArguments("emptyJar",
                        "installDist",
                        "installSimpleDist",
                        "installWithoutRawJarDist",
                        "installWithoutDependenciesDist",
                        "installComplexDist",
                        "installManualDist",
                        "installFullyManualDist")
                .build();

        validateTaskOutcome(result, ":applicationJar", TaskOutcome.SUCCESS);
        validateTaskOutcome(result, ":simpleApplicationJar", TaskOutcome.SUCCESS);
        validateTaskOutcome(result, ":withoutRawJarApplicationJar", TaskOutcome.SUCCESS);
        validateTaskOutcome(result, ":withoutDependenciesApplicationJar", TaskOutcome.SUCCESS);
        validateTaskOutcome(result, ":complexApplicationJar", TaskOutcome.SUCCESS);
        validateTaskOutcome(result, ":manualApplicationJar", TaskOutcome.SUCCESS);

        validateBuildOutput(TEST_NAME, TEST_NAME, "lib");
        validateBuildOutput(TEST_NAME, TEST_NAME + "-simple", "lib");
        validateBuildOutput("empty", TEST_NAME + "-withoutRawJar", "lib");
        validateBuildOutput(TEST_NAME, TEST_NAME + "-withoutDependencies", null);
        validateBuildOutput(TEST_NAME, "appsComplex", "complexJar", "complexApp", "complexDeps", true);
        validateBuildOutput(TEST_NAME, "application", "manualJar", "manualDist", "lib", true);
        validateBuildOutput(TEST_NAME, "application", "manualJar", "fullyManual/content/app", "lib", false);

        executeAndValidateFailIfPropertyMissing(gradleVersion, "dependencyDirectoryName");
        executeAndValidateFailIfPropertyEmpty(gradleVersion, "dependencyDirectoryName");
        executeAndValidateFailIfPropertyMissing(gradleVersion, "mainClass");
        executeAndValidateFailIfPropertyEmpty(gradleVersion, "mainClass");
    }

    private void executeAndValidateFailIfPropertyMissing(@Nonnull String gradleVersion, @Nonnull String propertyName) {
        String applicationName = "missing" + StringUtils.capitalize(propertyName);
        String errorMessage = executeAndValidateFail(gradleVersion, applicationName);
        Assertions.assertThat(errorMessage).containsPattern("(^|\\s)" +
                Pattern.quote("* What went wrong:") + "\\s+" +
                Pattern.quote("A problem was found with the configuration of " +
                        "task ':" + applicationName + "ApplicationJar' (type 'ApplicationJar.Bound').") + "\\s+" +
                "(" + Pattern.quote("> No value has been specified for property '" + propertyName + "'.") +
                "|" + Pattern.quote("- Type 'com.ms.gradle.application.ApplicationJar.Bound' " +
                        "property '" + propertyName + "' doesn't have a configured value.") +
                "|" + Pattern.quote("- In plugin 'com.ms.gradle.application' " +
                        "type 'com.ms.gradle.application.ApplicationJar.Bound' " +
                        "property '" + propertyName + "' doesn't have a configured value.") +
                ")(\\s|$)");
    }

    private void executeAndValidateFailIfPropertyEmpty(@Nonnull String gradleVersion, @Nonnull String propertyName) {
        String applicationName = "empty" + StringUtils.capitalize(propertyName);
        String errorMessage = executeAndValidateFail(gradleVersion, applicationName);
        Assertions.assertThat(errorMessage).containsPattern("(^|\\s)" +
                Pattern.quote("* What went wrong:") + "\\s+" +
                Pattern.quote("Execution failed for task ':" + applicationName + "ApplicationJar'.") + "\\s+" +
                "(> [^\\n]*\\s+)*" +
                Pattern.quote("> " + propertyName + " must not be empty") + "(\\s|$)");
    }

    @Nonnull
    private String executeAndValidateFail(@Nonnull String gradleVersion, @Nonnull String applicationName) {
        String installDistTaskName = "install" + StringUtils.capitalize(applicationName) + "Dist";
        String applicationJarTaskName =
                ":" + applicationName + StringUtils.capitalize(Application.MAIN_APPLICATION_JAR_TASK_NAME);

        StringWriter stdErrorWriter = new StringWriter();
        BuildResult result = makeGradleRunner(gradleVersion)
                .withArguments(installDistTaskName)
                .forwardStdError(stdErrorWriter)
                .buildAndFail();
        validateTaskOutcome(result, applicationJarTaskName, TaskOutcome.FAILED);
        return stdErrorWriter.toString();
    }

    private void validateTaskOutcome(
            @Nonnull BuildResult result, @Nonnull String taskPath, @Nonnull TaskOutcome expectedOutcome) {
        BuildTask task = result.task(taskPath);
        Assertions.assertThat(task).isNotNull();
        Assertions.assertThat(task.getOutcome()).isEqualTo(expectedOutcome);
    }

    private void validateBuildOutput(
            @Nonnull String rawJarName, @Nonnull String appJarAndDistName, @Nullable String depDir)
            throws IOException {
        validateBuildOutput(rawJarName, "application", appJarAndDistName, appJarAndDistName, depDir, false);
    }

    private void validateBuildOutput(
            @Nonnull String rawJarName, @Nonnull String appDir, @Nonnull String appJarName, @Nonnull String distName,
            @Nullable String depDir, boolean hasReadme)
            throws IOException {
        Path buildDir = projectDir.resolve("build");
        Path rawJar = buildDir.resolve("libs").resolve(rawJarName + "-1.0.jar");
        Path appJar = buildDir.resolve(appDir).resolve(appJarName + "-1.0.jar");
        Path distJar = buildDir.resolve("install").resolve(distName).resolve(appJarName + "-1.0.jar");
        validateApplicationJar(rawJar, appJar, depDir);
        validateDistribution(appJar, distJar, depDir, hasReadme);
    }

    private void validateApplicationJar(@Nonnull Path rawJar, @Nonnull Path appJar, @Nullable String depDir)
            throws IOException {
        Assertions.assertThat(rawJar).isRegularFile();
        Assertions.assertThat(appJar).isRegularFile();
        try (ZipFile rawZip = new ZipFile(rawJar.toFile());
                ZipFile appZip = new ZipFile(appJar.toFile())) {
            Map<String, ZipEntry> rawZipEntries = zipEntriesToMap(rawZip);
            Map<String, ZipEntry> appZipEntries = zipEntriesToMap(appZip);
            Assertions.assertThat(appZipEntries.keySet()).containsExactlyElementsOf(rawZipEntries.keySet());

            for (Map.Entry<String, ZipEntry> rawZipEntry : rawZipEntries.entrySet()) {
                ZipEntry rawEntry = rawZipEntry.getValue();
                ZipEntry appEntry = appZipEntries.get(rawZipEntry.getKey());
                Assertions.assertThat(appEntry.isDirectory()).isEqualTo(rawEntry.isDirectory());
                if (!appEntry.isDirectory() && !MANIFEST_PATH.equals(appEntry.getName())) {
                    Assertions.assertThat(appZip.getInputStream(appEntry))
                            .hasSameContentAs(rawZip.getInputStream(rawEntry));
                }
            }

            ZipEntry appManifestEntry = appZipEntries.get(MANIFEST_PATH);
            Assertions.assertThat(appManifestEntry).isNotNull();
            Assertions.assertThat(appManifestEntry.isDirectory()).isFalse();

            // The created InputStream will be closed implicitly when zipFile gets closed
            Manifest appManifest = new Manifest(appZip.getInputStream(appManifestEntry));
            Assertions.assertThat(appManifest.getEntries()).isEmpty();

            String classpath = buildClasspath(depDir,
                    "com.ms.test-console-1.2.3.jar",
                    "com.ms.test/datetime-current-4.5.jar",
                    "com.ms.test/datetime-format-4.5.jar");
            if (rawJar.endsWith("empty-1.0.jar")) {
                Assertions.assertThat(appManifest.getMainAttributes()).containsOnly(
                        Assertions.entry(Attributes.Name.MANIFEST_VERSION, "1.0"),
                        Assertions.entry(Attributes.Name.CLASS_PATH, classpath),
                        Assertions.entry(Attributes.Name.MAIN_CLASS, "com.ms.test.app.PrintTimestamp"));
            } else {
                Assertions.assertThat(appManifest.getMainAttributes()).containsOnly(
                        Assertions.entry(Attributes.Name.MANIFEST_VERSION, "1.0"),
                        Assertions.entry(Attributes.Name.IMPLEMENTATION_TITLE, "Application plugin functional test"),
                        Assertions.entry(Attributes.Name.IMPLEMENTATION_VERSION, "1.0.b42"),
                        Assertions.entry(Attributes.Name.CLASS_PATH, classpath),
                        Assertions.entry(Attributes.Name.MAIN_CLASS, "com.ms.test.app.PrintTimestamp"));
            }
        }
    }

    private void validateDistribution(
            @Nonnull Path appJar, @Nonnull Path distJar, @Nullable String depDir, boolean hasReadme) {
        Assertions.assertThat(distJar).isRegularFile().hasSameBinaryContentAs(appJar);

        Path distDir = distJar.getParent();
        if (depDir != null) {
            Path distDepDir = distDir.resolve(depDir);
            Path distDepConsoleJar = distDepDir.resolve("com.ms.test-console-1.2.3.jar");
            Path distDepComMsTestDir = distDepDir.resolve("com.ms.test");
            Path distDepDatetimeCurrentJar = distDepComMsTestDir.resolve("datetime-current-4.5.jar");
            Path distDepDatetimeFormatJar = distDepComMsTestDir.resolve("datetime-format-4.5.jar");

            Path repoDir = projectDir.resolve("repo");
            Path repoConsoleJar = repoDir.resolve("local/com.ms.test-console-1.2.3.jar");
            Path repoComMsTestDir = repoDir.resolve("maven/com/ms/test");
            Path repoDatetimeCurrentJar = repoComMsTestDir.resolve("datetime-current/4.5/datetime-current-4.5.jar");
            Path repoDatetimeFormatJar = repoComMsTestDir.resolve("datetime-format/4.5/datetime-format-4.5.jar");

            if (hasReadme) {
                assertIsDirectoryContainingOnly(distDir, distJar, distDepDir, distDir.resolve("README.md"));
            } else {
                assertIsDirectoryContainingOnly(distDir, distJar, distDepDir);
            }
            assertIsDirectoryContainingOnly(distDepDir, distDepConsoleJar, distDepComMsTestDir);
            assertIsDirectoryContainingOnly(distDepComMsTestDir, distDepDatetimeCurrentJar, distDepDatetimeFormatJar);

            Assertions.assertThat(distDepConsoleJar).isRegularFile().hasSameBinaryContentAs(repoConsoleJar);
            Assertions.assertThat(distDepDatetimeCurrentJar).isRegularFile()
                    .hasSameBinaryContentAs(repoDatetimeCurrentJar);
            Assertions.assertThat(distDepDatetimeFormatJar).isRegularFile()
                    .hasSameBinaryContentAs(repoDatetimeFormatJar);
        } else {
            if (hasReadme) {
                assertIsDirectoryContainingOnly(distDir, distJar, distDir.resolve("README.md"));
            } else {
                assertIsDirectoryContainingOnly(distDir, distJar);
            }
        }
    }

    private void assertIsDirectoryContainingOnly(@Nonnull Path path, @Nonnull Path... expectedPaths) {
        Assertions.assertThat(path).isDirectory();
        Set<String> expectedNames = Arrays.stream(expectedPaths)
                .peek(expectedPath -> Preconditions.checkArgument(
                        path.equals(expectedPath.getParent()), "Not a child: %s", expectedPath))
                .map(Path::getFileName)
                .map(String::valueOf)
                .collect(Collectors.toSet());
        Assertions.assertThat(path.toFile().list()).containsExactlyInAnyOrderElementsOf(expectedNames);
    }

    @Nonnull
    private Map<String, ZipEntry> zipEntriesToMap(@Nonnull ZipFile zipFile) {
        return zipFile.stream().collect(Collectors.toMap(ZipEntry::getName, Function.identity()));
    }

    @Nonnull
    private String buildClasspath(@Nullable String depDir, @Nonnull String... depPaths) {
        return depDir == null ? "" :
                Arrays.stream(depPaths).map(depPath -> depDir + '/' + depPath).collect(Collectors.joining(" "));
    }
}
