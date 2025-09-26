plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("org.jetbrains.intellij") version "1.17.2"
}

group = "com.codedecorator"
version = "1.2.9" // v1.2.9 - Fixed empty table issue: enum values as strings, debug output

repositories {
    mavenCentral()
}

// Use JDK 17 for IntelliJ Platform compatibility
java {
    sourceCompatibility = JavaVersion.VERSION_17  // IntelliJ Platform requires JDK 17+
    targetCompatibility = JavaVersion.VERSION_17
}

// Kotlin compilation for JDK 17+ compatibility  
kotlin {
    jvmToolchain(17)
}

intellij {
    version.set("2024.3") // Use stable IntelliJ IDEA 2024.3 for build compatibility
    type.set("IC") // IntelliJ Community Edition for build
    
    // Don't specify specific plugin modules, use default platform capabilities
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    patchPluginXml {
        sinceBuild.set("243") // IntelliJ/Rider 2024.3+  
        untilBuild.set("252.*") // Up to Rider 2025.2.x (includes your 2025.2.2)
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
