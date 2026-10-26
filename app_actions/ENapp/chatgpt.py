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
        (Path(__file__).resolve().parent / "profiles" / "chatgpt.json")
        .read_text(encoding="utf-8-sig")
    )
    package = config["package"]
    gestures = config["gestures"]
    result = {
        "app": "chatgpt",
        "package": package,
        "status": "FAIL",
        "steps": [],
    }

    def step(name, action, interval=True):
        if interval and result["steps"] and args.interval:
            time.sleep(args.interval)
        row = {"name": name, "status": "RUNNING"}
        result["steps"].append(row)
        print("[chatgpt] %02d %s" % (len(result["steps"]), name),
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
            print("[chatgpt] %s %s in %.3fs" % (
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
            raise RuntimeError("Open ChatGPT in the foreground first")
        width, height = d.window_size()

        def input_prompt():
            field = d(
                packageName=package,
                className="android.widget.EditText",
            )
            if not field.wait(timeout=args.timeout):
                raise RuntimeError("Message input not found")
            if field.count != 1:
                raise RuntimeError("Multiple message inputs found")
            field.click()
            field.set_text(config["prompt"])
            if field.get_text() != config["prompt"]:
                raise RuntimeError("Input verification failed")
            return {"method": "input_control", "value": config["prompt"]}

        def send_prompt():
            stop = time.monotonic() + args.timeout
            while True:
                button = d(
                    packageName=package,
                    descriptionMatches=config["send_pattern"],
                    enabled=True,
                )
                if button.count == 1:
                    button.click(timeout=0.5)
                    return {
                        "method": "send_control",
                        "response_verified": False,
                    }
                if time.monotonic() >= stop:
                    raise RuntimeError(
                        "Send arrow not found; UI hierarchy is needed"
                    )
                time.sleep(0.15)

        def read_response():
            time.sleep(gestures["response_wait"])
            x1, y1, x2, y2 = gestures["read_swipe"]
            d.swipe(
                int(width * x1), int(height * y1),
                int(width * x2), int(height * y2),
                duration=gestures["swipe_duration"],
            )
            return {"swipe_count": 1, "scroll_verified": False}

        def final_tap():
            field = d(
                packageName=package,
                className="android.widget.EditText",
            )
            if not field.wait(timeout=args.timeout):
                raise RuntimeError("Message input not found")
            if field.count != 1:
                raise RuntimeError("Multiple message inputs found")

            field.click(timeout=0.5)
            return {"method": "input_control_click"}

        result["initialization_seconds"] = round(
            time.monotonic() - SCRIPT_STARTED, 3
        )
        print("[chatgpt] initialization completed in %.3fs"
              % result["initialization_seconds"], flush=True)

        step("input_prompt", input_prompt)
        step("send_prompt", send_prompt)
        step("read_response", read_response)

        if args.interval:
            time.sleep(args.interval)
        remaining = max(
            0.0, SCRIPT_STARTED + args.time - time.monotonic()
        )
        result["wait_before_final_seconds"] = round(remaining, 3)
        print("[chatgpt] waiting %.3fs before final tap"
              % remaining, flush=True)
        time.sleep(remaining)

        result["final_started_elapsed_seconds"] = round(
            time.monotonic() - SCRIPT_STARTED, 3
        )
        step("tap_bottom_center", final_tap, interval=False)
        result["status"] = "COMPLETED"

    except Exception as exc:
        result["error"] = "%s: %s" % (type(exc).__name__, exc)
        print("[chatgpt] FAIL: " + result["error"], flush=True)
    finally:
        result["elapsed_seconds"] = round(
            time.monotonic() - SCRIPT_STARTED, 3
        )
        print(json.dumps(result, ensure_ascii=False, indent=2))

    return 0 if result["status"] == "COMPLETED" else 1


if __name__ == "__main__":
    raise SystemExit(main())