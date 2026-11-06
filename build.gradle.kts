plugins {
    java
    alias(libs.plugins.prun)
    alias(libs.plugins.paperweight)
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
