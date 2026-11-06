import java.util.Properties

plugins { alias(libs.plugins.android.application) }

// The release key lives outside the source tree, a checkout without it builds with the debug key instead
val releaseKeyFile = (System.getenv("AUX_SIGNING") ?: rootProject.file("../keys/aux-release.properties").path).let(::File)
val releaseKey = releaseKeyFile.takeIf { it.exists() }?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }
// The GitHub repository releases are checked in, as owner/name, empty leaves the update check off
val updateRepo = (findProperty("aux.updateRepo") as String?).orEmpty()
// Where the release list is read, a test build can point this at a local stand-in for GitHub
val updateApi = (findProperty("aux.updateApi") as String?) ?: "https://api.github.com"

android {
    namespace = "com.thekirankumar.youtubeauto2"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.syavi.aux"
        minSdk = 24
        targetSdk = 35
        versionCode = 41
        versionName = "1.0.0"
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
        buildConfigField("String", "UPDATE_API", "\"$updateApi\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { buildConfig = true }
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
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("benchmark") {
            initWith(getByName("release"))
            isDebuggable = true
            matchingFallbacks += "release"
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17; isCoreLibraryDesugaringEnabled = true }
    lint {
        disable += "ExpiredTargetSdkVersion"
        // android:tint works on ImageView since Android 5, Aux starts at Android 7
        disable += "UseAppTint"
        // Retained upstream numeric SDK flags and incomplete upstream translations.
        warning += listOf("WrongConstant", "MissingTranslation", "MissingIntentFilterForMediaSearch")
    }
}
dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.26.5")
    // martoreto's unofficial Android Auto SDK, the same one CarStream uses, fetched at build time rather than kept in this repository
    implementation("com.github.martoreto:aauto-sdk:v4.5@aar")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.webkit:webkit:1.17.1")
    implementation("androidx.fragment:fragment:1.8.9")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.media:media:1.7.0")
    implementation(libs.androidx.car.app)
    implementation(libs.androidx.car.app.projected)
    implementation("com.google.android.exoplayer:exoplayer:2.19.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
