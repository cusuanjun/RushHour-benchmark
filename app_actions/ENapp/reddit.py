import time

SCRIPT_STARTED = time.monotonic()

import argparse
import json
import math
from pathlib import Path

import uiautomator2 as u2


def main():

    step_labels = {
        "open_search": "打开搜索",
        "input_keyword": "输入关键词",
        "submit_search": "提交搜索",
        "return_to_results": "返回搜索结果页",
        "return_to_home": "返回首页",
        "open_result": "打开搜索结果",
        "read_until_time": "浏览内容直到设定时间",
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
        print("[reddit] 第 %02d 步：%s" % (len(result["steps"]), step_labels.get(name, name)),
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
            print("[reddit] %s %s，耗时 %.3f 秒" % (
                step_labels.get(name, name), status_labels.get(row["status"], row["status"]), row["elapsed_seconds"]
            ), flush=True)

    try:
        started = time.monotonic()
        d = u2.connect()
        result["connect_seconds"] = round(
            time.monotonic() - started, 3
        )
        d.implicitly_wait(0)

        if d.app_current().get("package") != package:
            raise RuntimeError("请先将 Reddit 打开到前台")
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
                    raise RuntimeError("未找到搜索入口")
                time.sleep(0.15)

            field = d(
                packageName=package,
                className="android.widget.EditText",
            )
            if not field.wait(timeout=args.timeout):
                raise RuntimeError("搜索输入框未出现")
            return {"method": "search_control"}

        def input_keyword():
            fields = d(
                packageName=package,
                className="android.widget.EditText",
            )
            if fields.count != 1:
                raise RuntimeError("搜索输入框不存在或匹配不唯一")
            fields.set_text(config["keyword"])
            if fields.get_text() != config["keyword"]:
                raise RuntimeError("搜索输入内容校验失败")
            return {"value": config["keyword"]}

        def submit_search():
            # 清除 Enter 可能留下的换行和空白。
            field = d(
                packageName=package,
                resourceId="expanded_search_field",
            )
            if not field.wait(timeout=args.timeout):
                raise RuntimeError("搜索输入框未出现")

            keyword = config["keyword"].strip()
            field.set_text(keyword)

            suggestion = d.xpath(
                f"//*[@package='{package}'"
                " and @resource-id='search_query_autocomplete_list_item'"
                " and @clickable='true']"
                "[.//*[@resource-id='selectable_query_item']]"
                "[not(.//*[@resource-id='selectable_query_auto_suggested_item'])]"
            )

            stop = time.monotonic() + args.timeout
            while True:
                matches = suggestion.all()
                exact = [
                    row for row in matches
                    if row.elem.xpath(
                        ".//*[@resource-id='selectable_query_item']/@text"
                    ) == [keyword]
                ]

                if len(exact) == 1:
                    exact[0].click()
                    break

                if time.monotonic() >= stop:
                    raise RuntimeError("未找到原关键词的搜索建议")
                time.sleep(0.2)

            posts = d.xpath(
                f"//*[@package='{package}'"
                " and @resource-id='search_post_section']"
                "/*[@clickable='true' and @enabled='true']"
            )
            if not posts.wait(timeout=args.timeout):
                raise RuntimeError("搜索后未检测到可点击的帖子")

            return {
                "method": "exact_query_suggestion_control",
                "keyword": keyword,
                "post_results_verified": True,
            }

        def open_result():
            first_post = d.xpath(
                f"(//*[@package='{package}'"
                " and @resource-id='search_post_section']"
                "/*[@clickable='true' and @enabled='true'])[1]"
            )

            if not first_post.wait(timeout=args.timeout):
                raise RuntimeError("第一个帖子未出现")

            post = first_post.get()
            description = post.attrib.get("content-desc", "")
            post.click()

            return {
                "method": "first_post_control",
                "description": description,
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
            # 等待刚打开帖子或上一次返回的动画结束。
            time.sleep(0.8)

            d.press("back")

            is_first_return = (
                result["steps"][-1]["name"] == "return_to_results"
            )

            def results_visible():
                search = d.xpath(
                    f"//*[@package='{package}'"
                    " and @resource-id='search_bar_field']"
                )
                tabs = d.xpath(
                    f"//*[@package='{package}'"
                    " and @resource-id='tab_group']"
                )
                return bool(search.exists) and bool(tabs.exists)

            stop = time.monotonic() + args.timeout
            while True:
                visible = results_visible()

                if is_first_return and visible:
                    time.sleep(0.5)
                    return {
                        "method": "android_back",
                        "search_results_verified": True,
                    }

                if not is_first_return and not visible:
                    time.sleep(0.5)
                    return {
                        "method": "android_back",
                        "search_results_left_verified": True,
                        "home_verified": False,
                    }

                if time.monotonic() >= stop:
                    raise RuntimeError(
                        "返回后未出现搜索结果页"
                        if is_first_return
                        else "第二次返回后仍停留在搜索结果页"
                    )

                time.sleep(0.2)

        result["initialization_seconds"] = round(
            time.monotonic() - SCRIPT_STARTED, 3
        )
        print("[reddit] 初始化完成，耗时 %.3f 秒"
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
        print("[reddit] 失败：" + result["error"], flush=True)
    finally:
        result["elapsed_seconds"] = round(
            time.monotonic() - SCRIPT_STARTED, 3
        )
        print(json.dumps(result, ensure_ascii=False, indent=2))

    return 0 if result["status"] == "COMPLETED" else 1


if __name__ == "__main__":
    raise SystemExit(main())
