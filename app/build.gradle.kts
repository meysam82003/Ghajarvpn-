import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val ghajarDemoBuild = providers.gradleProperty("ghajar.demo").orNull == "true"
val ghajarSignedDemo = providers.gradleProperty("ghajar.demo.signed").orNull == "true"
check(!ghajarSignedDemo || ghajarDemoBuild) { "Signed demo requires -Pghajar.demo=true" }

fun demoSigningValue(name: String): String = System.getenv(name)
    ?.takeIf { it.isNotBlank() }
    ?: error("Missing demo signing setting: $name. No ephemeral-key fallback is allowed.")

android {
    namespace = "net.gozar.app"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.ghajarvpn.app"
        // The bundled core AAR requires API 26. Keep the API 24 release target
        // explicit while building an honestly labelled Android 8+ demo.
        minSdk = if (ghajarDemoBuild) 26 else 24
        targetSdk = 36
        versionCode = 30003
        versionName = if (ghajarDemoBuild) "3.0.3-demo" else "3.0.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (ghajarSignedDemo) {
            create("demo") {
                storeFile = file(demoSigningValue("GHAJAR_DEMO_KEYSTORE_FILE"))
                    .also { check(it.isFile) { "Demo keystore file is missing" } }
                storePassword = demoSigningValue("GHAJAR_DEMO_KEYSTORE_PASSWORD")
                keyAlias = demoSigningValue("GHAJAR_DEMO_KEY_ALIAS")
                keyPassword = demoSigningValue("GHAJAR_DEMO_KEY_PASSWORD")
            }
        }
        create("release") {
            val envKeystore = System.getenv("KEYSTORE_FILE")
            val propsFile = rootProject.file("keystore.properties")
            if (envKeystore != null) {
                storeFile = file(envKeystore)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            } else if (propsFile.exists()) {
                val props = Properties().apply {
                    propsFile.inputStream().use { load(it) }
                }
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    buildTypes {
        debug {
            if (ghajarSignedDemo) signingConfig = signingConfigs.getByName("demo")
        }
        release {
            val hasSigning = System.getenv("KEYSTORE_FILE") != null ||
                    rootProject.file("keystore.properties").exists()
            if (hasSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(project(":strongswan"))
    implementation(project(":openvpn"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation(files("libs/gozarcore.aar"))
    implementation("androidx.compose.material:material-icons-extended")
    implementation("dev.chrisbanes.haze:haze:1.6.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.github.mwiede:jsch:0.2.17")
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
