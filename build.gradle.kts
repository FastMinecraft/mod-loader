plugins {
    id("java")
    id("dev.fastmc.maven-repo").version("1.0.0").apply(false)
}

subprojects {
    apply {
        plugin("dev.fastmc.maven-repo")
    }
}

allprojects {
    group = "dev.fastmc"
    version = "1.0-SNAPSHOT"

    apply {
        plugin("java")
    }

    repositories {
        mavenCentral()
    }
}