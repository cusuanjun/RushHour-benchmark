#/bin/bash

# warmup
./gradlew run --args="-t 0 -a 2 -p warmup"

# no flash
./gradlew run --args="-t 0 -a 1"

# zram
./gradlew run --args="-t 1 -a 1"

# zram+writeback
./gradlew run --args="-t 2 -a 1 -f 1024 -w 10"
./gradlew run --args="-t 2 -a 1 -f 2048 -w 10"
./gradlew run --args="-t 2 -a 1 -f 4096 -w 10"

./gradlew run --args="-t 2 -a 1 -f 1024 -w 30"
./gradlew run --args="-t 2 -a 1 -f 2048 -w 30"
./gradlew run --args="-t 2 -a 1 -f 4096 -w 30"

./gradlew run --args="-t 2 -a 1 -f 1024 -w 60"
./gradlew run --args="-t 2 -a 1 -f 2048 -w 60"
./gradlew run --args="-t 2 -a 1 -f 4096 -w 60"

# flash swap
./gradlew run --args="-t 3 -a 1 -f 1024"
./gradlew run --args="-t 3 -a 1 -f 2048"
./gradlew run --args="-t 3 -a 1 -f 4096"

# zswap
./gradlew run --args="-t 4 -a 1 -f 1024 -m 5"
./gradlew run --args="-t 4 -a 1 -f 2048 -m 5"
./gradlew run --args="-t 4 -a 1 -f 4096 -m 5"

./gradlew run --args="-t 4 -a 1 -f 1024 -m 10"
./gradlew run --args="-t 4 -a 1 -f 2048 -m 10"
./gradlew run --args="-t 4 -a 1 -f 4096 -m 10"
