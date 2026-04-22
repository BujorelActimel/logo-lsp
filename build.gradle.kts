plugins {
    kotlin("jvm") version "2.1.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.logolsp"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:1.0.0")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

kotlin {
    jvmToolchain(21)
}

tasks.shadowJar {
    archiveClassifier.set("")
    manifest {
        attributes["Main-Class"] = "com.logolsp.server.MainKt"
    }
    // Exclude duplicate files that cause warnings
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.test {
    useJUnitPlatform()
}
