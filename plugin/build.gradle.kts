import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    `java-gradle-plugin`
}

repositories {
    maven("https://maven.fabricmc.net/")
    maven("https://maven.minecraftforge.net/")
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")

    implementation("org.apache.commons:commons-compress:1.22")
    implementation("org.tukaani:xz:1.9")

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
    withSourcesJar()
}

tasks {
    withType(org.gradle.jvm.tasks.Jar::class.java) {
        archiveBaseName.set("mod-loader-plugin")
    }

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