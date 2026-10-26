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

    if not all(
        math.isfinite(v)
        for v in (args.time, args.interval, args.timeout)
    ):
        parser.error("Timing values must be finite")

    if args.time < 0 or args.interval < 0 or args.timeout <= 0:
        parser.error("--time/--interval >= 0; --timeout > 0")

    config = json.loads(
        (BASE_DIR / "profiles" / "gmail.json")
        .read_text(encoding="utf-8-sig")
    )
    package = config["package"]
    selectors = config["selectors"]
    gestures = config["gestures"]

    result = {
        "app": "gmail",
        "package": package,
        "status": "FAIL",
        "steps": [],
    }

    d = None
    row = None
    step_started = None

    def present(rules):
        return any(
            d(packageName=package, **rule).exists
            for rule in rules
        )

    def wait_until(check, message):
        deadline = time.monotonic() + args.timeout
        while True:
            if check():
                return
            if time.monotonic() >= deadline:
                raise RuntimeError(message)
            time.sleep(0.2)

    def open_mail():
        rows = d.xpath(
            "//*[@resource-id="
            "'com.google.android.gm:id/thread_list_view']"
            "/*[@clickable='true']"
            "[.//*[@resource-id='com.google.android.gm:id/subject']]"
        )

        rows.click(timeout=args.timeout)

        wait_until(
            lambda: present(selectors["mail"]),
            "Mail detail page did not appear",
        )

        result["mail_opened_elapsed_seconds"] = round(
            time.monotonic() - SCRIPT_STARTED, 3
        )
        return {"method": "message_row_xpath"}

    def scroll_mail():
        width, height = d.window_size()
        x1, y1, x2, y2 = gestures["read_swipe"]

        d.swipe(
            int(width * x1),
            int(height * y1),
            int(width * x2),
            int(height * y2),
            duration=gestures["duration"],
        )
        time.sleep(gestures["settle_seconds"])

        return {
            "direction": "up",
            "swipe_count": 1,
        }

    def read_mail():
        started = time.monotonic()
        remaining = max(
            0.0,
            SCRIPT_STARTED + args.time - started,
        )

        print(
            "[gmail] staying on mail for %.3fs" % remaining,
            flush=True,
        )
        if remaining:
            time.sleep(remaining)

        return {
            "reading_wait_seconds": round(
                time.monotonic() - started, 3
            )
        }

    def return_home():
        result["return_started_elapsed_seconds"] = round(
            time.monotonic() - SCRIPT_STARTED, 3
        )

        d.press("back")

        wait_until(
            lambda: (
                present(selectors["home"])
                and not present(selectors["mail"])
            ),
            "Inbox did not reappear after one Back",
        )

        result["home_elapsed_seconds"] = round(
            time.monotonic() - SCRIPT_STARTED, 3
        )
        return {"back_count": 1, "home_verified": True}

    try:
        connect_started = time.monotonic()
        d = u2.connect()
        result["connect_seconds"] = round(
            time.monotonic() - connect_started, 3
        )

        if d.app_current().get("package") != package:
            raise RuntimeError("Open Gmail in the foreground")

        wait_until(
            lambda: (
                present(selectors["home"])
                and not present(selectors["mail"])
            ),
            "Open the Gmail inbox before running",
        )

        result["initialization_seconds"] = round(
            time.monotonic() - SCRIPT_STARTED, 3
        )
        print(
            "[gmail] initialization completed in %.3fs"
            % result["initialization_seconds"],
            flush=True,
        )

        steps = [
            ("open_mail", open_mail),
            ("scroll_mail", scroll_mail),
            ("read_mail", read_mail),
            ("return_to_home", return_home),
        ]

        for index, (name, action) in enumerate(steps, 1):
            # Return immediately after the reading wait ends.
            if index > 1 and name != "return_to_home":
                if args.interval:
                    time.sleep(args.interval)

            row = {"name": name, "status": "RUNNING"}
            result["steps"].append(row)

            print("[gmail] %02d %s" % (index, name), flush=True)
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
                "[gmail] %s completed in %.3fs"
                % (name, row["elapsed_seconds"]),
                flush=True,
            )
            step_started = None

        result["status"] = "PASS"

    except Exception as exc:
        error = "%s: %s" % (type(exc).__name__, exc)
        result["error"] = error

        if row is not None and row["status"] == "RUNNING":
            row.update(status="FAIL", error=error)
            if step_started is not None:
                row["elapsed_seconds"] = round(
                    time.monotonic() - step_started, 3
                )
            print(
                "[gmail] %s failed in %.3fs: %s"
                % (row["name"], row["elapsed_seconds"], error),
                flush=True,
            )
        else:
            print("[gmail] FAIL: " + error, flush=True)

    finally:
        result["elapsed_seconds"] = round(
            time.monotonic() - SCRIPT_STARTED, 3
        )
        print(json.dumps(result, ensure_ascii=False, indent=2))

    return 0 if result["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())