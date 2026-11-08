"""Extract permitted author artwork into static SVG layers; never copies legacy motion/code.

Usage: python tools/extract_preview_layers.py path/to/oprawa-preview-tanks.svg
Outputs are confined to this project's app/src/main/assets/preview/layers directory.
"""
from copy import deepcopy
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

NS = "http://www.w3.org/2000/svg"
ET.register_namespace("", NS)
source = ET.parse(Path(sys.argv[1])).getroot()
defs = source.find(f"{{{NS}}}defs")
battle = source.find(f".//{{{NS}}}g[@id='battle']")
assert defs is not None and battle is not None
tank = battle.find(f"{{{NS}}}g[@class='tank']")
turret = tank.find(f"{{{NS}}}g[@class='turret']")
camera = battle.find(f"{{{NS}}}g[@transform='translate(-8 -103) scale(1.32)']")
clipped = camera.find(f"{{{NS}}}g")
player = clipped.find(f"{{{NS}}}g[@class='player']")
headset = clipped.find(f"{{{NS}}}g[@class='headset']")
assert len(player) == 15, "Author asset changed; review body/arm split before extracting"
output = Path(__file__).resolve().parents[1] / "app/src/main/assets/preview/layers"
output.mkdir(parents=True, exist_ok=True)


def write(name, children):
    root = ET.Element(f"{{{NS}}}svg", {"viewBox": "0 0 640 360"})
    root.append(deepcopy(defs))
    for child in children:
        root.append(deepcopy(child))
    for element in root.iter():
        element.attrib.pop("class", None)
    ET.indent(root)
    ET.ElementTree(root).write(output / f"{name}.svg", encoding="utf-8", xml_declaration=True)


def camera_layer(children, clip=True):
    outer = ET.Element(f"{{{NS}}}g", dict(camera.attrib))
    target = ET.SubElement(outer, f"{{{NS}}}g", {"clip-path": "url(#cam)"}) if clip else outer
    for child in children:
        target.append(deepcopy(child))
    return outer


write("background", list(battle)[:list(battle).index(tank)])
body = deepcopy(tank)
body.remove(body.find(f"{{{NS}}}g[@class='turret']"))
write("tank", [body])
write("turret", [turret])
write("camera-background", [camera_layer(list(clipped)[:3])])
write("player", [camera_layer(list(player)[:7])])
write("mug", [camera_layer(list(player)[7:])])
write("headset", [camera_layer([headset])])
write("camera-frame", [camera_layer(list(camera)[1:], clip=False)])
print(f"Extracted 8 static author-art layers to {output}")
