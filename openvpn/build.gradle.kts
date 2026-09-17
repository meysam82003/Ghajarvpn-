import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider

/*
 * OpenVPN for Android core, integrated as a library for Ghajarvpn.
 * Upstream: https://github.com/schwabe/ics-openvpn (GPLv2 + additional terms)
 */
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "de.blinkt.openvpn"
    compileSdk {
        version = release(36) { minorApiLevel = 1 }
    }
    ndkVersion = "28.2.13676358"

    buildFeatures {
        aidl = true
        buildConfig = true
    }

    defaultConfig {
        minSdk = 24
        externalNativeBuild { cmake { } }
        buildConfigField("boolean", "openvpn3", "true")
        buildConfigField("String", "FLAVOR", "\"normal\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt") }
    }

    sourceSets {
        getByName("main") {
            // Container-block form matches the working strongswan module.
            java.srcDirs("src/main/java", "src/skeleton/java")
            res.srcDirs("src/main/res", "src/skeleton/res")
            manifest.srcFile("src/main/AndroidManifest.xml")
        }
    }

    packaging { jniLibs { useLegacyPackaging = true } }
    lint {
        abortOnError = false
        disable += setOf("MissingTranslation", "UnsafeNativeCodeLocation")
    }
}

val swigCommand = when {
    file("/opt/homebrew/bin/swig").exists() -> "/opt/homebrew/bin/swig"
    file("/usr/local/bin/swig").exists() -> "/usr/local/bin/swig"
    else -> "swig"
}

abstract class GenerateGhajarOpenVpnSwig : Exec() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty
}

fun registerSwigTask(variantName: String): TaskProvider<GenerateGhajarOpenVpnSwig> {
    val baseDir = layout.buildDirectory.dir("generated/source/ovpn3swig/$variantName")
    return tasks.register<GenerateGhajarOpenVpnSwig>("generateOpenVPN3Swig${variantName.replaceFirstChar { it.uppercase() }}") {
        val generated = baseDir.get().asFile.resolve("net/openvpn/ovpn3")
        outputDir.set(baseDir)
        doFirst { generated.mkdirs() }
        commandLine(
            swigCommand,
            "-outdir", generated.absolutePath,
            "-outcurrentdir", "-c++", "-java", "-package", "net.openvpn.ovpn3",
            "-Isrc/main/cpp/openvpn3/client", "-Isrc/main/cpp/openvpn3/",
            "-DOPENVPN_PLATFORM_ANDROID",
            "-o", "$generated/ovpncli_wrap.cxx",
            "-oh", "$generated/ovpncli_wrap.h",
            "src/main/cpp/openvpn3/client/ovpncli.i"
        )
        inputs.file("src/main/cpp/openvpn3/client/ovpncli.i")
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        val task = registerSwigTask(variant.name)
        variant.sources.java?.addGeneratedSourceDirectory(task, GenerateGhajarOpenVpnSwig::outputDir)
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("androidx.core:core-ktx:1.17.0")
}
