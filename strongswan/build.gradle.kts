plugins {
	alias(libs.plugins.android.library)
}

android {
	namespace = "org.strongswan.android"
	compileSdk {
		version = release(36) {
			minorApiLevel = 1
		}
	}

	ndkVersion = "28.2.13676358"

	defaultConfig {
		minSdk = 24

		externalNativeBuild {
			ndkBuild {
				arguments += "-j" + Runtime.getRuntime().availableProcessors()
				arguments += "APP_ALLOW_MISSING_DEPS=true"
				cFlags += "-DHAVE_SIGWAITINFO"
			}
		}

		ndk {
			val testAbi = providers.gradleProperty("ghajar.testAbi").orNull
			check(testAbi == null || (testAbi == "x86_64" && providers.gradleProperty("ghajar.demo").orNull == "true"))
			abiFilters += if (testAbi == null) listOf("arm64-v8a", "armeabi-v7a") else listOf(testAbi)
		}

		consumerProguardFiles("consumer-rules.pro")

	}

	externalNativeBuild {
		ndkBuild {
			path = file("src/frontends/android/app/src/main/jni/Android.mk")
		}
	}

	sourceSets {
		getByName("main") {
			manifest.srcFile("src/main/AndroidManifest.xml")
			// Classic API; the typed directories DSL breaks under AGP 9.
			java.srcDirs("src/upstream-java", "src/patched")
			res.srcDirs("src/frontends/android/app/src/main/res")
		}
	}

	buildTypes {
		release {
			isMinifyEnabled = false
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_11
		targetCompatibility = JavaVersion.VERSION_11
	}

	packaging {
		jniLibs {
			useLegacyPackaging = true
		}
	}

	lint {
		checkReleaseBuilds = false
		abortOnError = false
		disable += "all"
	}

	buildFeatures {
		buildConfig = false
	}
}

val syncUpstreamJava by tasks.registering(Sync::class) {
	from("src/frontends/android/app/src/main/java") {
		exclude("org/strongswan/android/ui/**")
	}
	into(layout.projectDirectory.dir("src/upstream-java"))
}

tasks.named("preBuild") {
	dependsOn(syncUpstreamJava)
}

dependencies {
	implementation("androidx.appcompat:appcompat:1.7.0")
	implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
	implementation("androidx.preference:preference:1.2.1")
	implementation("com.google.android.material:material:1.12.0")
}
