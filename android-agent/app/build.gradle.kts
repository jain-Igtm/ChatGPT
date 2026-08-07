plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseStorePath = System.getenv("SMITH_KEYSTORE_PATH")
val releaseStorePassword = System.getenv("SMITH_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("SMITH_KEY_ALIAS")

android {
    namespace = "com.jane.resident"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jane.resident"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"
    }

    signingConfigs {
        if (!releaseStorePath.isNullOrBlank() &&
            !releaseStorePassword.isNullOrBlank() &&
            !releaseKeyAlias.isNullOrBlank()
        ) {
            create("smithRelease") {
                storeFile = file(releaseStorePath)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseStorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("smithRelease")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")

    testImplementation("junit:junit:4.13.2")
}
