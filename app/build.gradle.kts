import java.util.Properties

plugins { alias(libs.plugins.android.application) }

// The release key lives outside the source tree, a checkout without it builds with the debug key instead
val releaseKeyFile = (System.getenv("AUX_SIGNING") ?: rootProject.file("../keys/aux-release.properties").path).let(::File)
val releaseKey = releaseKeyFile.takeIf { it.exists() }?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }
// The GitHub repository releases are checked in, as owner/name, empty leaves the update check off
val updateRepo = (findProperty("aux.updateRepo") as String?).orEmpty()
// Where the release list is read, a test build can point this at a local stand-in for GitHub
val updateApi = (findProperty("aux.updateApi") as String?) ?: "https://api.github.com"
val localBuild = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val usageEndpoint = providers.gradleProperty("aux.usageEndpoint")
    .orElse(providers.environmentVariable("AUX_USAGE_ENDPOINT"))
    .orElse(localBuild.getProperty("aux.usageEndpoint", "")).get()

android {
    namespace = "com.thekirankumar.youtubeauto2"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.syavi.aux"
        minSdk = 24
        targetSdk = 35
        versionCode = 55
        versionName = "1.1.0"
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
        buildConfigField("String", "UPDATE_API", "\"$updateApi\"")
        buildConfigField("String", "USAGE_ENDPOINT", "\"$usageEndpoint\"")
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
        // Aux ships in English only, its text sits beside the code that shows it rather than in string resources
        disable += listOf("SetTextI18n", "HardcodedText")
        // Aux is a browser, every page it opens needs JavaScript
        disable += "SetJavaScriptEnabled"
        // Library, Gradle and target SDK updates change behaviour, so they are done and tested on their own, not as lint warnings
        disable += listOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
    }
}
dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)
    implementation(libs.newpipe.extractor)
    // martoreto's unofficial Android Auto SDK, the same one CarStream uses, fetched at build time rather than kept in this repository
    implementation(libs.aauto.sdk) { artifact { type = "aar" } }
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.media)
    implementation(libs.androidx.car.app)
    implementation(libs.androidx.car.app.projected)
    implementation(libs.exoplayer)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
