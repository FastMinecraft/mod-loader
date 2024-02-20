import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-gradle-plugin`
    kotlin("jvm")
}

repositories {
    maven("https://maven.fabricmc.net/")
    maven("https://maven.minecraftforge.net/")
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    implementation("org.apache.commons:commons-compress:1.25.0")

    implementation("com.google.code.gson:gson:2.10")
    implementation("org.ow2.asm:asm-commons:9.4")
    implementation("org.ow2.asm:asm-tree:9.4")
}

gradlePlugin {
    plugins {
        create("mod-loader-plugin") {
            id = "dev.fastmc.mod-loader-plugin"
            displayName = "mod-loader-plugin"
            description = "Allows packaging mod implementations for different platform into the same Jar"
            implementationClass = "dev.fastmc.loader.ModLoaderPlugin"
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
}

tasks {
    withType(KotlinCompile::class.java) {
        kotlinOptions {
            jvmTarget = "17"
        }
    }

    processResources {
        expand("version" to project.version)
    }
}

afterEvaluate {
    publishing {
        publications {
            forEach {
                (it as MavenPublication)
                if (it.artifactId == project.name) {
                    it.artifactId = "mod-loader-plugin"
                } else {
                    it.pom.withXml {
                        val elements = asElement().getElementsByTagName("artifactId")
                        for (i in 0 until elements.length) {
                            val element = elements.item(i)
                            if (element.textContent == project.name) {
                                element.textContent = "mod-loader-plugin"
                            }
                        }
                    }
                }
            }
        }
    }
}