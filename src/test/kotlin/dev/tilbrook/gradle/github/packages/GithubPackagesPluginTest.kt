package dev.tilbrook.gradle.github.packages

import org.gradle.internal.impldep.com.google.common.io.Files
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GradleVersion
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

const val GITHUB_PACKAGES_MAVEN_NAME = "dev.tilbrook/gradleGithubPackages"

class GithubPackagesPluginTest {

  @get:Rule
  val testProjectDir = TemporaryFolder()
  private lateinit var settingsFile: File
  private lateinit var buildFile: File
  private lateinit var initFile: File
  private lateinit var rootGradlePropertyFile: File
  private lateinit var gradleHome: File

  private val GITHUB_PACKAGES_URL
    get() =
      "file:${testProjectDir.root}/$GITHUB_PACKAGES_MAVEN_NAME"

  @Before
  fun setup() {
    settingsFile = File(testProjectDir.root, "settings.gradle.kts").also { it.createNewFile() }
    buildFile = File(testProjectDir.root, "build.gradle.kts").also { it.createNewFile() }
    initFile = File(testProjectDir.root, "init.gradle.kts").also { it.createNewFile() }
    gradleHome = File(testProjectDir.root, ".gradle").also { it.mkdir() }
    rootGradlePropertyFile = File(gradleHome, "gradle.properties").also { it.createNewFile() }
    val libs = File(testProjectDir.root, "libs").also { it.mkdir() }
    File("./build/libs").listFiles()!!.forEach {
      Files.copy(it, File(libs, it.name))
    }
    setupBuildFiles()
  }

  @Test
  fun `dependencyResolutionManagement is used when using gradle greater than 7_0`() {
    val version = GradleVersion.version("7.0")
    buildFile.writeText(dependencyResolutionManagementSetup())
    val runner = runGradle(version).build()

    assert(runner.output.contains("SUCCESS"))
    assert(runner.output.contains("url: $GITHUB_PACKAGES_URL"))
    assert(runner.output.contains("GithubPackagesPlugin: Using dependencyResolutionManagement configuration"))
  }

  @Test
  @Ignore("Can't run on M1 mac. Remove once Linux test runner is setup")
  fun `allprojects is used when using gradle lesser than 6_8`() {
    val version = GradleVersion.version("7.0")
    buildFile.writeText(allProjectsSetup())
    val runner = runGradle(version).build()

    assert(runner.output.contains("SUCCESS"))
    assert(runner.output.contains("url: $GITHUB_PACKAGES_URL"))
    assert(runner.output.contains("GithubPackagesPlugin: Using allprojects configuration"))
  }

  @Test
  fun `allprojects is used when GRADLE_HOME gradle_properties has isAllProjectConfig`() {
    val version = GradleVersion.version("7.0")
    buildFile.writeText(allProjectsSetup())
    rootGradlePropertyFile.appendText("\ngithub.useAllProjects=true")
    val runner = runGradle(version).build()

    println(runner.output)
    assert(runner.output.contains("SUCCESS"))
    assert(runner.output.contains("url: $GITHUB_PACKAGES_URL"))
    assert(runner.output.contains("GithubPackagesPlugin: Using allprojects configuration"))
  }

  private fun runGradle(gradleVersion: GradleVersion): GradleRunner =
    GradleRunner.create()
      .withGradleVersion(gradleVersion.version)
      .withPluginClasspath()
      .withProjectDir(testProjectDir.root)
      .withArguments(
        "--info",
        "--stacktrace",
        "--init-script", "./init.gradle.kts",
        "listRepos",
      )
      .withEnvironment(
        mapOf(
          "GRADLE_HOME" to testProjectDir.root.path + "/.gradle",
          "GITHUB_PACKAGES_URL" to GITHUB_PACKAGES_URL,
        )
      )

  private fun setupBuildFiles() {
    rootGradlePropertyFile.writeText(
      """
      github.url=$GITHUB_PACKAGES_URL
      github.username=gh-username
      github.password=gh-password
    """.trimIndent()
    )
    initFile.writeText(
      """
        initscript {
          repositories {
            mavenCentral()
            gradlePluginPortal()
          }
          dependencies {
            // This feels dirty, but it works 
            classpath(fileTree("libs") { include("*.jar") })
          }
        }
        apply<dev.tilbrook.github.packages.GithubPackagesPlugin>()
      """.trimIndent()
    )
  }

  private fun allProjectsSetup() = """
      tasks.register("listRepos") {
        group = "Repos"
        doLast {
          project.repositories.joinToString("\n") {
              (it as MavenArtifactRepository)
              "Name: ${'$'}{it.name}; url: ${'$'}{it.url}" 
            }
            .also(::println)
        }
      }
    """.trimIndent()

  private fun dependencyResolutionManagementSetup() = """
    tasks.register("listRepos") {
        group = "Repos"
        doLast {
          @Suppress("UnstableApiUsage")
          (gradle as org.gradle.api.internal.GradleInternal)
            .settings
            .dependencyResolutionManagement
            .repositories 
            .joinToString("\n") {
              (it as MavenArtifactRepository)
              "Name: ${'$'}{it.name}; url: ${'$'}{it.url}" 
            }
            .also(::println)
        }
      }
  """.trimIndent()
}