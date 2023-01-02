repositories {
    maven("https://maven.fabricmc.net/")
    maven("https://maven.minecraftforge.net/")
}

dependencies {
    compileOnly("com.google.code.findbugs:annotations:3.0.1")
    implementation("org.tukaani:xz:1.9")

    compileOnly("net.fabricmc:fabric-loader:0.14.9")

    compileOnly("cpw.mods:modlauncher:8.1.3")
    compileOnly("net.minecraftforge:forge:1.16.5-36.2.34:universal")
    compileOnly("org.apache.logging.log4j:log4j-api:2.8.1")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

tasks {
    jar {
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

publishing {
    publications {
        create<MavenPublication>("runtime") {
            artifactId = "mod-loader-runtime"
            artifact(tasks.jar)
        }
    }
}