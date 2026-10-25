#!/usr/bin/env bash
# Run Windows UI automation from a WSL2 scheduler.
set -euo pipefail
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
python_exe="${APP_ACTION_PYTHON:-/mnt/c/Users/仲昭阳/AppData/Local/Programs/Python/Python312/python.exe}"
if [[ ! -f "$python_exe" ]]; then
    printf '%s\n' "Set APP_ACTION_PYTHON to the WSL path of Windows Python with uiautomator2." >&2
    exit 1
fi
windows_script="$(wslpath -w "$script_dir/run_app.py")"
exec "$python_exe" "$windows_script" "$@"
