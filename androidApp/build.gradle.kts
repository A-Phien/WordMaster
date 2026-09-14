import java.util.Properties

plugins {
    id("com.android.application")
    alias(libs.plugins.compose.compiler)
}

// Read secrets from local.properties (never committed to VCS)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "dev.johnoreilly.wordmaster.androidApp"

    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        compileSdk = libs.versions.android.targetSdk.get().toInt()

        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Exposed to app code via BuildConfig.GEMINI_API_KEY
        buildConfigField(
            "String", "GEMINI_API_KEY",
            "\"${localProps["gemini.api.key"] ?: ""}\""
        )
        // Exposed to app code via BuildConfig.PIXABAY_API_KEY
        buildConfigField(
            "String", "PIXABAY_API_KEY",
            "\"${localProps["PIXABAY_API_KEY"] ?: ""}\""
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true   // required for buildConfigField
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}



dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
}
