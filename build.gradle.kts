allprojects {
    group = "dev.fastmc"
    version = "1.1.0"
}

plugins {
    id("java")
    id("dev.fastmc.maven-repo").version("1.0.0").apply(false)
}

subprojects {
    apply {
        plugin("java")
        plugin("dev.fastmc.maven-repo")
    }

    repositories {
        mavenCentral()
    }

    base {
        archivesName.set("${rootProject.name}-${project.name}")
    }
}

tasks.jar.get().isEnabled = false
