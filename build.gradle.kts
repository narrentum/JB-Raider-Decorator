import java.util.Properties
import org.jetbrains.intellij.platform.gradle.tasks.PatchPluginXmlTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.20"
    id("org.jetbrains.intellij.platform") version "2.9.0"
}

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

// IntelliJ plugin configuration — target Rider
intellij {
    // Target Rider distribution (RD)
    type.set("RD")

    // Choose either a version to download (recommended for CI) or a localPath for fast local dev.
    // For Rider 2025.x use a matching version string, e.g.:
    version.set("2025.2")

    // If you prefer to use a locally installed Rider during development, uncomment and set:
    // localPath.set("C:/Program Files/JetBrains/JetBrains Rider 2025.2.2")

    // Optional: list of dependent plugins
    // plugins.set(listOf())
}

/**
 * JDK 17. В 2.9.0 надёжнее указать toolchain через Kotlin DSL:
 */
kotlin {
    jvmToolchain(17)
}

java {
    modularity.inferModulePath.set(false)
}

/**
 * Целевая IDE: Rider 2025.2.x — через локально установленную IDE.
 * ВАЖНО: путь укажи с прямыми слэшами (или экранируй обратные), иначе "Illegal escape".
 */
dependencies {
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))

    // If you need to use a local IDE for development, prefer setting `intellij { localPath.set(...) }` above.
}

/**
 * buildPlugin -> bumpPatchVersion
 */
tasks.named("buildPlugin") {
    dependsOn("bumpPatchVersion")
}

// tasks.named<Jar>("jar") {
//     from("src/main/resources/META-INF") {
//         include("pluginIcon*.png")
//         into("META-INF")
//     }
//     from("src/main/resources/icons") {
//         into("icons")
//     }
// }

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}

/* ===================== Управление версией ===================== */
fun readVersionFromGradleProps(): String 
{
    val propsFile 				= rootProject.file("gradle.properties")
    val props 					= Properties().apply { propsFile.inputStream().use { load(it) } }

    return props.getProperty("version") ?: error("Property 'version' not found")
}

fun writeVersionToGradleProps(newVersion: String) 
{
    val propsFile 				= rootProject.file("gradle.properties")
    val props 					= Properties().apply { propsFile.inputStream().use { load(it) } }

    props.setProperty			("version", newVersion)
    propsFile.outputStream().use { props.store(it, "Updated by bump task") }
    project.version 			= newVersion

    println						("Version updated to: $newVersion")
}

data class Version4(val major: Int, val minor: Int, val patch: Int, val build: Int?) 
{
    override fun toString(): String =  if (build != null) "$major.$minor.$patch.$build" else "$major.$minor.$patch"

    companion object 
	{
        private val re 			= Regex("""^\s*(\d+)\.(\d+)\.(\d+)(?:\.(\d+))?\s*$""")

        fun parse(s: String): Version4 
		{
            val m = re.matchEntire(s) ?: error("Unsupported version format: '$s'")
            val (a, b, c, d) = m.destructured

            return Version4(a.toInt(), b.toInt(), c.toInt(), d.ifEmpty { null }?.toInt())
        }
    }
}

fun bumpPatch(v: Version4) = v.copy(patch = v.patch + 1, build = null)
fun bumpMinor(v: Version4) = v.copy(minor = v.minor + 1, patch = 0, build = null)
fun bumpMajor(v: Version4) = v.copy(major = v.major + 1, minor = 0, patch = 0, build = null)
fun bumpBuild(v: Version4) = v.copy(build = (v.build ?: 0) + 1)

tasks.register("bumpPatchVersion") {
    group = "versioning"
    description = "Increment patch (X.Y.Z -> X.Y.(Z+1))"
    doLast {
        val cur = Version4.parse(readVersionFromGradleProps())
        writeVersionToGradleProps(bumpPatch(cur).toString())
    }
}

tasks.register("bumpMinorVersion") {
    group = "versioning"
    description = "Increment minor (X.Y.Z -> X.(Y+1).0)"
    doLast {
        val cur = Version4.parse(readVersionFromGradleProps())
        writeVersionToGradleProps(bumpMinor(cur).toString())
    }
}

tasks.register("bumpMajorVersion") {
    group = "versioning"
    description = "Increment major ((X+1).0.0)"
    doLast {
        val cur = Version4.parse(readVersionFromGradleProps())
        writeVersionToGradleProps(bumpMajor(cur).toString())
    }
}

tasks.register("bumpBuildNumber") {
    group = "versioning"
    description = "Increment 4th component (X.Y.Z.B -> X.Y.Z.(B+1))"
    doLast {
        val cur = Version4.parse(readVersionFromGradleProps())
        writeVersionToGradleProps(bumpBuild(cur).toString())
    }
}
