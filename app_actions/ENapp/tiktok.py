import time

SCRIPT_STARTED = time.monotonic()

import argparse
import json
import math
from pathlib import Path

import uiautomator2 as u2


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--time", type=float, default=10)
    parser.add_argument("--interval", type=float, default=0.1)
    parser.add_argument("--timeout", type=float, default=5)
    args = parser.parse_args()

    if not all(math.isfinite(v) for v in vars(args).values()):
        parser.error("Timing values must be finite")

    if args.time < 0 or args.interval < 0 or args.timeout <= 0:
        parser.error("--time/--interval >= 0; --timeout > 0")

    base_dir = Path(__file__).resolve().parent
    profile_path = base_dir / "profiles" / "tiktok.json"
    config = json.loads(
        profile_path.read_text(encoding="utf-8-sig")
    )

    package = config["package"]
    gestures = config["gestures"]
    result = {
        "app": config["app"],
        "package": package,
        "status": "FAIL",
        "verification": "gesture_execution_only",
        "steps": [],
    }

    deadline = SCRIPT_STARTED + args.time
    device = None

    def run_step(name, action, apply_interval=True):
        if apply_interval and result["steps"] and args.interval:
            time.sleep(args.interval)

        row = {
            "name": name,
            "status": "RUNNING",
        }
        result["steps"].append(row)

        print(
            "[tiktok] %02d %s"
            % (len(result["steps"]), name),
            flush=True,
        )

        started = time.monotonic()

        try:
            row["detail"] = action()
            row["status"] = "PASS"
        except Exception as exc:
            row.update(
                status="FAIL",
                error="%s: %s" % (type(exc).__name__, exc),
            )
            raise
        finally:
            row["elapsed_seconds"] = round(
                time.monotonic() - started,
                3,
            )
            print(
                "[tiktok] %s %s in %.3fs"
                % (
                    name,
                    row["status"],
                    row["elapsed_seconds"],
                ),
                flush=True,
            )

    def find_feed():
        controls = device(
            packageName=package,
            scrollable=True,
        )

        if controls.count == 0:
            raise RuntimeError(
                "No scrollable TikTok feed control found"
            )

        candidates = []

        for index in range(controls.count):
            item = controls[index]
            info = item.info
            bounds = (
                info.get("visibleBounds")
                or info.get("bounds")
            )

            if not bounds:
                continue

            width = bounds["right"] - bounds["left"]
            height = bounds["bottom"] - bounds["top"]

            if width > 0 and height > 0:
                candidates.append(
                    (width * height, item)
                )

        if not candidates:
            raise RuntimeError(
                "TikTok scrollable control has no visible bounds"
            )

        return max(
            candidates,
            key=lambda item: item[0],
        )[1]

    def scroll_next_video():
        feed = find_feed()

        feed.swipe(
            "up",
            steps=gestures["scroll_steps"],
        )

        time.sleep(
            gestures["settle_seconds"]
        )

        return {
            "method": "native_scrollable_control",
            "direction": "up",
            "scroll_verified": False,
        }

    try:
        connect_started = time.monotonic()
        device = u2.connect()

        result["connect_seconds"] = round(
            time.monotonic() - connect_started,
            3,
        )

        device.implicitly_wait(0)

        current_package = device.app_current().get("package")
        allowed_packages = config.get(
            "packages",
            [config["package"]],
        )

        if current_package not in allowed_packages:
            raise RuntimeError(
                "Unexpected foreground package: %s; expected one of %s"
                % (current_package, allowed_packages)
            )

        package = current_package
        result["package"] = package

        result["initialization_seconds"] = round(
            time.monotonic() - SCRIPT_STARTED,
            3,
        )

        print(
            "[tiktok] initialization completed in %.3fs"
            % result["initialization_seconds"],
            flush=True,
        )

        # 第一次上滑，浏览下一条视频。
        run_step(
            "scroll_next_video_1",
            scroll_next_video,
        )

        # 第一次滑动后观看 2 秒。
        remaining = max(
            0.0,
            deadline - time.monotonic(),
        )
        watch_seconds = min(
            gestures["watch_seconds"],
            remaining,
        )

        result["first_watch_seconds"] = round(
            watch_seconds,
            2,
        )

        print(
            "[tiktok] watching for %.3fs"
            % watch_seconds,
            flush=True,
        )

        time.sleep(watch_seconds)

        # 如果时间还没到，再滑动一次。
        remaining = deadline - time.monotonic()

        if remaining > 0:
            run_step(
                "scroll_next_video_2",
                scroll_next_video,
                apply_interval=False,
            )

        # 第二次滑动后一直停留到总时间结束。
        remaining = max(
            0.0,
            deadline - time.monotonic(),
        )

        result["wait_before_finish_seconds"] = round(
            remaining,
            3,
        )

        if remaining:
            print(
                "[tiktok] waiting %.3fs until time limit"
                % remaining,
                flush=True,
            )
            time.sleep(remaining)

        result["time_reached_elapsed_seconds"] = round(
            time.monotonic() - SCRIPT_STARTED,
            3,
        )
        result["time_budget_met"] = (
            result["time_reached_elapsed_seconds"]
            >= args.time
        )
        result["status"] = "PASS"

    except Exception as exc:
        result["error"] = "%s: %s" % (
            type(exc).__name__,
            exc,
        )
        print(
            "[tiktok] FAIL: " + result["error"],
            flush=True,
        )

    finally:
        result["elapsed_seconds"] = round(
            time.monotonic() - SCRIPT_STARTED,
            3,
        )
        print(
            json.dumps(
                result,
                ensure_ascii=False,
                indent=2,
            )
        )

    return 0 if result["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())