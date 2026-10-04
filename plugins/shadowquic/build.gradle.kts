plugins { alias(libs.plugins.android.application) }
val hostCertificate = providers.gradleProperty("ghajarHostCertificate").orElse("").get()
val pluginCode = providers.gradleProperty("shadowquicVersionCode").orElse("1").get().toInt()
val pluginVersion = providers.gradleProperty("shadowquicVersionName").orElse("").get()
android {
    namespace = "net.ghajar.plugin.shadowquic"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig {
        applicationId = "net.ghajar.plugin.shadowquic.v$pluginCode"
        minSdk = 26; targetSdk = 36; versionCode = pluginCode; versionName = pluginVersion
        buildConfigField("String", "HOST_CERTIFICATE", "\"$hostCertificate\"")
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }
    buildFeatures { buildConfig = true }
    packaging { jniLibs.useLegacyPackaging = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies { implementation(project(":plugin-api")) }
// Fail closed: no fake APK containing only a service and no native engine.
tasks.matching { it.name == "preBuild" }.configureEach {
    doFirst {
        require(providers.gradleProperty("shadowquicVersionCode").isPresent && pluginCode > 0 && pluginVersion.isNotBlank()) { "Provide the owner-approved plugin version name and monotonically increasing code" }
        require(hostCertificate.matches(Regex("[0-9a-f]{64}"))) { "Provide the real Ghajar signing certificate SHA-256" }
        listOf("arm64-v8a", "armeabi-v7a").forEach { abi ->
            require(file("src/main/jniLibs/$abi/libshadowquic.so").isFile) { "Pinned ShadowQUIC binary missing: $abi" }
        }
    }
}

val identitySources = layout.buildDirectory.dir("generated/pluginIdentity")
val generateIdentity = tasks.register("generatePluginIdentity") {
    inputs.property("pluginCode", pluginCode)
    outputs.dir(identitySources)
    doLast {
        val source = identitySources.get().file("net/ghajar/plugin/shadowquic/v$pluginCode/EngineService.java").asFile
        source.parentFile.mkdirs()
        source.writeText("package net.ghajar.plugin.shadowquic.v$pluginCode; public final class EngineService extends net.ghajar.plugin.shadowquic.ShadowQuicService {}")
    }
}
android.sourceSets.getByName("main").java.srcDir(identitySources)
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(generateIdentity) }
