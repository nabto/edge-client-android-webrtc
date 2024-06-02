// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.jetbrainsKotlinAndroid) apply false
}

apply(from = "$rootDir/scripts/versioning.gradle.kts")

tasks.register("showVersion") {
    doLast {
        println(rootProject.extra["buildVersionName"])
    }
}

rootProject.extra.apply {
    set("ossrhUsername",    System.getenv("OSSRH_USERNAME")     ?: "")
    set("ossrhPassword",    System.getenv("OSSRH_PASSWORD")     ?: "")
    set("signingKeyBase64", System.getenv("SIGNING_KEY_BASE64") ?: "")
    set("signingPassword",  System.getenv("SIGNING_PASSWORD")   ?: "")
}
