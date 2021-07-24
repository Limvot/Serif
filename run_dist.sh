#!/usr/bin/env bash
export TERM=cygwin
target=serif_swing
tar_loc=../$target/build/distributions/$target.tar
./gradlew --console=plain :$target:assembleDist && mkdir -p test && cd test && tar xf $tar_loc && ./$target/bin/$target && cd .. && rm -r test
