
plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)

}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.notifier.nxytw"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // ----------------------------------------------------------------
    // Supabase credentials -- injected as BuildConfig fields.
    //
    // We deliberately do NOT use the secrets-gradle-plugin here.
    // That plugin parses .env using java.util.Properties.load(), which
    // uses ISO-8859-1 and has been observed to corrupt URLs (e.g.
    // turning a regular 'u' into 'ú' = byte 0xFA), producing
    // "HTTP request to https://hhwevlstzúvjpewkaiz.supabase.co..."
    // errors at runtime.
    //
    // Instead we read directly from environment variables (set by the
    // GitHub Actions workflow) or fall back to hardcoded defaults that
    // are verified to work. The SUPABASE_KEY is the anon public key --
    // it ships inside every APK we publish, so anyone can extract it
    // anyway. RLS policies (supabase_setup.sql) protect the data.
    // The service_role key must NEVER be put here.
    // ----------------------------------------------------------------
    buildConfigField(
      "String",
      "SUPABASE_URL",
      "\"" + (System.getenv("SUPABASE_URL") ?: "https://hhwwevlstzuvjpewkaiz.supabase.co") + "\""
    )
    buildConfigField(
      "String",
      "SUPABASE_KEY",
      "\"" + (System.getenv("SUPABASE_KEY") ?: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imhod3dldmxzdHp1dmpwZXdrYWl6Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODM2MDI4OTQsImV4cCI6MjA5OTE3ODg5NH0.Jpjy0gN_PiKGFmuJUbMBTMrYGECStXoKKz4YmVmLib0") + "\""
    )
    buildConfigField(
      "String",
      "GEMINI_API_KEY",
      "\"" + (System.getenv("GEMINI_API_KEY") ?: "placeholder_api_key") + "\""
    )
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// NOTE: The secrets-gradle-plugin has been removed entirely.
// BuildConfig fields are now declared explicitly in defaultConfig
// above, reading from environment variables with hardcoded fallbacks.
// This avoids the Properties.load() ISO-8859-1 encoding bug that was
// corrupting the Supabase URL (u -> ú).


// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  
  implementation(libs.supabase.postgrest)
  implementation(libs.supabase.gotrue)
  implementation(libs.supabase.storage)
  implementation(libs.ktor.client.android)
  
  
  
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  // implementation(libs.coil.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.play.services.location)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
