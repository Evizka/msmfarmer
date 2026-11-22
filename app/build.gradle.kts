plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.msmfarmer.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.msmfarmer.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "1.5.2"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Постоянный ключ: все сборки подписываются одинаково, поэтому
    // новая версия ставится поверх предыдущей без удаления.
    signingConfigs {
        create("persistent") {
            storeFile = file("msm-farmer.keystore")
            storePassword = "msmfarmer"
            keyAlias = "msmfarmer"
            keyPassword = "msmfarmer"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("persistent")
        }
        getByName("release") {
            signingConfig = signingConfigs.getByName("persistent")
        }
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
    testImplementation("junit:junit:4.13.2")
    val composeBom = platform("androidx.compose:compose-bom:2025.04.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
