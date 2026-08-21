plugins {
    alias(libs.plugins.androidLibrary)
}

rootProject.extra.apply {
    set("POM_GROUP", "com.nabto.edge.client")
    set("POM_ARTIFACT_ID", "webrtc")
}

apply(from ="$rootDir/scripts/publish.gradle")

android {
    namespace = "com.nabto.edge.client.webrtc"
    compileSdk = 37

    defaultConfig {
        namespace = "com.nabto.edge.client.webrtc"
        minSdk = 24
        testOptions.targetSdk = 36
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }

    packaging {
        resources.excludes.apply {
            add("META-INF/LICENSE.md")
            add("META-INF/LICENSE-notice.md")
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
    testImplementation(libs.mockk)
    testImplementation(libs.mockk.android)
    testImplementation(libs.mockk.agent)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.core.ktx)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.androidx.rules)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.mockk.agent)
}
