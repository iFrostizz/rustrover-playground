plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intellijPlatform)
}

group = "com.example"
version = "1.0.0-SNAPSHOT"

// Set the JVM language level used to build the project.
kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        rustRover("2026.1")

        bundledPlugins("com.jetbrains.rust")

//        plugins("org.jetbrains.kotlin:2.3.20")

        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        pluginVerifier()
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }

        changeNotes = """
            Initial version
        """.trimIndent()
    }
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }
}
