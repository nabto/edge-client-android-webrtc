#!/bin/bash

SCRIPT_DIR="$( cd -P "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd SCRIPT_DIR/..

IMAGE_NAME=nabto-android-webrtc-ci

help() {
    echo "Usage: $0 [build|publish]"
}

build() {
    docker run --rm \
        --volume=".:/sandbox" \
        --workdir="/sandbox" \
        $IMAGE_NAME "scripts/build.sh"
}

publish() {
    docker run --rm \
    --volume=".:/sandbox" \
    --workdir="/sandbox" \
    $IMAGE_NAME "scripts/publish.sh"
}

docker build -t $IMAGE_NAME .

case $1 in
    "build") build ;;
    "publish") publish ;;
    *) help ;;
esac
