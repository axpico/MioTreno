plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "it.picone.miotreno"
    compileSdk = 36

    defaultConfig {
        applicationId = "it.picone.miotreno"
        minSdk = 26
        targetSdk = 36
        // in CI la release le passa dal tag (v1.2.0 → 1.2.0) e dal numero di run; in locale 1 / "1.0"
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("versionName") as String?) ?: "1.0"
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
            // M3 Expressive è ancora sperimentale: opt-in una volta qui invece che in ogni file
            freeCompilerArgs.addAll(
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            )
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
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.navigation.suite)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
}

android.lint {
    // Aggiornamenti di dipendenze, AGP e targetSdk 37: le versioni nuove di Compose, AndroidX
    // e AGP richiedono AGP 9.1 + compileSdk 37 (vedi README, Versioni). Finché non si migra
    // sono avvisi senza azione possibile, e coprivano quelli veri.
    disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
    warningsAsErrors = true
}

ktlint {
    version.set("1.3.1")
    android.set(true)
    // i file generati non sono nostri e non devono far fallire la build
    filter { exclude("**/generated/**") }
}
