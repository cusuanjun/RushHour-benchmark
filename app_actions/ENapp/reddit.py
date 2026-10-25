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
        (Path(__file__).resolve().parent / "profiles" / "reddit.json")
        .read_text(encoding="utf-8-sig")
    )
    package = config["package"]
    gestures = config["gestures"]
    deadline = SCRIPT_STARTED + args.time
    result = {
        "app": "reddit",
        "package": package,
        "status": "FAIL",
        "verification": "action_execution_only",
        "steps": [],
    }

    def run_step(name, action, interval=True):
        if interval and result["steps"] and args.interval:
            time.sleep(args.interval)
        row = {"name": name, "status": "RUNNING"}
        result["steps"].append(row)
        print("[reddit] %02d %s" % (len(result["steps"]), name),
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
            print("[reddit] %s %s in %.3fs" % (
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
            raise RuntimeError("Open Reddit in the foreground first")
        width, height = d.window_size()

        def find_search():
            pattern = config["search_pattern"]
            for rule in [
                {"textMatches": pattern},
                {"descriptionMatches": pattern},
            ]:
                target = d(packageName=package, **rule)
                if target.count == 1:
                    return target
            return None

        def open_search():
            stop = time.monotonic() + args.timeout
            while True:
                target = find_search()
                if target is not None:
                    target.click(timeout=0.5)
                    break
                if time.monotonic() >= stop:
                    raise RuntimeError("Search entry not found")
                time.sleep(0.15)

            field = d(
                packageName=package,
                className="android.widget.EditText",
            )
            if not field.wait(timeout=args.timeout):
                raise RuntimeError("Search input did not appear")
            return {"method": "search_control"}

        def input_keyword():
            fields = d(
                packageName=package,
                className="android.widget.EditText",
            )
            if fields.count != 1:
                raise RuntimeError("Search input absent or ambiguous")
            fields.set_text(config["keyword"])
            if fields.get_text() != config["keyword"]:
                raise RuntimeError("Search input verification failed")
            return {"value": config["keyword"]}

        def submit_search():
            x_ratio, y_ratio = 0.93, 0.955
            d.click(
                int(width * x_ratio),
                int(height * y_ratio),
            )
            time.sleep(gestures["results_settle"])
            return {
                "method": "keyboard_search_ratio_click",
                "x_ratio": x_ratio,
                "y_ratio": y_ratio,
                "results_verified": False,
            }

        def open_result():
            x, y = gestures["open_result"]
            # PIXEL_ADAPTATION: user-requested proportional click.
            # Verify that this position contains a result on each device.
            d.click(int(width * x), int(height * y))
            return {
                "method": "screen_ratio",
                "x_ratio": x,
                "y_ratio": y,
                "detail_page_verified": False,
            }

        def read_until_time():
            count = 0
            duration = gestures["swipe_duration"]
            while True:
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    break
                if remaining <= duration + 0.2:
                    time.sleep(remaining)
                    break

                x1, y1, x2, y2 = gestures["read_swipe"]
                d.swipe(
                    int(width * x1), int(height * y1),
                    int(width * x2), int(height * y2),
                    duration=duration,
                )
                count += 1
                time.sleep(min(
                    gestures["read_pause"],
                    max(0.0, deadline - time.monotonic()),
                ))
            return {"swipe_count": count, "scroll_verified": False}

        def back():
            d.press("back")
            return {"method": "android_back"}

        result["initialization_seconds"] = round(
            time.monotonic() - SCRIPT_STARTED, 3
        )
        print("[reddit] initialization completed in %.3fs"
              % result["initialization_seconds"], flush=True)

        run_step("open_search", open_search)
        run_step("input_keyword", input_keyword)
        run_step("submit_search", submit_search)
        run_step("open_result", open_result)
        run_step("read_until_time", read_until_time, interval=False)

        result["return_started_elapsed_seconds"] = round(
            time.monotonic() - SCRIPT_STARTED, 3
        )
        run_step("return_to_results", back, interval=False)
        run_step("return_to_home", back)
        result["returns_completed_elapsed_seconds"] = round(
            time.monotonic() - SCRIPT_STARTED, 3
        )
        result["home_verified"] = False
        result["status"] = "COMPLETED"

    except Exception as exc:
        result["error"] = "%s: %s" % (type(exc).__name__, exc)
        print("[reddit] FAIL: " + result["error"], flush=True)
    finally:
        result["elapsed_seconds"] = round(
            time.monotonic() - SCRIPT_STARTED, 3
        )
        print(json.dumps(result, ensure_ascii=False, indent=2))

    return 0 if result["status"] == "COMPLETED" else 1


if __name__ == "__main__":
    raise SystemExit(main())