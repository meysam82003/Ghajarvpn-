/*
 * OpenVPN for Android core, integrated as a library for Ghajarvpn.
 * Upstream: https://github.com/schwabe/ics-openvpn (GPLv2 + additional terms)
 *
 * NOTE ON NATIVE LIBRARIES:
 * This module used to try to compile the openvpn3/openssl/mbedtls C++ core from
 * source via CMake + SWIG. That source (imported upstream as git submodules) was
 * never present in this project, so that build step could never succeed. We now
 * ship the six required native libraries as prebuilt binaries per-ABI under
 * src/main/jniLibs/<abi>/ instead (libopenvpn.so, libovpnexec.so, libovpn3.so,
 * libovpnutil.so, libosslutil.so, libosslspeedtest.so), extracted from a known
 * good ics-openvpn 0.7.64 build. The Java engine here talks to libopenvpn.so as
 * a child process through the management socket (the classic, non-openvpn3-core
 * path), so no openvpn3 SWIG/JNI glue is required.
 *
 * If you ever want to build the native core from source instead of using
 * prebuilt binaries, restore the CMakeLists-based externalNativeBuild block and
 * populate src/main/cpp/{openvpn,openvpn3,openssl,mbedtls,asio,lz4,fmt} from
 * https://github.com/schwabe/ics-openvpn's git submodules first.
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
        // openvpn3 core is not built into this module (see note above); keep this
        // false so VpnProfile.doUseOpenVPN3() can never route into a native path
        // we don't actually ship, even if a stray preference value is true.
        buildConfigField("boolean", "openvpn3", "false")
        buildConfigField("String", "FLAVOR", "\"normal\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java")
            res.srcDirs("src/main/res")
            manifest.srcFile("src/main/AndroidManifest.xml")
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    // Prebuilt .so files only; no externalNativeBuild here (see note above).
    packaging { jniLibs { useLegacyPackaging = true } }
    lint {
        abortOnError = false
        disable += setOf("MissingTranslation", "UnsafeNativeCodeLocation")
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("androidx.core:core-ktx:1.17.0")
}
