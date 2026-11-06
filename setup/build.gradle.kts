import java.util.Properties

plugins { alias(libs.plugins.android.application) }

// Signed with the same release key as Aux, see app/build.gradle.kts
val releaseKeyFile = (System.getenv("AUX_SIGNING") ?: rootProject.file("../keys/aux-release.properties").path).let(::File)
val releaseKey = releaseKeyFile.takeIf { it.exists() }?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }

// Carries the Tube release APK and hands it to the system installer the way Android Auto expects,
// so Tube can be installed from the phone alone with no PC, adb or Shizuku
val tubeApk = layout.buildDirectory.dir("tubeAssets")
val bundleTube by tasks.registering(Copy::class) {
    dependsOn(":app:assembleRelease")
    from(rootProject.file("app/build/outputs/apk/release/app-release.apk"))
    into(tubeApk)
    rename { "tube.apk" }
}

android {
    namespace = "com.sya.tube.setup"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.syavi.aux.setup"
        minSdk = 26
        targetSdk = 35
        versionCode = 29
        versionName = "1.0.0"
    }
    signingConfigs {
        if (releaseKey != null) create("release") {
            storeFile = File(releaseKeyFile.parentFile, releaseKey.getProperty("storeFile"))
            storePassword = releaseKey.getProperty("storePassword")
            keyAlias = releaseKey.getProperty("keyAlias")
            keyPassword = releaseKey.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    // A plain directory, AGP 9 rejects lazy providers here and preBuild already depends on the copy
    sourceSets["main"].assets.directories.add(layout.buildDirectory.dir("tubeAssets").get().asFile.path)
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    // The bundled APK is already compressed, storing it avoids recompressing three megabytes for nothing
    androidResources { noCompress += "apk" }
}

tasks.named("preBuild") { dependsOn(bundleTube) }

dependencies {
    implementation("androidx.core:core:1.18.0")
}
