#!/bin/bash

cd "$(dirname "$0")/.."
mkdir -p build
(cd build && cmake .. -DCMAKE_BUILD_TYPE=Debug -DBUILD_TESTS=On && cmake --build . --config Debug -j $(nproc))
ctest --test-dir build/tests --output-on-failure
