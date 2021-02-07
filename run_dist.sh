#!/usr/bin/env bash

#ensure test directory exists
if [ ! -d ./test ]; then
    mkdir test
fi
./gradlew :serif_cli:assembleDist && cd test && tar xf ../serif_cli/build/distributions/serif_cli.tar && ./serif_cli/bin/serif_cli && cd .. && rm -r test
