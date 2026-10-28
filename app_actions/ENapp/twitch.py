import time

SCRIPT_STARTED = time.monotonic()

import argparse
import json
import math
from pathlib import Path

import uiautomator2 as u2


def main():
    # 中文标签仅用于提示和日志，步骤标识及 JSON 状态值保持原样。
    step_labels = {
        "return_to_home": "返回首页",
        "close_miniplayer": "关闭小窗",
        "open_bottom_video": "打开底部可见视频",
    }
    status_labels = {"PASS": "成功", "FAIL": "失败", "RUNNING": "执行中", "COMPLETED": "已完成"}

    parser = argparse.ArgumentParser()
    parser.add_argument("--time", type=float, default=10)
    parser.add_argument("--interval", type=float, default=0.1)
    parser.add_argument("--timeout", type=float, default=5)
    args = parser.parse_args()
    if not all(math.isfinite(v) for v in vars(args).values()):
        parser.error("时间参数必须是有限数值，不能为无穷大或 NaN")
    if args.time < 0 or args.interval < 0 or args.timeout <= 0:
        parser.error("--time 和 --interval 必须大于或等于 0；--timeout 必须大于 0")

    config = json.loads(
        (Path(__file__).resolve().parent / "profiles" / "twitch.json")
        .read_text(encoding="utf-8-sig")
    )
    package = config["package"]
    result = {"app": "twitch", "package": package, "status": "FAIL", "steps": []}
    d = None

    def control(key, **extra):
        return d(packageName=package, **config["selectors"][key], **extra)

    def wait(check, message):
        deadline = time.monotonic() + args.timeout
        while not check():
            if time.monotonic() >= deadline:
                raise RuntimeError(message)
            time.sleep(0.15)

    def home():
        return (
            not control("chat").exists
            and control("feed").exists
            and control("home", selected=True).exists
        )

    def open_bottom_video():
        wait(home, "首页内容流尚未就绪")
        cards = control("card", clickable=True)
        choices = []
        for index in range(cards.count):
            info = cards[index].info
            box = info.get("visibleBounds") or info["bounds"]
            if box["bottom"] > box["top"] and box["right"] > box["left"]:
                choices.append((box["bottom"], index))
        if not choices:
            raise RuntimeError("未找到可见的视频卡片")
        _, index = max(choices)
        cards[index].click()
        result["video_clicked_elapsed_seconds"] = round(
            time.monotonic() - SCRIPT_STARTED, 3
        )
        return {
            "method": "bottom_visible_card_control",
            "player_page_verified": False,
            "playback_verified": False,
        }

    def return_to_feed():
        d.press("back")
        wait(home, "首页内容流未重新出现")
        return {"home_verified": True}

    def close_miniplayer():
        import xml.etree.ElementTree as ET
        from uiautomator2.exceptions import UiObjectNotFoundError

        wait(home, "首页内容流尚未就绪")

        close_button = d.xpath(
            "//*[@resource-id='theatre-screen']"
            "//*[@clickable='true']["
            "contains(@content-desc, '关闭') or "
            "contains(translate(@content-desc,"
            "'ABCDEFGHIJKLMNOPQRSTUVWXYZ',"
            "'abcdefghijklmnopqrstuvwxyz'), 'close') or "
            "contains(translate(@content-desc,"
            "'ABCDEFGHIJKLMNOPQRSTUVWXYZ',"
            "'abcdefghijklmnopqrstuvwxyz'), 'dismiss')]"
        )

        def clean_home():
            tree = ET.fromstring(
                d.dump_hierarchy(compressed=False)
            )
            nodes = [
                n for n in tree.iter("node")
                if n.get("package") == package
            ]
            ids = {n.get("resource-id", "") for n in nodes}
            player_ids = {
                "theatre-screen",
                "mini-player-controls-overlay",
                "video-player",
                package + ":id/video_presenter_container",
                package + ":id/minimized_overlay",
            }
            home_selected = any(
                n.get("resource-id")
                == package + ":id/viewer_bottom_nav_home"
                and n.get("selected") == "true"
                for n in nodes
            )
            feed_present = bool(ids & {
                "feed-list",
                package + ":id/feed_item_recycler",
            })
            return (
                home_selected and feed_present
                and not (ids & player_ids)
            )

        for attempt in range(1, 4):
            buttons = close_button.all()
            if not buttons:
                mini = control("miniplayer")
                if not mini.exists:
                    raise RuntimeError(
                        "未找到小窗入口，无法验证小窗是否关闭"
                    )
                mini.click(timeout=0.5)
                buttons = close_button.all()

            if len(buttons) > 1:
                raise RuntimeError("匹配到多个关闭按钮")
            if not buttons:
                continue

            attrs = buttons[0].attrib
            rule = {
                "packageName": package,
                # "className": attrs["class"],
                "clickable": True,
            }
            for xml_key, selector_key in [
                ("resource-id", "resourceId"),
                ("content-desc", "description"),
                ("text", "text"),
            ]:
                if attrs.get(xml_key):
                    rule[selector_key] = attrs[xml_key]

            try:
                d(**rule).click(timeout=0.5)
            except UiObjectNotFoundError:
                continue

            if clean_home():
                time.sleep(0.5)
                if clean_home():
                    return {
                        "method": "native_close_control",
                        "attempts": attempt,
                        "miniplayer_closed": True,
                    }

        raise RuntimeError(
            "已尝试关闭 3 次，仍未确认播放器已消失"
        )

    def run_step(name, action, apply_interval=True):
        if apply_interval and result["steps"] and args.interval:
            time.sleep(args.interval)
        row = {"name": name, "status": "RUNNING"}
        result["steps"].append(row)
        print("[twitch] 第 %02d 步：%s" % (len(result["steps"]), step_labels.get(name, name)), flush=True)
        started = time.monotonic()
        try:
            row["detail"] = action()
            row["status"] = "PASS"
        except Exception as exc:
            row.update(status="FAIL", error="%s: %s" % (type(exc).__name__, exc))
            raise
        finally:
            row["elapsed_seconds"] = round(time.monotonic() - started, 3)
            print("[twitch] %s %s，耗时 %.3f 秒" % (
                step_labels.get(name, name), status_labels.get(row["status"], row["status"]), row["elapsed_seconds"]
            ), flush=True)

    try:
        started = time.monotonic()
        d = u2.connect()
        result["connect_seconds"] = round(time.monotonic() - started, 3)
        d.implicitly_wait(args.timeout)
        if d.app_current().get("package") != package:
            raise RuntimeError("请先将 Twitch 打开到前台")
        wait(home, "请从 Twitch 首页内容流开始运行")
        if control("presenter").exists:
            raise RuntimeError("开始前请先关闭现有播放器")
        result["initialization_seconds"] = round(time.monotonic() - SCRIPT_STARTED, 3)
        print("[twitch] 初始化完成，耗时 %.3f 秒" % result["initialization_seconds"], flush=True)

        run_step("open_bottom_video", open_bottom_video)

        if args.interval:
            time.sleep(args.interval)

        remaining = max(
            0.0,
            SCRIPT_STARTED + args.time - time.monotonic(),
        )
        result["wait_before_return_seconds"] = round(remaining, 3)
        print(
            "[twitch] 继续观看 %.3f 秒后返回" % remaining,
            flush=True,
        )
        time.sleep(remaining)

        result["return_started_elapsed_seconds"] = round(
            time.monotonic() - SCRIPT_STARTED, 3
        )
        run_step(
            "return_to_home",
            return_to_feed,
            apply_interval=False,
        )
        result["home_elapsed_seconds"] = round(
            time.monotonic() - SCRIPT_STARTED, 3
        )

        run_step("close_miniplayer", close_miniplayer)

        result["home_clean_elapsed_seconds"] = round(
            time.monotonic() - SCRIPT_STARTED, 3
        )
        result["status"] = "PASS"
    except Exception as exc:
        result["error"] = "%s: %s" % (type(exc).__name__, exc)
        print("[twitch] 失败：" + result["error"], flush=True)
    finally:
        result["elapsed_seconds"] = round(time.monotonic() - SCRIPT_STARTED, 3)
        print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if result["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
