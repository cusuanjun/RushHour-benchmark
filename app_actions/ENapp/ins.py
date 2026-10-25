import time

SCRIPT_STARTED = time.monotonic()

import argparse
import json
import math
from pathlib import Path

import uiautomator2 as u2


BASE_DIR = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--time", type=float, default=10)
    parser.add_argument("--interval", type=float, default=0.1)
    parser.add_argument("--timeout", type=float, default=10)
    args = parser.parse_args()

    values = (args.time, args.interval, args.timeout)
    if not all(math.isfinite(v) for v in values):
        parser.error("Timing values must be finite")
    if args.time < 0 or args.interval < 0 or args.timeout <= 0:
        parser.error("--time/--interval >= 0; --timeout > 0")

    config = json.loads(
        (BASE_DIR / "profiles" / "ins.json")
        .read_text(encoding="utf-8-sig")
    )
    package = config["package"]
    gestures = config["gestures"]

    result = {
        "app": "ins",
        "package": package,
        "status": "FAIL",
        "verification": "gesture_execution_only",
        "steps": [],
    }

    row = None
    step_started = None

    try:
        connect_started = time.monotonic()
        d = u2.connect()
        result["connect_seconds"] = round(
            time.monotonic() - connect_started, 3
        )

        if d.app_current().get("package") != package:
            raise RuntimeError(
                "Open Instagram Home before running"
            )

        width, height = d.window_size()

        def double_tap():
            width, height = d.window_size()
            d.double_click(
                width // 2,
                height // 2,
                duration=gestures["double_tap_interval"],
            )
            time.sleep(gestures["settle_seconds"])

            return {
                "method": "screen_center_double_tap",
                "like_verified": False,
            }

        def swipe(key):
            feed = d(
                packageName=package,
                **config["selectors"]["feed"],
            )

            if feed.count != 1:
                raise RuntimeError(
                    "Feed container is absent or ambiguous"
                )

            info = feed.info
            box = info.get("visibleBounds") or info["bounds"]

            x = (box["left"] + box["right"]) // 2
            margin = max(8, (box["bottom"] - box["top"]) // 20)
            top = box["top"] + margin
            bottom = box["bottom"] - margin

            if bottom <= top:
                raise RuntimeError("Feed has no usable gesture area")

            if key == "browse_down":
                start_y, end_y = bottom, top
            else:
                start_y, end_y = top, bottom

            d.swipe(x, start_y, x, end_y, duration=0.5)
            time.sleep(gestures["settle_seconds"])

            return {
                "method": "gesture_within_feed_bounds",
                "action": key,
                "gesture_sent": True,
                "scroll_verified": False,
            }

        result["initialization_seconds"] = round(
            time.monotonic() - SCRIPT_STARTED, 3
        )
        print(
            "[ins] initialization completed in %.3fs"
            % result["initialization_seconds"],
            flush=True,
        )

        steps = [
            ("double_tap_1", double_tap),
            ("browse_down_1", lambda: swipe("browse_down")),
            ("double_tap_2", double_tap),
            ("browse_down_2", lambda: swipe("browse_down")),
            ("double_tap_3", double_tap),
            ("browse_up_1", lambda: swipe("browse_up")),
            ("browse_up_2", lambda: swipe("browse_up")),
        ]

        for index, (name, action) in enumerate(steps, 1):
            if index > 1 and args.interval:
                time.sleep(args.interval)

            if name == "browse_up_1":
                remaining = max(
                    0.0,
                    SCRIPT_STARTED + args.time - time.monotonic(),
                )
                result["wait_before_return_seconds"] = round(
                    remaining, 3
                )

                if remaining:
                    print(
                        "[ins] waiting %.3fs before scrolling back"
                        % remaining,
                        flush=True,
                    )
                    time.sleep(remaining)

                result["return_started_elapsed_seconds"] = round(
                    time.monotonic() - SCRIPT_STARTED, 3
                )

            row = {"name": name, "status": "RUNNING"}
            result["steps"].append(row)

            print("[ins] %02d %s" % (index, name), flush=True)

            step_started = time.monotonic()
            detail = action()

            row.update(
                status="PASS",
                elapsed_seconds=round(
                    time.monotonic() - step_started, 3
                ),
                detail=detail,
            )

            print(
                "[ins] %s completed in %.3fs"
                % (name, row["elapsed_seconds"]),
                flush=True,
            )
            step_started = None

        result["status"] = "PASS"

    except Exception as exc:
        error = "%s: %s" % (type(exc).__name__, exc)
        result["error"] = error

        if row is not None and row["status"] == "RUNNING":
            row.update(
                status="FAIL",
                error=error,
                elapsed_seconds=round(
                    time.monotonic() - step_started, 3
                ),
            )
            print(
                "[ins] %s failed in %.3fs: %s"
                % (row["name"], row["elapsed_seconds"], error),
                flush=True,
            )
        else:
            print("[ins] FAIL: " + error, flush=True)

    finally:
        result["elapsed_seconds"] = round(
            time.monotonic() - SCRIPT_STARTED, 3
        )
        print(json.dumps(result, ensure_ascii=False, indent=2))

    return 0 if result["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())