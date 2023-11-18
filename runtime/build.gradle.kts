plugins {
    id("dev.luna5ama.jar-optimizer").version("1.2.0")
    id("com.github.johnrengelman.shadow").version("7.1.2")
}

repositories {
    maven("https://maven.fabricmc.net/")
    maven("https://maven.minecraftforge.net/")
    maven("https://libraries.minecraft.net/")
}

val configureSourceSet: SourceSet.() -> Unit = {
    compileClasspath += sourceSets.main.get().output
    compileClasspath += sourceSets.main.get().compileClasspath

    runtimeClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().runtimeClasspath
}

val legacyForge by sourceSets.creating(configureSourceSet)
val fabric by sourceSets.creating(configureSourceSet)
val forge116 by sourceSets.creating(configureSourceSet)
val forge118 by sourceSets.creating(configureSourceSet)
val forge119 by sourceSets.creating(configureSourceSet)

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

val lib118 by configurations.creating { isTransitive = false }
val lib119 by configurations.creating { isTransitive = false }

dependencies {
    implementation("org.tukaani:xz:1.9")
    compileOnly("org.apache.logging.log4j:log4j-api:2.8.1")

    "legacyForgeCompileOnly"("net.minecraft:launchwrapper:1.12") { isTransitive = false }
    "legacyForgeCompileOnly"("net.minecraftforge:forge:1.12.2-14.23.5.2860:universal") { isTransitive = false }

    "fabricCompileOnly"("net.fabricmc:fabric-loader:0.14.9")

    "forge116CompileOnly"("net.minecraftforge:forge:1.16.5-36.2.34:launcher")
    "forge116CompileOnly"("net.minecraftforge:forgespi:3.2.0")
    "forge116CompileOnly"("org.apache.commons:commons-lang3:3.12.0")

    lib118("net.minecraftforge:fmlloader:1.18.2-40.1.0")
    "forge118CompileOnly"("cpw.mods:securejarhandler:1.0.3")
    "forge118CompileOnly"("cpw.mods:modlauncher:9.1.3")
    "forge118CompileOnly"("net.minecraftforge:forgespi:4.0.15-4.x")

    lib119("net.minecraftforge:fmlloader:1.19.2-43.0.0")
    "forge119CompileOnly"("cpw.mods:securejarhandler:2.1.4")
    "forge119CompileOnly"("cpw.mods:modlauncher:10.0.8")
    "forge119CompileOnly"("net.minecraftforge:forgespi:6.0.0")
}

afterEvaluate {
    dependencies {
        "forge118CompileOnly"(files(lib118.singleFile))
        "forge119CompileOnly"(files(lib119.singleFile))
    }
}

tasks {
    fun JavaCompile.setCompilerVersion(version: Int) {
        val fullVersion = if (version <= 8) "1.$version" else version.toString()
        this.sourceCompatibility = fullVersion
        this.targetCompatibility = fullVersion
        javaToolchains {
            javaCompiler.set(compilerFor { languageVersion.set(JavaLanguageVersion.of(version)) })
        }
    }

    withType(JavaCompile::class) { setCompilerVersion(8) }
    named<JavaCompile>(forge118.compileJavaTaskName) { setCompilerVersion(17) }
    named<JavaCompile>(forge119.compileJavaTaskName) { setCompilerVersion(17) }

    shadowJar {
        configurations = listOf(project.configurations.runtimeClasspath.get())
        relocate("org.tukaani.xz", "dev.fastmc.loader.xz")
        exclude("dev/fastmc/loader/core/Constants.class")
    }
}

val legacyForgeJar by tasks.creating(Jar::class) {
    archiveClassifier.set("legacy-forge")
    from(legacyForge.output)
}

val fabricJar by tasks.creating(Jar::class) {
    archiveClassifier.set("fabric")
    from(fabric.output)
}

val forge116Jar by tasks.creating(Jar::class) {
    archiveClassifier.set("forge-1.16")
    from(forge116.output)
}

val forge118Jar by tasks.creating(Jar::class) {
    archiveClassifier.set("forge-1.18")
    from(forge118.output)
}

val forge119Jar by tasks.creating(Jar::class) {
    archiveClassifier.set("forge-1.19")
    from(forge119.output)
}

val optimizeJar = jarOptimizer.register(tasks.shadowJar, "dev.fastmc.loader.core")

artifacts {
    archives(optimizeJar)
    archives(legacyForgeJar)
    archives(fabricJar)
    archives(forge116Jar)
    archives(forge118Jar)
    archives(forge119Jar)
}

publishing {
    publications {
        create<MavenPublication>("runtime") {
            artifactId = "mod-loader-runtime"
            artifact(optimizeJar) {
                classifier = ""
            }
            artifact(legacyForgeJar)
            artifact(fabricJar)
            artifact(forge116Jar)
            artifact(forge118Jar)
            artifact(forge119Jar)
        }
    }
}