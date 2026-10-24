import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    // Screenshot tests (JVM/layoutlib, no emulator): ./BUILD.sh :app-customer:recordPaparazziDebug
    id("app.cash.paparazzi") version "2.0.0-alpha02"
}

// Firebase is optional: the plugin is applied only when google-services.json is present (see README).
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun prop(name: String, default: String): String =
    (project.findProperty(name) as String?) ?: localProps.getProperty(name) ?: System.getenv(name) ?: default

android {
    namespace = "in.localrush.customer"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "in.localrush.customer"
        minSdk = 24
        targetSdk = 35
        versionCode = prop("LOCALRUSH_VERSION_CODE", "1").toInt()
        versionName = prop("LOCALRUSH_VERSION_NAME", "0.1.0")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        resourceConfigurations += listOf("en", "hi")
    }

    signingConfigs {
        create("release") {
            val ks = prop("LOCALRUSH_KEYSTORE_FILE", "")
            if (ks.isNotBlank() && file(ks).exists()) {
                storeFile = file(ks)
                storePassword = prop("LOCALRUSH_KEYSTORE_PASSWORD", "")
                keyAlias = prop("LOCALRUSH_KEY_ALIAS", "localrush")
                keyPassword = prop("LOCALRUSH_KEY_PASSWORD", "")
            }
        }
    }

    buildTypes {
        debug {
            versionNameSuffix = "-debug"
            isDebuggable = true
            // Android emulator -> host machine. Use http://<LAN-IP>:8001/api/v1/ for a physical device (add it to
            // src/debug/res/xml/network_security_config.xml too).
            // Debug honours LOCALRUSH_DEBUG_* first, then the generic LOCALRUSH_API_BASE_URL / LOCALRUSH_WS_BASE_URL
            // (forwarded by BUILD.sh), so a physical phone can point at e.g. http://50.50.50.14:8001/api/v1/.
            buildConfigField("String", "API_BASE_URL", "\"${prop("LOCALRUSH_DEBUG_API_BASE_URL", prop("LOCALRUSH_API_BASE_URL", "http://10.0.2.2:8001/api/v1/"))}\"")
            buildConfigField("String", "WS_BASE_URL", "\"${prop("LOCALRUSH_DEBUG_WS_BASE_URL", prop("LOCALRUSH_WS_BASE_URL", "ws://10.0.2.2:8001/ws/"))}\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) signingConfig = releaseSigning
            buildConfigField("String", "API_BASE_URL", "\"${prop("LOCALRUSH_API_BASE_URL", "https://api.localrush.in/api/v1/")}\"")
            buildConfigField("String", "WS_BASE_URL", "\"${prop("LOCALRUSH_WS_BASE_URL", "wss://api.localrush.in/ws/")}\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions.unitTests.isReturnDefaultValues = true
    packaging {
        resources.excludes += setOf("META-INF/LICENSE*", "META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/*.kotlin_module")
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:auth"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:push"))
    implementation(project(":core:database"))
    implementation(project(":feature:auth"))
    implementation(project(":core:location"))
    implementation(project(":core:maps"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.splashscreen)
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.lifecycle.process)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    implementation(libs.coil.compose)
    implementation(libs.datastore.preferences)
    // Not in the shared version catalog (customer-app only): Razorpay Checkout SDK.
    implementation("com.razorpay:checkout:1.6.40")

    testImplementation(libs.junit)
    // Screenshot tests render the real screens against mocked ViewModels (see src/test/.../screenshots).
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
