package com.novoda.gradle.release


import com.google.common.truth.TruthJUnit
import com.novoda.gradle.test.GeneratedPom
import com.novoda.gradle.test.GradleBuildResult
import com.novoda.gradle.test.GradleScriptTemplates
import com.novoda.gradle.test.TestProject
import com.novoda.gradle.truth.GradleTruth
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.FileTree
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.GradleVersion
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import static com.google.common.truth.Truth.assertThat

@RunWith(Parameterized.class)
class ReleasePluginTest {

    private static final GradleVersion GRADLE_4_1 = GradleVersion.version('4.1')
    private static final String BASE_UPLOAD_PATH = 'https://api.bintray.com/content/novoda/maven/test/1.0/com/novoda/test/1.0/test-1.0'
    private static final String SOURCES_UPLOAD_PATH = "$BASE_UPLOAD_PATH-sources.jar"
    private static final String JAVADOC_UPLOAD_PATH = "$BASE_UPLOAD_PATH-javadoc.jar"
    private static final String POM_UPLOAD_PATH = "${BASE_UPLOAD_PATH}.pom"
    private static final String JAR_UPLOAD_PATH = "${BASE_UPLOAD_PATH}.jar"
    private static final String AAR_UPLOAD_PATH = "${BASE_UPLOAD_PATH}.aar"

    private static final Map<String, GradleBuildResult> RESULTS = [:]

    @Parameterized.Parameters(name = "{0}")
    static Collection<BuildConfiguration> configurations() {
        return [
                BuildConfiguration.forAndroid('4.0', false),
                BuildConfiguration.forAndroid('4.1', true),
                BuildConfiguration.forAndroid('4.2', true),
                BuildConfiguration.forAndroid('4.3', true),
                BuildConfiguration.forAndroid('4.4', true),
                BuildConfiguration.forAndroid('4.5', false),
                BuildConfiguration.forJava('4.0', true),
                BuildConfiguration.forJava('4.1', true),
                BuildConfiguration.forJava('4.2', true),
                BuildConfiguration.forJava('4.3', true),
                BuildConfiguration.forJava('4.4', true),
                BuildConfiguration.forJava('4.5', true)
        ]
    }

    private final BuildConfiguration configuration

    ReleasePluginTest(BuildConfiguration configuration) {
        this.configuration = configuration
    }

    @Before
    void setUp() throws Exception {
        if (RESULTS[configuration.key] == null) {
            configuration.testProject.init("${this.class.canonicalName}/${configuration}/test")
            RESULTS[configuration.key] = configuration.testProject.execute('clean', 'build', 'bintrayUpload', '-PbintrayKey=key', '-PbintrayUser=user', '--stacktrace')
        }
    }

    @Test
    void shouldBuildLibrary() {
        skipTestWhen(configuration.expectedBuildFailure)

        GradleTruth.assertThat(result.task(':build')).hasOutcome(TaskOutcome.SUCCESS)
    }

    @Test
    void shouldGeneratePomFile() {
        skipTestWhen(configuration.expectedBuildFailure)

        GradleTruth.assertThat(result.task(configuration.generatePomTaskName)).hasOutcome(TaskOutcome.SUCCESS)
    }

    @Test
    void shouldProvideCompileScopeDependenciesInGeneratedPomFile() {
        skipTestWhen(configuration.expectedBuildFailure)

        assertThat(generatedPom.dependency('rxjava').scope).isEqualTo('compile')
    }

    @Test
    void shouldProvideRuntimeScopeDependenciesInGeneratedPomFile() {
        skipTestWhen(configuration.expectedBuildFailure)
        skipTestWhen(configuration.gradleVersion < GRADLE_4_1)

        assertThat(generatedPom.dependency('okio').scope).isEqualTo('runtime')
    }

    @Test
    void shouldGenerateJavadocs() {
        skipTestWhen(configuration.expectedBuildFailure)

        GradleTruth.assertThat(result.task(configuration.generateJavadocsTaskName)).hasOutcome(TaskOutcome.SUCCESS)
    }

    @Test
    void shouldPackageAllGeneratedJavadocs() {
        skipTestWhen(configuration.expectedBuildFailure)

        GradleTruth.assertThat(result.task(configuration.packageJavadocsTaskName)).hasOutcome(TaskOutcome.SUCCESS)

        ConfigurableFileTree generatedFiles = testProject.fileTree('build/docs/javadoc')
        List<String> includePatterns = generatedFiles.collect { '**' + it.path - generatedFiles.dir.path }
        FileTree jarfile = testProject.zipTree('build/libs/test-javadoc.jar')
        assertThat(jarfile.matching { include includePatterns }).hasSize(includePatterns.size())
    }

    @Test
    void shouldPackageAllSources() {
        skipTestWhen(configuration.expectedBuildFailure)

        GradleTruth.assertThat(result.task(configuration.packageSourcesTaskName)).hasOutcome(TaskOutcome.SUCCESS)

        ConfigurableFileTree sourceFiles = testProject.fileTree('src/main/java')
        List<String> includePatterns = sourceFiles.collect { '**' + it.path - sourceFiles.dir.path }
        FileTree jarfile = testProject.zipTree('build/libs/test-sources.jar')
        assertThat(jarfile.matching { include includePatterns }).hasSize(includePatterns.size())
    }

