plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.ghajarvpn.downloads"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { abortOnError = false }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    testImplementation("junit:junit:4.13.2")
}
