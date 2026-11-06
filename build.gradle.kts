import io.papermc.hangarpublishplugin.model.Platforms

plugins {
    java
    alias(libs.plugins.prun)
    alias(libs.plugins.paperweight)
    alias(libs.plugins.hangar)
}

group = rootProject.group
version = rootProject.version

repositories {
    mavenCentral()
    maven(url = "https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle(libs.versions.paper.version.get())
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.runServer {
    minecraftVersion("26.3")
    systemProperty("com.mojang.eula.agree", "true")
    systemProperty("paper.disableStartupVersionCheck", "true")
    jvmArgs("-XX:+AllowEnhancedClassRedefinition")

    debug = true
    javaLauncher = javaToolchains.launcherFor {
        vendor = JvmVendorSpec.JETBRAINS
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// release.yml passes -PreleaseVersion=<version>-build.<run>, so Hangar and the GitHub release share one version
val releaseVersion = providers.gradleProperty("releaseVersion").orElse(project.version.toString())

// the pushed commit's message becomes the Hangar changelog
val commitMessage = providers.exec {
    workingDir(layout.projectDirectory.asFile)
    commandLine("git", "log", "-1", "--pretty=%B")
}.standardOutput.asText.map { it.trim() }

hangarPublish {
    publications.register("plugin") {
        version.set(releaseVersion)
        id.set(providers.gradleProperty("hangarProject"))
        channel.set(providers.gradleProperty("hangarChannel"))
        changelog.set(commitMessage)
        apiKey.set(providers.environmentVariable("HANGAR_API_TOKEN"))
        platforms {
            register(Platforms.PAPER) {
                jar.set(tasks.jar.flatMap { it.archiveFile })
                platformVersions.set(providers.gradleProperty("paperVersion").map { it.split(",").map(String::trim) })
            }
        }
    }
}
