#!/bin/bash

# This should be set to the commit hash that is being tracked. Needed even if TRACKING_PR is set.
TRACKING_COMMIT="6387275"
# To track a particular pull request, put it's number here, otherwise comment it out.
# TRACKING_PR="84"

set -e

cd "$( dirname "${BASH_SOURCE[0]}" )/.."
mkdir -p target
cd target

if [[ -d microprofile-reactive ]]; then
    cd microprofile-reactive
    git fetch
else
    git clone https://github.com/eclipse/microprofile-reactive.git
    cd microprofile-reactive
fi

if [[ -n ${TRACKING_PR+x} ]]; then
    git fetch origin "pull/${TRACKING_PR}/head"
fi

git checkout "${TRACKING_COMMIT}"

mvn -am -pl streams/api,streams/tck,streams/tck-arquillian clean install -Dmaven.test.skip -Drat.skip=true -Dcheckstyle.skip=true -Dmaven.javadoc.skip=true -Dasciidoctor.skip=true
