plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val betaSigningStoreFile = providers.environmentVariable("ANDROID_SIGNING_STORE_FILE").orNull
val betaSigningStorePassword = providers.environmentVariable("ANDROID_SIGNING_STORE_PASSWORD").orNull
val betaSigningKeyAlias = providers.environmentVariable("ANDROID_SIGNING_KEY_ALIAS").orNull
val betaSigningKeyPassword = providers.environmentVariable("ANDROID_SIGNING_KEY_PASSWORD").orNull
val betaSigningValues = listOf(
    betaSigningStoreFile,
    betaSigningStorePassword,
    betaSigningKeyAlias,
    betaSigningKeyPassword
)
val betaSigningRequested = betaSigningValues.any { !it.isNullOrBlank() }
val betaSigningConfigured = betaSigningValues.all { !it.isNullOrBlank() }

if (betaSigningRequested && !betaSigningConfigured) {
    error("Beta signing environment is incomplete")
}

android {
    namespace = "com.java.myapplication"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.java.myapplication"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (betaSigningConfigured) {
            create("stableBeta") {
                storeFile = file(betaSigningStoreFile!!)
                storePassword = betaSigningStorePassword!!
                keyAlias = betaSigningKeyAlias!!
                keyPassword = betaSigningKeyPassword!!
                storeType = "PKCS12"
            }
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "AI API Dashboard Dev")
            signingConfigs.findByName("stableBeta")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        resValues = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Operit/Proot on Linux ARM64 needs the matching native AAPT2 binary.
// Other hosts (Windows and GitHub x64 runners) must keep the Android plugin default.
val isLinuxArm64 = System.getProperty("os.name").contains("linux", ignoreCase = true) &&
    System.getProperty("os.arch").let { it.equals("aarch64", true) || it.equals("arm64", true) }
if (isLinuxArm64) {
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "com.android.tools.build" && requested.name == "aapt2") {
                useTarget("com.android.tools.build:aapt2:${'$'}{requested.version}:linux-aarch64")
            }
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

