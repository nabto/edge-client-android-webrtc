// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.jetbrainsKotlinAndroid) apply false
    id("base")
    id("maven-publish")
    id("org.jreleaser") version "1.22.0"
}

// JReleaser 1.22.0's bcpg-jdk18on calls APIs that require bcprov-jdk18on >= 1.71.
// Pin all BouncyCastle artifacts on the buildscript classpath to a single recent
// version so an older transitive bcprov can't shadow JReleaser's expectations.
buildscript {
    configurations.classpath {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.bouncycastle") {
                useVersion("1.80")
            }
        }
    }
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

