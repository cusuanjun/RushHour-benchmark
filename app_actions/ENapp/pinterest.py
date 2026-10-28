import argparse
import json
import re
import time
from pathlib import Path

import uiautomator2 as u2


SCRIPT_STARTED = time.monotonic()


def main():

    step_labels = {
        "open_top_left_pin": "打开左侧图片卡片",
        "open_top_right_pin": "打开右侧图片卡片",
        "return_to_feed_1": "第一次返回内容流",
        "return_to_feed_2": "第二次返回内容流",
    }

    parser = argparse.ArgumentParser()
    parser.add_argument("--time", type=float, default=10)
    parser.add_argument("--interval", type=float, default=0.1)
    parser.add_argument("--timeout", type=float, default=10)
    args = parser.parse_args()

    config_path = Path(__file__).parent / "profiles" / "pinterest.json"
    config = json.loads(config_path.read_text(encoding="utf-8-sig"))

    device = u2.connect()
    device.implicitly_wait(0)

    width, height = device.window_size()
    steps = []

    def find_card(side):
        candidates = device.xpath(
            "//*[@clickable='true' and @enabled='true']"
        ).all()

        matches = []
        for element in candidates:
            bounds = element.attrib.get("bounds", "")
            numbers = [int(value) for value in re.findall(r"-?\d+", bounds)]
            if len(numbers) != 4:
                continue

            left, top, right, bottom = numbers
            card_width = right - left
            card_height = bottom - top
            if card_width <= 0 or card_height <= 0:
                continue

            center_x = (left + right) / 2
            center_y = (top + bottom) / 2
            area_ratio = card_width * card_height / (width * height)

            in_side = center_x < width / 2 if side == "left" else center_x >= width / 2
            if (
                in_side
                and top > height * 0.06
                and center_y < height * config["card_center_y_max_ratio"]
                and area_ratio >= config["card_min_area_ratio"]
            ):
                matches.append((area_ratio, element))

        if not matches:
            raise RuntimeError(
                f"当前可见内容流中未找到可点击的{'左' if side == 'left' else '右'}侧图片卡片"
            )

        return max(matches, key=lambda item: item[0])[1]

    def step(name, action):
        started = time.monotonic()
        detail = action()
        elapsed = round(time.monotonic() - started, 3)
        steps.append({
            "name": name,
            "status": "PASS",
            "elapsed_seconds": elapsed,
            "detail": detail or {},
        })
        print(f"[pinterest] {step_labels.get(name, name)} 成功，耗时 {elapsed:.3f} 秒")

    def open_right_pin():
        deadline = time.monotonic() + args.timeout

        while True:
            try:
                card = find_card("right")
                break
            except RuntimeError:
                if time.monotonic() >= deadline:
                    raise
                time.sleep(max(args.interval, 0.1))

        card.click()
        return {
            "method": "visible_clickable_card",
            "detail_page_verified": False,
        }

    def return_right():
        device.swipe(
            1,
            height // 2,
            int(width * 0.65),
            height // 2,
            duration=0.2,
        )
        time.sleep(config["back_settle_seconds"])

        return {
            "method": "left_edge_swipe_right",
            "home_verified": False,
        }

    try:
        step("open_top_left_pin", lambda: (
            find_card("left").click(),
            {"method": "visible_clickable_card"},
        )[1])

        time.sleep(config["first_view_seconds"])

        step("return_to_feed_1", return_right)

        step("open_top_right_pin", open_right_pin)

        deadline = SCRIPT_STARTED + args.time
        remaining = deadline - time.monotonic()
        if remaining > 0:
            time.sleep(remaining)

        step("return_to_feed_2", return_right)

        result = {
            "app": "pinterest",
            "package": config["package"],
            "status": "PASS",
            "steps": steps,
            "elapsed_seconds": round(time.monotonic() - SCRIPT_STARTED, 3),
        }
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0

    except Exception as exc:
        result = {
            "app": "pinterest",
            "package": config["package"],
            "status": "FAIL",
            "steps": steps,
            "error": f"{type(exc).__name__}: {exc}",
            "elapsed_seconds": round(time.monotonic() - SCRIPT_STARTED, 3),
        }
        print(f"[pinterest] 失败：{result['error']}")
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
