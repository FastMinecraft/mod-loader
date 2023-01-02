plugins {
    id("java")
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