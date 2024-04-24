import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.util.jar.JarFile

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.shadowjar)
  `java-gradle-plugin`
  `maven-publish`
}

group = "dev.tilbrook"
version = "0.1.0"

kotlin {
  jvmToolchain(11)
}

val shadowImplementation: Configuration by configurations.creating
configurations["compileOnly"].extendsFrom(shadowImplementation)
configurations["testImplementation"].extendsFrom(shadowImplementation)

tasks.withType<PluginUnderTestMetadata>().configureEach {
  pluginClasspath.from(shadowImplementation)
}

val shadowJarTask = tasks.named<ShadowJar>("shadowJar") {
  archiveClassifier = ""
  configurations = listOf(shadowImplementation)
  val projectGroup = project.group
  doFirst {
    configurations.forEach { configuration ->
      configuration.files.forEach { jar ->
        JarFile(jar).use { jf ->
          jf.entries().iterator().forEach { entry ->
            if (entry.name.endsWith(".class") && entry.name != "module-info.class") {
              val packageName =
                entry
                  .name
                  .substring(0..entry.name.lastIndexOf('/'))
                  .replace('/', '.')
              relocate(packageName, "${projectGroup}.shadow.$packageName")
            }
          }
        }
      }
    }
  }
}

configurations.archives.get().artifacts.clear()
configurations {
  artifacts {
    runtimeElements(shadowJarTask)
    apiElements(shadowJarTask)
    archives(tasks.shadowJar)
  }
}

// Add the shadow JAR to the runtime consumable configuration
configurations.apiElements.get().artifacts.clear()
configurations.apiElements.get().outgoing.artifact(tasks.shadowJar)
configurations.runtimeElements.get().outgoing.artifacts.clear()
configurations.runtimeElements.get().outgoing.artifact(tasks.shadowJar)

val shadowJarConfig = configurations.create("shadowJar") {
  isCanBeConsumed = true
  isCanBeResolved = false
}

artifacts {
  add(shadowJarConfig.name, tasks.shadowJar)
}

// Disabling default jar task as it is overridden by shadowJar
tasks.named("jar").configure {
  enabled = false
}

// Our integration tests need a fully compiled jar
tasks.withType<Test> {
  dependsOn("assemble")
  dependsOn("shadowJar")
  javaLauncher.set(javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(11))
  })
}

gradlePlugin {
  plugins {
    create("GithubPackagesPlugin") {
      id = "dev.tilbrook.github.packages"
      implementationClass = "dev.tilbrook.github.packages.GithubPackagesPlugin"
    }
  }
}


dependencies {
  compileOnly(gradleApi())
  testImplementation(gradleTestKit())
  testImplementation(libs.junit)
}