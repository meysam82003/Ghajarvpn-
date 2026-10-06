import java.util.Properties

// The Ghajar web app for Android: the same shop, services and notices as the
// web, in a full-screen app with Android notifications. Plain Java, no
// libraries; it carries no VPN core.
plugins {
    id("com.android.application")
}

val signing = rootProject.file("keystore.properties")

android {
    namespace = "com.ghajarvpn.web"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ghajarvpn.web"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"
    }

    signingConfigs {
        if (signing.exists()) {
            create("release") {
                val p = Properties().apply { signing.inputStream().use { load(it) } }
                storeFile = file(p.getProperty("storeFile"))
                storePassword = p.getProperty("storePassword")
                keyAlias = p.getProperty("keyAlias")
                keyPassword = p.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // The owner's release key when CI has it; otherwise an installable debug-signed build.
            signingConfig = if (signing.exists()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}
