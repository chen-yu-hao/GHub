#!/usr/bin/env python3
"""Generate Android GHub icons and a transparent SVG from the editable brand SVG."""
from pathlib import Path
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
BRAND = ROOT / "app/design/brand"
RES = ROOT / "app/src/main/res"
ANDROID = "http://schemas.android.com/apk/res/android"
SVG = "http://www.w3.org/2000/svg"
ET.register_namespace("android", ANDROID)
ET.register_namespace("", SVG)


def write_xml(path, element):
    path.parent.mkdir(parents=True, exist_ok=True)
    ET.indent(element, space="    ")
    ET.ElementTree(element).write(path, encoding="utf-8", xml_declaration=True)


def android_attributes(**values):
    return {f"{{{ANDROID}}}{name}": str(value) for name, value in values.items()}


def vector(paths, *, monochrome=False, notification=False):
    root = ET.Element("vector", android_attributes(
        width="24dp" if notification else "108dp",
        height="24dp" if notification else "108dp",
        viewportWidth="108", viewportHeight="108", autoMirrored="false",
    ))
    parent = root
    if notification:
        # Map the adaptive safe-zone artwork to ~20dp of a transparent 24dp canvas.
        parent = ET.SubElement(root, "group", android_attributes(
            pivotX="54", pivotY="54", scaleX="1.5", scaleY="1.5",
        ))
    for path in paths:
        attrs = {"pathData": path.attrib["d"]}
        fill = path.get("fill", "none")
        attrs["fillColor"] = "@android:color/transparent" if fill == "none" else ("#FFFFFF" if monochrome else fill)
        if "stroke" in path.attrib:
            attrs.update(
                strokeColor="#FFFFFF" if monochrome else path.attrib["stroke"],
                strokeWidth=path.attrib["stroke-width"],
                strokeLineCap=path.attrib["stroke-linecap"],
                strokeLineJoin=path.attrib["stroke-linejoin"],
            )
        ET.SubElement(parent, "path", android_attributes(**attrs))
    return root


def main():
    source = ET.parse(BRAND / "ghub-icon.svg").getroot()
    preview_path = BRAND / "preview.html"
    preview = preview_path.read_text()
    start, end = "<!-- brand-mark:start -->", "<!-- brand-mark:end -->"
    before, remaining = preview.split(start, 1)
    _, after = remaining.split(end, 1)
    preview_path.write_text(before + start + "\n" + ET.tostring(source, encoding="unicode") + "\n" + end + after)
    paths = {element.attrib["id"]: element for element in source.findall(f"{{{SVG}}}path")}
    background = paths["background"]
    mark = [paths["monogram"], paths["hub"]]
    write_xml(RES / "mipmap-anydpi/ic_launcher.xml", vector([background, *mark]))
    write_xml(RES / "drawable/ic_launcher_foreground.xml", vector(mark))
    write_xml(RES / "drawable/ic_launcher_monochrome.xml", vector(mark, monochrome=True))
    write_xml(RES / "drawable/ic_notification.xml", vector(mark, monochrome=True, notification=True))

    resources = ET.Element("resources")
    ET.SubElement(resources, "color", {"name": "ghub_icon_background"}).text = background.attrib["fill"]
    write_xml(RES / "values/brand.xml", resources)

    for qualifier, monochrome in [("mipmap-anydpi-v26", False), ("mipmap-anydpi-v33", True)]:
        adaptive = ET.Element("adaptive-icon")
        ET.SubElement(adaptive, "background", android_attributes(drawable="@color/ghub_icon_background"))
        ET.SubElement(adaptive, "foreground", android_attributes(drawable="@drawable/ic_launcher_foreground"))
        if monochrome:
            ET.SubElement(adaptive, "monochrome", android_attributes(drawable="@drawable/ic_launcher_monochrome"))
        write_xml(RES / qualifier / "ic_launcher.xml", adaptive)

    # This transparent mark uses the same geometry, tightly framed for documents.
    source.remove(background)
    source.set("viewBox", "20 20 68 68")
    source.set("width", "68")
    source.set("height", "68")
    paths["monogram"].set("stroke", "#1C293D")
    paths["hub"].set("fill", "#4B6590")
    source.find(f"{{{SVG}}}desc").text = "A rounded G connection path with a blue hub on a transparent background."
    write_xml(BRAND / "ghub-mark.svg", source)
    print("Generated GHub adaptive, monochrome, notification, fallback and transparent icons.")


if __name__ == "__main__":
    main()
