plugins {
    id("com.android.application")
}

val discordApplicationId = providers.gradleProperty("DISCORD_APPLICATION_ID").orElse("0").get()
val socialSdkRoot = file("discord_social_sdk")
val socialSdkPresent = file("discord_social_sdk/include/discordpp.h").exists()

android {
    namespace = "com.alastorkaneki.discordwidget"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.alastorkaneki.discordwidget"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        manifestPlaceholders["discordApplicationId"] = discordApplicationId
        buildConfigField("String", "DISCORD_APPLICATION_ID", "\"$discordApplicationId\"")
        buildConfigField("boolean", "SOCIAL_SDK_PRESENT", socialSdkPresent.toString())
    }

    buildFeatures {
        buildConfig = true
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
                    arguments += "-DDISCORD_SDK_ROOT=${socialSdkRoot.absolutePath}"
                    cppFlags += "-std=c++17"
                }
            }
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
            }
        }
        sourceSets.getByName("main").jniLibs.srcDir("discord_social_sdk/lib")
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.browser:browser:1.8.0")
}
