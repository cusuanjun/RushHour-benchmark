import argparse
from pathlib import Path

import uiautomator2 as u2


parser = argparse.ArgumentParser()
parser.add_argument("--name", required=True)
args = parser.parse_args()

if not args.name.replace("_", "").isalnum():
    parser.error("Use letters, numbers and underscores for --name")

d = u2.connect()
folder = Path(__file__).resolve().parent / "ui_dumps"
folder.mkdir(exist_ok=True)

path = folder / (args.name + ".xml")
path.write_text(
    d.dump_hierarchy(compressed=False),
    encoding="utf-8",
)

print("PACKAGE:", d.app_current().get("package"))
print("XML:", path)