plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.alastor.discordrelicvault"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.alastor.discordrelicvault"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "Nitro Ate The Asset Manifest"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17) }
