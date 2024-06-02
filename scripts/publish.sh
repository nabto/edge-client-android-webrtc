#!/bin/bash

git diff --quiet && clean=true || clean=false

if [ "$clean" = true ] && [ ! -z "$(git describe --exact-match --tags 2>/dev/null)" ];
then
    echo "Releasing to maven central..."
else
   echo "Publishing to sonatype staging repository without releasing..."
fi

./gradlew publish
