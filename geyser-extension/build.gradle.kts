plugins {
    java
}

group = rootProject.group
version = rootProject.version

val geyserApiVersion = "2.11.0"

dependencies {
    compileOnly("org.geysermc.geyser:api:$geyserApiVersion-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("extension.yml") {
        expand("version" to project.version.toString())
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveFileName = "NexoBedrock-GeyserExtension-${project.version}.jar"
}
