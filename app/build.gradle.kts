plugins {
    id("com.android.application")
}

val discordApplicationId = providers.gradleProperty("DISCORD_APPLICATION_ID").orElse("0").get()
val socialSdkAar = file("discord_social_sdk/discord_partner_sdk.aar")
val socialSdkPresent = socialSdkAar.exists()
val signingStorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
val signingStorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
val signingKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
val signingKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
val stableSigningConfigured = listOf(
    signingStorePath,
    signingStorePassword,
    signingKeyAlias,
    signingKeyPassword
).all { !it.isNullOrBlank() }

configurations.configureEach {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:1.8.22")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22")
    }
}

android {
    namespace = "com.alastorkaneki.discordwidget"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.alastorkaneki.discordwidget"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.4.0"
        manifestPlaceholders["discordApplicationId"] = discordApplicationId
        buildConfigField("String", "DISCORD_APPLICATION_ID", "\"$discordApplicationId\"")
        buildConfigField("boolean", "SOCIAL_SDK_PRESENT", socialSdkPresent.toString())
    }

    signingConfigs {
        if (stableSigningConfigured) {
            create("stable") {
                storeFile = file(signingStorePath!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (stableSigningConfigured) {
                signingConfig = signingConfigs.getByName("stable")
            }
        }
        getByName("release") {
            isMinifyEnabled = false
            if (stableSigningConfigured) {
                signingConfig = signingConfigs.getByName("stable")
            }
        }
    }

    buildFeatures {
        buildConfig = true
        prefab = socialSdkPresent
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    if (socialSdkPresent) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
        defaultConfig {
            externalNativeBuild {
                cmake {
                    cppFlags += "-std=c++17"
                }
            }
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
            }
        }
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.browser:browser:1.8.0")
    if (socialSdkPresent) {
        implementation(files(socialSdkAar))
    }
}
