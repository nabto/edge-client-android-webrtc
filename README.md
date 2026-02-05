# Nabto Edge WebRTC Android Library

This library is used to implement Nabto Edge WebRTC in an Android app. This library is used to implement the demo app on [Google Play](https://play.google.com/store/apps/details?id=com.nabto.edge.webrtcdemo) and [GitHub](https://github.com/nabto/edge-android-webrtc).

The following modules are included

* webrtc - the core library
* webrtc-demo - a small test application in kotlin that uses the library
* webrtc-demo-java - same as above but in java

## Building and creating staging artifacts

To build run the following

```shell
./gradlew build
```

To create staging artifacts run the following command, the artifacts will be saved to `webrtc/build/staging-deploy`. These artifacts are not signed and cannot be uploaded to maven central. See the jreleaser section below for info on deploying.

```shell
./gradlew
```

# Deploying to Maven Central with JReleaser

This repository uses JReleaser to upload artifacts to Maven Central. This happens in two steps

1. The publish step run `scripts/publish.gradle` in the `:webrtc` module to store staging artifacts in the build directory.
2. JReleaser (configured in `scripts/jreleaser.gradle`) deploys the staging artifacts to Maven Central.

## Gradle properties for deployment

The following gradle properties must be set to allow JReleaser to publish artifacts.

* `GPG_SIGNING_KEY_BASE64` Base64 encoded armored PGP private key
* `GPG_SIGNING_PUBLIC_KEY_BASE64` Base64 encoded armored PGP public key
* `GPG_SIGNING_PASSWORD` Passphrase for the above keypair
* `OSSRH_USERNAME` **IMPORTANT** this is no longer the OSSRH username but actually the username of a token generated in the account options in https://central.sonatype.com/
* `OSSRH_PASSWORD` Similar to above, this is the password of that same token.

Note that the signing keys are **armored and base64 encoded**, so they are actually twice-encoded. This is to get around a Jenkins limitation where secrets cannot have newlines. To output these keys from `gpg` you can use the following commands
```
gpg --armor --export <KEYID> | base64 -w 0
gpg --armor --export-secret-keys <KEYID> | base64 -w 0
```

## Publishing staging artifacts

```
./gradlew publish
```

Running publish will no longer attempt to deploy to maven. Instead it will store staging artifacts into the `build/staging-deploy` directory.

## Deploying

```
./gradlew jreleaserDeploy
```
Running this command will deploy the files in `build/staging-deploy` to Maven Central.

If the current Git commit is tagged with a version it will be uploaded but not published. You must manually go to https://central.sonatype.com/ and publish the staged artifacts.

If there is no tag JReleaser will only deploy a snapshot.
