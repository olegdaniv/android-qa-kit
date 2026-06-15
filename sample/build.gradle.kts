plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace  = "io.github.olegdaniv.qakit.sample"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.olegdaniv.qakit.sample"
        minSdk        = 24
        targetSdk     = 35
        versionCode   = 2
        versionName   = "1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Завжди доступна логіка (QaKit, QaLogger, DeviceInfo, ...)
    implementation(project(":qa-core"))
    implementation(libs.okhttp)
    implementation(libs.androidx.fragment.ktx)

    // Debug — повна бібліотека (UI панель)
    debugImplementation(project(":qa-ui-compose"))
    debugImplementation(project(":qa-ui-view"))

    // Release — порожні стаби, нуль оверхеду
    releaseImplementation(project(":qa-no-op"))

    val bom = platform(libs.compose.bom)
    implementation(bom)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.androidx.core.ktx)
}
