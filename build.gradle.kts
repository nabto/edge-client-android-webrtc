// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.jetbrainsKotlinAndroid) apply false
    id("base")
    id("maven-publish")
    id("org.jreleaser") version "1.22.0"
}

// AGP brings BouncyCastle's legacy -jdk15on flavor (last release 1.70) while
// JReleaser 1.22.0 brings -jdk18on. Both share the org.bouncycastle.* packages,
// so the older -jdk15on classes can shadow APIs JReleaser needs and cause a
// NoSuchMethodError on BigIntegers.writeUnsignedByteArray. Substitute -jdk15on
// with -jdk18on (drop-in on JDK 8+) and pin everything to one version.
buildscript {
    configurations.classpath {
        resolutionStrategy.dependencySubstitution {
            substitute(module("org.bouncycastle:bcprov-jdk15on"))
                .using(module("org.bouncycastle:bcprov-jdk18on:1.80"))
            substitute(module("org.bouncycastle:bcpkix-jdk15on"))
                .using(module("org.bouncycastle:bcpkix-jdk18on:1.80"))
        }
        resolutionStrategy.eachDependency {
            if (requested.group == "org.bouncycastle" && requested.name.endsWith("-jdk18on")) {
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

