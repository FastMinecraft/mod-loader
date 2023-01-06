plugins {
    id("dev.luna5ama.jar-optimizer").version("1.2-SNAPSHOT")
}

repositories {
    maven("https://maven.fabricmc.net/")
    maven("https://maven.minecraftforge.net/")
    maven("https://libraries.minecraft.net/")
}

val legacyForge by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

configurations {
    getByName("legacyForgeCompileClasspath") {
        extendsFrom(configurations.compileClasspath.get())
        resolutionStrategy {
            force("net.minecraftforge:forge:1.12.2-14.23.5.2860")
        }
    }
}

dependencies {
    compileOnly("com.google.code.findbugs:annotations:3.0.1")
    implementation("org.apache.commons:commons-compress:1.22")

    "legacyForgeCompileOnly"("net.minecraft:launchwrapper:1.12") {
        isTransitive = false
    }

    compileOnly("net.fabricmc:fabric-loader:0.14.9") {
        isTransitive = false
    }

    compileOnly("cpw.mods:modlauncher:8.1.3") {
        isTransitive = false
    }
    compileOnly("org.ow2.asm:asm-tree:7.2")
    compileOnly("net.minecraftforge:forge:1.16.5-36.2.34:universal") {
        isTransitive = false
    }
    compileOnly("org.apache.logging.log4j:log4j-api:2.8.1")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

tasks {
    jar {
        from(legacyForge.output)

        from(configurations.runtimeClasspath.get().elements.map { set ->
            set.map { fileSystemLocation ->
                fileSystemLocation.asFile.let {
                    if (it.isDirectory) it else zipTree(it)
                }
            }
        })

        exclude("dev/fastmc/loader/Constants.class")

        archiveBaseName.set("mod-loader-runtime")
    }
}

val optimizeJar = jarOptimizer.register(tasks.jar, "dev.fastmc.loader")

artifacts {
    archives(optimizeJar)
}

publishing {
    publications {
        create<MavenPublication>("runtime") {
            artifactId = "mod-loader-runtime"
            artifact(optimizeJar) {
                classifier = ""
            }
        }
    }
}