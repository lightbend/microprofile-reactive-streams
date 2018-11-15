#!/bin/bash

# This should be set to the commit hash that is being tracked. Needed even if TRACKING_PR is set.
TRACKING_COMMIT="1468891"
# To track a particular pull request, put it's number here, otherwise comment it out.
TRACKING_PR="107"

set -e

cd "$( dirname "${BASH_SOURCE[0]}" )/.."
mkdir -p target
cd target

if [[ -d microprofile-reactive-streams ]]; then
    cd microprofile-reactive-streams
    git fetch
else
    git clone https://github.com/eclipse/microprofile-reactive-streams.git
    cd microprofile-reactive-streams
fi

if [[ -n ${TRACKING_PR+x} ]]; then
    git fetch origin "pull/${TRACKING_PR}/head"
fi

git checkout "${TRACKING_COMMIT}"

mvn clean install -Dmaven.test.skip -Drat.skip=true -Dcheckstyle.skip=true -Dmaven.javadoc.skip=true -Dasciidoctor.skip=true
