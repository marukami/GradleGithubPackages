@file:Suppress("UnstableApiUsage")

package dev.tilbrook.github.packages

import org.gradle.api.Plugin
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.invocation.Gradle
import org.gradle.util.GradleVersion
import java.io.File
import java.util.Properties

open class GithubPackagesPlugin : Plugin<Gradle> {

  private val properties by lazy {
    val env = System.getenv()
    val home = env["GRADLE_HOME"]
    Properties().apply {
      val file = File("$home/gradle.properties")
      if (file.exists()) {
        load(file.reader())
      }
    }
  }

  private fun RepositoryHandler.githubMaven(
    ghUsername: String,
    ghPassword: String,
  ) {
    val env = System.getenv()

    maven { repository ->
      with(repository) {
        name = run {
          env["GITHUB_PACKAGES_REPO_NAME"]
            ?: properties.getStringOrNull("github.repo")
            ?: "GitHubPackages"
        }
        setUrl(
          env["GITHUB_PACKAGES_URL"]
            ?: properties.getStringOrNull("github.url")
            ?: error("No GITHUB_PACKAGES_URL or gradle.properties github.url key was found")
        )
        credentials { auth ->
          auth.username = ghUsername
          auth.password = ghPassword
        }
      }
    }
  }

  private fun Properties.getStringOrNull(key: String): String? =
    get(key) as String?

  override fun apply(target: Gradle) {
    val env = System.getenv()

    val username =
      requireNotNull(env["GITHUB_PACKAGES_USER"] ?: properties.getStringOrNull("github.username")) {
        "Github Packages username not define. Please define an environment variable " +
            "GITHUB_PACKAGES_USER, or a gradle.property github.username key was found"
      }
    val password =
      requireNotNull(
        env["GITHUB_PACKAGES_PASSWORD"] ?: properties.getStringOrNull("github.password")
      ) {
        "Github Packages password not define. Please define an environment variable " +
            "GITHUB_PACKAGES_PASSWORD, or a gradle.property github.password key was found"
      }
    val gradleVersion = target.gradleVersion.let(GradleVersion::version)
    val isAllProjectConfig =
      (env["GITHUB_PACKAGES_ALL_PROJECTS"] ?: properties["github.useAllProjects"])
        .let { it == "true" }

    if (isAllProjectConfig || gradleVersion < GradleVersion.version("6.8")) {
      target.rootProject { project ->
        project.logger.info("GithubPackagesPlugin: isAllProjectConfig=$isAllProjectConfig")
        project.logger.info("GithubPackagesPlugin: Using allprojects configuration")
      }
      target.allprojects { project ->
        project.repositories.githubMaven(username, password)
      }
    } else {
      target.rootProject { project ->
        project.logger.info("GithubPackagesPlugin: Using dependencyResolutionManagement configuration")
      }
      target.settingsEvaluated { settings ->
        settings.pluginManagement { manager ->
          manager.repositories.githubMaven(username, password)
        }
        settings.dependencyResolutionManagement { manager ->
          manager.repositories.githubMaven(username, password)
        }
      }
    }
  }
}
