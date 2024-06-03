plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsKotlinAndroid)
}

rootProject.extra.apply {
    set("POM_GROUP_ID", "com.nabto.edge.client")
    set("POM_ARTIFACT_ID", "webrtc")
    set("POM_VERSION", rootProject.extra["buildVersionName"])
}

apply(from ="$rootDir/scripts/publish.gradle")

android {
    namespace = "com.nabto.edge.client.webrtc"
    compileSdk = 34

    defaultConfig {
        namespace = "com.nabto.edge.client.webrtc"
        minSdk = 24
        testOptions.targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.startup.runtime)

    implementation(libs.nabto.library)
    implementation(libs.nabto.library.ktx)

    implementation(libs.bundles.jackson)
    api(libs.stream.webrtc.android)
    api(libs.stream.webrtc.android.ui)
    implementation(libs.stream.webrtc.android.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.core.ktx)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.androidx.rules)
}
