plugins {
    java
}

group = "dev.nexobedrock"
version = "0.1.25"

val paperVersion = "1.21.10-R0.1-SNAPSHOT"
val nexoVersion = "1.29.1"
val gsonVersion = "2.11.0"
val junitVersion = "5.11.4"

dependencies {
    // Provided by the Paper server at runtime.
    compileOnly("io.papermc.paper:paper-api:$paperVersion")
    compileOnly("com.google.code.gson:gson:$gsonVersion")

    // Provided by the Nexo plugin at runtime. We intentionally only use the
    // stable public API surface (NexoItems / ItemBuilder / NexoMeta).
    compileOnly("com.nexomc:nexo:$nexoVersion")
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")

    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("com.google.code.gson:gson:$gsonVersion")
    testImplementation("io.papermc.paper:paper-api:$paperVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") {
        expand("version" to project.version.toString())
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.jar {
    archiveFileName = "NexoBedrock-${project.version}.jar"
    manifest {
        attributes(
            "Implementation-Title" to "NexoBedrock",
            "Implementation-Version" to project.version
        )
    }
}
