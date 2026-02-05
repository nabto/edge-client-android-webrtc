// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.jetbrainsKotlinAndroid) apply false
    id("base")
    id("maven-publish")
    id("org.jreleaser") version "1.22.0"
}

apply(from="$rootDir/scripts/versioning.gradle")
val buildVersionName: groovy.lang.Closure<String> by extra

rootProject.extra.apply {
    set("JRELEASER_GROUP_ID", "com.nabto.edge.client")
}
apply(from="$rootDir/scripts/jreleaser.gradle")

tasks.register("showVersion") {
    doLast {
        println(rootProject.extra["buildVersionName"])
    }
}

