#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"

# 短测：每个模式 12 个 heavy 应用，只跑 1 轮，映射容量 1024 MB。
# 每个模式约 4 分钟负载 + 5 分钟静置 + 重启，合计预计 20–25 分钟。
# 仅用于验证配置及统计输出；内存压力不足时读写次数可能为 0。

# Flash Swap
./gradlew run --args="-t 3 -a 2 -f 1024 --rounds 1 -p quick_flash"

# zRAM + writeback，每 10 秒触发一次下刷
./gradlew run --args="-t 2 -a 2 -f 1024 -w 10 --rounds 1 -p quick_writeback"