    @Test
    void shouldPublishToMavenLocal() {
        skipTestWhen(configuration.expectedBuildFailure)

        GradleTruth.assertThat(result.task(configuration.publishToMavenLocalTaskName)).hasOutcome(TaskOutcome.SUCCESS)
    }

    @Test
    void shouldRunUploadTask() {
        skipTestWhen(configuration.expectedBuildFailure)

        GradleTruth.assertThat(result.task(":bintrayUpload")).hasOutcome(TaskOutcome.SUCCESS)
    }

    @Test
    void shouldUploadSourcesJar() {
        skipTestWhen(configuration.expectedBuildFailure)

        assertThat(result.output).contains(SOURCES_UPLOAD_PATH)
    }

    @Test
    void shouldUploadJavadocJar() {
        skipTestWhen(configuration.expectedBuildFailure)

        assertThat(result.output).contains(JAVADOC_UPLOAD_PATH)
    }

    @Test
    void shouldUploadPomFile() {
        skipTestWhen(configuration.expectedBuildFailure)

        assertThat(result.output).contains(POM_UPLOAD_PATH)
    }

    @Test
    void shouldUploadLibraryArtifact() {
        skipTestWhen(configuration.expectedBuildFailure)

        assertThat(result.output).contains(configuration.libraryUploadPath)
    }

    @Test
    void shouldMatchBuildOutcome() {
        assertThat(result.success).isEqualTo(configuration.expectedBuildSuccess)
    }

    private GradleBuildResult getResult() {
        return RESULTS[configuration.key]
    }

    private TestProject getTestProject() {
        return configuration.testProject
    }

    private GeneratedPom getGeneratedPom() {
        File pomFile = new File(testProject.projectDir, "/build/publications/$configuration.publicationName/pom-default.xml")
        return GeneratedPom.from(pomFile)
    }

    private static void skipTestWhen(boolean condition) {
        TruthJUnit.assume().that(condition).isFalse()
    }

    private static class BuildConfiguration {
        final GradleVersion gradleVersion
        final TestProject testProject
        final boolean expectedBuildSuccess

        static BuildConfiguration forAndroid(String gradleVersion, boolean expectedBuildSuccess) {
            def additionalRunnerConfig = { GradleRunner runner -> runner.withGradleVersion(gradleVersion) }
            def buildGradleVersion = GradleVersion.version(gradleVersion)
            def buildScript = addDependenciesTo(GradleScriptTemplates.forAndroidProject(), buildGradleVersion)
            def testProject = TestProject.newAndroidProject(buildScript, additionalRunnerConfig)
            return new BuildConfiguration(buildGradleVersion, testProject, expectedBuildSuccess)
        }

        static BuildConfiguration forJava(String gradleVersion, boolean expectedBuildSuccess) {
            def additionalRunnerConfig = { GradleRunner runner -> runner.withGradleVersion(gradleVersion) }
            def buildGradleVersion = GradleVersion.version(gradleVersion)
            def buildScript = addDependenciesTo(GradleScriptTemplates.forJavaProject(), buildGradleVersion)
            def testProject = TestProject.newJavaProject(buildScript, additionalRunnerConfig)
            return new BuildConfiguration(buildGradleVersion, testProject, expectedBuildSuccess)
        }

        private static String addDependenciesTo(String buildscript, GradleVersion gradleVersion) {
            String compileScopeDependency = "${gradleVersion < GRADLE_4_1 ? 'compile' : 'api'} 'io.reactivex.rxjava2:rxjava:2.2.0'"
            String runtimeScopeDependency = gradleVersion < GRADLE_4_1 ? '' : 'implementation \'com.squareup.okio:okio:2.1.0\''
            return """
                $buildscript

                dependencies {
                    $compileScopeDependency
                    $runtimeScopeDependency
                }
                """.stripIndent()
        }

        private BuildConfiguration(GradleVersion buildGradleVersion, TestProject testProject, boolean expectedBuildSuccess) {
            this.gradleVersion = buildGradleVersion
            this.testProject = testProject
            this.expectedBuildSuccess = expectedBuildSuccess
        }

        @Override
        String toString() {
            return "${testProject.projectType.capitalize()} project with $gradleVersion"
        }

        String getKey() {
            return toString()
        }

        private boolean isAndroid() {
            return testProject.projectType == 'android'
        }

        private String getPublicationName() {
            return isAndroid() ? 'release' : 'maven'
        }

        String getGeneratePomTaskName() {
            return ":generatePomFileFor${publicationName.capitalize()}Publication"
        }

        String getGenerateJavadocsTaskName() {
            return isAndroid() ? ":javadoc${publicationName.capitalize()}" : ':javadoc'
        }

        String getPackageJavadocsTaskName() {
            return ":genereateJavadocsJarFor${publicationName.capitalize()}Publication"
        }

        String getPackageSourcesTaskName() {
            return ":genereateSourcesJarFor${publicationName.capitalize()}Publication"
        }

        String getPublishToMavenLocalTaskName() {
            return ":publish${publicationName.capitalize()}PublicationToMavenLocal"
        }

        String getLibraryUploadPath() {
            return isAndroid() ? AAR_UPLOAD_PATH : JAR_UPLOAD_PATH
        }

        boolean getExpectedBuildFailure() {
            return !expectedBuildSuccess
        }
    }
}