#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
test_output="$(mktemp -d)"
trap 'rm -rf "$test_output"' EXIT HUP INT TERM
java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -d "$test_output" \
    app/src/main/java/fr/bonobo/stopdemarchage/FilterEngine.java app/src/main/java/fr/bonobo/stopdemarchage/CommunityPolicy.java tests/FilterEngineTest.java
java -cp "$test_output" fr.bonobo.stopdemarchage.FilterEngineTest
