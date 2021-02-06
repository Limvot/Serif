#!/usr/bin/env bash
./gradlew :serif_cli:assembleDist && mkdir test && cd test && tar xf ../serif_cli/build/distributions/serif_cli.tar && ./serif_cli/bin/serif_cli && cd .. && rm -r test
