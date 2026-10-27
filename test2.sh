#/bin/bash

# warm up
./gradlew run --args="-t 0 -a 2 -p warmup"

# zswap
./gradlew run --args="-t 4 -a 1 -f 1024 -m 5"
./gradlew run --args="-t 4 -a 1 -f 2048 -m 5"
./gradlew run --args="-t 4 -a 1 -f 4096 -m 5"

./gradlew run --args="-t 4 -a 1 -f 1024 -m 10"
./gradlew run --args="-t 4 -a 1 -f 2048 -m 10"
./gradlew run --args="-t 4 -a 1 -f 4096 -m 10"
