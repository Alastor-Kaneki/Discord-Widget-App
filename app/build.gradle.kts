plugins {
    id("com.android.application")
}

val discordApplicationId = providers.gradleProperty("DISCORD_APPLICATION_ID").orElse("0").get()
val socialSdkAar = file("discord_social_sdk/discord_partner_sdk.aar")
val socialSdkPresent = socialSdkAar.exists()

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
        versionCode = 2
        versionName = "0.2.0"
        manifestPlaceholders["discordApplicationId"] = discordApplicationId
        buildConfigField("String", "DISCORD_APPLICATION_ID", "\"$discordApplicationId\"")
        buildConfigField("boolean", "SOCIAL_SDK_PRESENT", socialSdkPresent.toString())
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
