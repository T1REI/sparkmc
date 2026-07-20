plugins {
    java
    application
}

group = "sparkmc"
version = "0.0.3"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.13.1")
}

application {
    mainClass.set("sparkmc.Main")
}

tasks.jar {
    archiveFileName.set("sparkmc.jar")
    manifest {
        attributes(
            "Main-Class" to "sparkmc.Main",
            "Implementation-Title" to "sparkmc",
            "Implementation-Version" to project.version,
        )
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
