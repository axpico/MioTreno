plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "it.picone.miotreno"
    compileSdk = 36

    defaultConfig {
        applicationId = "it.picone.miotreno"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    // Chiave di firma letta da env/gradle.properties, mai committata: se manca, il release
    // resta semplicemente non firmato (comportamento attuale) invece di rompere la build.
    // Vedi RELEASE.md per come valorizzarle.
    fun releaseSigningProperty(name: String): String? = System.getenv(name) ?: findProperty(name) as String?

    signingConfigs {
        create("release") {
            releaseSigningProperty("MIOTRENO_KEYSTORE_PATH")?.let { storeFile = file(it) }
            storePassword = releaseSigningProperty("MIOTRENO_KEYSTORE_PASSWORD")
            keyAlias = releaseSigningProperty("MIOTRENO_KEY_ALIAS")
            keyPassword = releaseSigningProperty("MIOTRENO_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets["main"].kotlin.srcDir("src/main/kotlin")
    sourceSets["test"].kotlin.srcDir("src/test/kotlin")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
}
