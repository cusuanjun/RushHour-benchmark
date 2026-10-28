import argparse
from pathlib import Path

import uiautomator2 as u2


parser = argparse.ArgumentParser()
parser.add_argument("--name", required=True)
args = parser.parse_args()

if not args.name.replace("_", "").isalnum():
    parser.error("--name 只能包含字母、数字和下划线")

d = u2.connect()
folder = Path(__file__).resolve().parent / "ui_dumps"
folder.mkdir(exist_ok=True)

path = folder / (args.name + ".xml")
path.write_text(
    d.dump_hierarchy(compressed=False),
    encoding="utf-8",
)

print("当前包名：", d.app_current().get("package"))
print("界面 XML 保存位置：", path)
