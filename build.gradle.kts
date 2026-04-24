plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.3.0"
    id("com.gradleup.shadow") version "8.3.6"
}

group = "com.logolsp"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:1.0.0")

    intellijPlatform {
        intellijIdeaUltimate("2024.3.2")
    }

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    pluginConfiguration {
        id = "com.logolsp"
        name = "LOGO Language Support"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "232"   // IntelliJ 2023.2 — first release with built-in LSP client
        }
    }
}

// Standalone server fat JAR — bundled inside the plugin and launched as a subprocess.
// IntelliJ JARs are compileOnly (provided by the IDE), so they are not included here.
tasks.shadowJar {
    archiveBaseName.set("logo-lsp-server")
    archiveClassifier.set("")
    archiveVersion.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "com.logolsp.server.MainKt"
    }
    exclude("META-INF/plugin.xml")
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    mergeServiceFiles()
}

// Place the server JAR inside the plugin sandbox so it is available at runtime.
tasks.named<Sync>("prepareSandbox") {
    dependsOn(tasks.shadowJar)
    from(tasks.shadowJar.flatMap { it.archiveFile }) {
        into("${rootProject.name}/server")
    }
}

tasks.test {
    useJUnitPlatform()
}
