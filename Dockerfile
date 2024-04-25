FROM ubuntu:22.04

RUN apt-get update && apt-get install -y curl unzip git openjdk-17-jdk

ENV SDK_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
ENV ANDROID_HOME="/usr/local/android-sdk"
ENV ANDROID_SDKMANAGER=$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager

RUN mkdir "$ANDROID_HOME" .android && \
    cd "$ANDROID_HOME" && \
    curl -s -o sdk.zip $SDK_URL && \
    unzip -q sdk.zip && \
    rm sdk.zip

# Google's distribution of android cmd tools comes with the wrong directory layout, WTF
RUN mv $ANDROID_HOME/cmdline-tools $ANDROID_HOME/temp
RUN mkdir $ANDROID_HOME/cmdline-tools
RUN mv $ANDROID_HOME/temp $ANDROID_HOME/cmdline-tools/latest

RUN yes | $ANDROID_SDKMANAGER --licenses
RUN $ANDROID_SDKMANAGER "tools" "platform-tools"
RUN $ANDROID_SDKMANAGER "build-tools;34.0.0"
RUN $ANDROID_SDKMANAGER "platforms;android-34"
RUN yes | $ANDROID_SDKMANAGER --licenses
