plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace  = "io.github.olegdaniv.qakit.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api(libs.timber)
    api(libs.seismic)
    api(libs.okhttp)
    api(libs.chucker)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.fragment.ktx)
}
