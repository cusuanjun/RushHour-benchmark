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

    config = json.loads(
        (Path(__file__).resolve().parent / "profiles" / "google.json")
        .read_text(encoding="utf-8-sig")
    )
    package = config["package"]
    gestures = config["gestures"]
    result = {
        "app": "google",
        "package": package,
        "status": "FAIL",
        "verification": "action_execution_only",
        "steps": [],
    }

    def step(name, action, interval=True):
        if interval and result["steps"] and args.interval:
            time.sleep(args.interval)

        row = {"name": name, "status": "RUNNING"}
        result["steps"].append(row)
        print("[google] %02d %s" % (len(result["steps"]), name),
              flush=True)
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
                time.monotonic() - started, 3
            )
            print("[google] %s %s in %.3fs" % (
                name, row["status"], row["elapsed_seconds"]
            ), flush=True)

    try:
        started = time.monotonic()
        d = u2.connect()
        result["connect_seconds"] = round(
            time.monotonic() - started, 3
        )
        d.implicitly_wait(0)

        if d.app_current().get("package") != package:
            raise RuntimeError("Open Google in the foreground first")

        width, height = d.window_size()

        def open_search():
            label = config["search_label"]
            targets = d.xpath(
                f"//*[@package='{package}']"
                f"[@text='{label}' or @content-desc='{label}']"
                "/ancestor-or-self::*[@clickable='true'][1]"
            )
            stop = time.monotonic() + args.timeout

            while True:
                matches = targets.all()
                if matches:
                    target = min(
                        matches,
                        key=lambda item: item.bounds[1],
                    )
                    target.click()
                    break

                if time.monotonic() >= stop:
                    raise RuntimeError(
                        "Home search box not found"
                    )
                time.sleep(0.15)

            fields = d(
                packageName=package,
                className="android.widget.EditText",
            )
            if not fields.wait(timeout=args.timeout):
                raise RuntimeError(
                    "Search input did not appear"
                )

            return {"method": "home_search_box_control"}

        def input_keyword():
            fields = d(
                packageName=package,
                className="android.widget.EditText",
            )
            focused = d(
                packageName=package,
                className="android.widget.EditText",
                focused=True,
            )
            if focused.count == 1:
                field = focused
            elif fields.count == 1:
                field = fields
            else:
                raise RuntimeError("Search input absent or ambiguous")

            field.click()
            field.set_text(config["keyword"])
            if field.get_text() != config["keyword"]:
                raise RuntimeError("Search input verification failed")
            return {"value": config["keyword"]}

        def submit_search():
            d.press("enter")
            time.sleep(gestures["results_wait"])
            return {
                "method": "enter_key",
                "results_verified": False,
            }

        def swipe(ratios):
            x1, y1, x2, y2 = ratios
            d.swipe(
                int(width * x1), int(height * y1),
                int(width * x2), int(height * y2),
                duration=gestures["duration"],
            )
            time.sleep(gestures["settle_seconds"])

        def browse_down():
            swipe(gestures["browse"])
            return {
                "direction": "finger_up",
                "scroll_verified": False,
            }

        def return_home():
            d.press("back")
            time.sleep(0.8)

            home = d(
                packageName=package,
                text="搜索",
            )

            return {
                "method": "android_back",
                "home_verified": bool(home.exists),
            }

        result["initialization_seconds"] = round(
            time.monotonic() - SCRIPT_STARTED, 3
        )
        print("[google] initialization completed in %.3fs"
              % result["initialization_seconds"], flush=True)
        step("open_search", open_search)
        step("input_keyword", input_keyword)
        step("submit_search", submit_search)
        step("browse_down_1", browse_down)
        # step("browse_down_2", browse_down)

        if args.interval:
            time.sleep(args.interval)
        remaining = max(
            0.0, SCRIPT_STARTED + args.time - time.monotonic()
        )
        result["wait_before_return_seconds"] = round(remaining, 3)
        print("[google] waiting %.3fs before returning"
              % remaining, flush=True)
        time.sleep(remaining)

        result["return_started_elapsed_seconds"] = round(
            time.monotonic() - SCRIPT_STARTED, 3
        )
        step("return_to_home", return_home, interval=False)
        result["status"] = "COMPLETED"

    except Exception as exc:
        result["error"] = "%s: %s" % (type(exc).__name__, exc)
        print("[google] FAIL: " + result["error"], flush=True)
    finally:
        result["elapsed_seconds"] = round(
            time.monotonic() - SCRIPT_STARTED, 3
        )
        print(json.dumps(result, ensure_ascii=False, indent=2))

    return 0 if result["status"] == "COMPLETED" else 1


if __name__ == "__main__":
    raise SystemExit(main())