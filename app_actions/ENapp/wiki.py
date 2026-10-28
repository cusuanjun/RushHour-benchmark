import time
SCRIPT_STARTED = time.monotonic()
import argparse
import json
from pathlib import Path
import uiautomator2 as u2

def main():
    # 中文标签仅用于提示和日志，步骤标识及 JSON 状态值保持原样。
    step_labels = {
        "open_search": "打开搜索",
        "return_1": "第一次返回",
        "return_2": "第二次返回",
        "input_and_search": "输入关键词并搜索",
        "tap_home": "点击首页标签",
    }
    status_labels = {"PASS": "成功", "FAIL": "失败", "RUNNING": "执行中", "COMPLETED": "已完成"}

    p = argparse.ArgumentParser()
    p.add_argument("--time", type=float, default=10)
    p.add_argument("--interval", type=float, default=0.1)
    p.add_argument("--timeout", type=float, default=10)
    args = p.parse_args()
    cfg = json.loads((Path(__file__).parent / "profiles/wiki.json")
                     .read_text(encoding="utf-8-sig"))
    result = {"app": "wiki", "status": "FAIL", "steps": []}

    def step(name, action):
        started = time.monotonic()
        item = {"name": name, "status": "FAIL"}
        try:
            item["detail"] = action() or {}
            item["status"] = "PASS"
        finally:
            item["elapsed_seconds"] = round(time.monotonic() - started, 3)
            result["steps"].append(item)
            print(f"[wiki] {step_labels.get(name, name)} {status_labels.get(item['status'], item['status'])}，耗时 {item['elapsed_seconds']:.3f} 秒")

    def click_bottom(pattern):
        deadline = time.monotonic() + args.timeout
        while True:
            controls = []
            for selector in (d(textMatches=pattern, enabled=True),
                             d(descriptionMatches=pattern, enabled=True)):
                for i in range(selector.count):
                    control = selector[i]
                    controls.append((control.info["bounds"]["bottom"], control))
            if controls:
                max(controls, key=lambda pair: pair[0])[1].click()
                return {"method": "bottom_label_control"}
            if time.monotonic() >= deadline:
                raise RuntimeError(f"未找到导航控件：{pattern}")
            time.sleep(max(args.interval, 0.1))

    def search():
        field = d(resourceId="org.wikipedia:id/search_src_text")

        if not field.exists:
            entry = d(resourceId="org.wikipedia:id/search_card")
            if not entry.wait(timeout=args.timeout):
                raise RuntimeError("顶部搜索卡片未出现")
            entry.click()

        if not field.wait(timeout=args.timeout):
            raise RuntimeError("点击搜索卡片后，输入框未出现")

        if field.count != 1:
            raise RuntimeError("搜索输入框不唯一")

        field.click()
        field.set_text(cfg["keyword"])

        if field.get_text() != cfg["keyword"]:
            raise RuntimeError("搜索文字校验失败")

        # 输入后自动搜索。
        time.sleep(1.0)
        return {
            "method": "search_card_then_input",
            "keyword": cfg["keyword"],
            "input_verified": True,
            "results_verified": False,
        }

    def back():
        d.press("back")
        time.sleep(0.6)
        return {"method": "android_back"}

    try:
        d = u2.connect()
        d.implicitly_wait(0)
        if d.app_current().get("package") != cfg["package"]:
            raise RuntimeError("请先将维基百科打开到前台")
        step("open_search", lambda: d(
            resourceId="org.wikipedia:id/nav_tab_search"
        ).click(timeout=args.timeout))
        step("input_and_search", search)
        time.sleep(max(0, SCRIPT_STARTED + args.time - time.monotonic()))
        step("return_1", back)
        step("return_2", back)
        step("tap_home", lambda: d(
            resourceId="org.wikipedia:id/nav_tab_home"
        ).click(timeout=args.timeout))
        result.update(status="COMPLETED", verification="action_execution_only")
    except Exception as exc:
        result["error"] = f"{type(exc).__name__}: {exc}"
    result["elapsed_seconds"] = round(time.monotonic() - SCRIPT_STARTED, 3)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if result["status"] == "COMPLETED" else 1

if __name__ == "__main__":
    raise SystemExit(main())
