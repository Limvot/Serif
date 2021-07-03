#!/usr/bin/env bash
target=serif_swing
tar_loc=../$target/build/distributions/$target.tar
./gradlew :$target:assembleDist && mkdir -p test && cd test && tar xf $tar_loc && ./$target/bin/$target && cd .. && rm -r test
