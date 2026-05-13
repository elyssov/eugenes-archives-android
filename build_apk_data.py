#!/usr/bin/env python3
"""
Build embedded_data.js for Eugene's Archives Android app.

Reads works.json, universes.json, manifests, chapters, and cover images
from a checkout of the source project (elyssov/eugenes-archives) and
generates a single JS file with all data embedded as base64 / string
literals, then copies style.css alongside it.

Usage:
    build_apk_data.py [--source DIR] [--output DIR]

Defaults:
    --source   $EUGENES_SOURCE_DIR or ../eugenes-archives
    --output   $EUGENES_OUTPUT_DIR or ./app/src/main/assets
"""

import argparse
import base64
import json
import os
import shutil
import sys


def get_mime_type(filename):
    ext = os.path.splitext(filename)[1].lower()
    return {
        ".jpg": "image/jpeg",
        ".jpeg": "image/jpeg",
        ".png": "image/png",
        ".webp": "image/webp",
        ".gif": "image/gif",
        ".svg": "image/svg+xml",
    }.get(ext, "application/octet-stream")


def encode_image_as_data_uri(source_dir, image_path):
    full_path = os.path.join(source_dir, image_path)
    if not os.path.exists(full_path):
        print(f"  WARNING: image not found: {full_path}")
        return None
    mime = get_mime_type(full_path)
    with open(full_path, "rb") as f:
        data = base64.b64encode(f.read()).decode("ascii")
    return f"data:{mime};base64,{data}"


def escape_for_js_string(s):
    s = s.replace("\\", "\\\\")
    s = s.replace('"', '\\"')
    s = s.replace("\n", "\\n")
    s = s.replace("\r", "\\r")
    s = s.replace("\t", "\\t")
    s = s.replace("`", "\\`")
    s = s.replace("${", "\\${")
    return s


def replace_covers_in_catalog(source_dir, catalog):
    image_count = 0
    for entry in catalog:
        cover = entry.get("cover")
        if cover and isinstance(cover, str) and cover.startswith("images/"):
            data_uri = encode_image_as_data_uri(source_dir, cover)
            if data_uri:
                entry["cover"] = data_uri
                image_count += 1
    return image_count


def find_manifests(works_dir, work_id):
    work_path = os.path.join(works_dir, work_id)
    manifests = {}
    if not os.path.isdir(work_path):
        return manifests
    for lang in ("ru", "en"):
        manifest_file = os.path.join(work_path, f"manifest_{lang}.json")
        if os.path.exists(manifest_file):
            manifests[lang] = manifest_file
    return manifests


def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    default_source = os.environ.get(
        "EUGENES_SOURCE_DIR",
        os.path.join(os.path.dirname(script_dir), "eugenes-archives"),
    )
    default_output = os.environ.get(
        "EUGENES_OUTPUT_DIR",
        os.path.join(script_dir, "app", "src", "main", "assets"),
    )

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", default=default_source, help="Path to eugenes-archives checkout")
    parser.add_argument("--output", default=default_output, help="Path to Android assets directory")
    args = parser.parse_args()

    source_dir = os.path.abspath(args.source)
    output_dir = os.path.abspath(args.output)

    works_json = os.path.join(source_dir, "works.json")
    universes_json = os.path.join(source_dir, "universes.json")
    output_js = os.path.join(output_dir, "embedded_data.js")

    print("=" * 60)
    print("Eugene's Archives - APK Data Builder")
    print("=" * 60)
    print(f"  source: {source_dir}")
    print(f"  output: {output_dir}")

    if not os.path.isdir(source_dir):
        print(f"ERROR: source directory not found: {source_dir}", file=sys.stderr)
        return 2
    if not os.path.exists(works_json):
        print(f"ERROR: works.json not found at {works_json}", file=sys.stderr)
        return 2

    os.makedirs(output_dir, exist_ok=True)

    print("\n[1/5] Reading catalogs...")
    with open(works_json, "r", encoding="utf-8") as f:
        works_catalog = json.load(f)
    print(f"  works.json: {len(works_catalog)} entries")

    if os.path.exists(universes_json):
        with open(universes_json, "r", encoding="utf-8") as f:
            universes_catalog = json.load(f)
        print(f"  universes.json: {len(universes_catalog)} entries")
    else:
        universes_catalog = []
        print("  universes.json: not present, using empty list")

    all_ids = set()
    for entry in works_catalog:
        all_ids.add(entry["id"])
    for entry in universes_catalog:
        all_ids.add(entry["id"])
    print(f"  Total unique work IDs: {len(all_ids)}")

    print("\n[2/5] Encoding cover images...")
    img_count = 0
    img_count += replace_covers_in_catalog(source_dir, works_catalog)
    img_count += replace_covers_in_catalog(source_dir, universes_catalog)
    print(f"  Total images encoded: {img_count}")

    print("\n[3/5] Reading manifests...")
    works_dir = os.path.join(source_dir, "works")
    all_manifests = {}
    manifest_count = 0
    for work_id in sorted(all_ids):
        manifests = find_manifests(works_dir, work_id)
        if not manifests:
            continue
        for lang, manifest_path in manifests.items():
            key = f"{work_id}_{lang}"
            try:
                with open(manifest_path, "r", encoding="utf-8") as f:
                    manifest_data = json.load(f)
                all_manifests[key] = manifest_data
                manifest_count += 1
                chapters = len(manifest_data.get("chapters", []))
                print(f"  {key}: {chapters} chapters")
            except Exception as e:
                print(f"  WARNING: failed to read {manifest_path}: {e}")
    print(f"  Total manifests: {manifest_count}")

    print("\n[4/5] Reading chapter HTML files...")
    all_chapters = {}
    chapter_count = 0
    for manifest_key, manifest_data in sorted(all_manifests.items()):
        for chapter in manifest_data.get("chapters", []):
            chapter_file = chapter.get("file", "")
            if not chapter_file:
                continue
            full_path = os.path.join(source_dir, chapter_file)
            if not os.path.exists(full_path):
                print(f"  WARNING: chapter file not found: {full_path}")
                continue
            try:
                with open(full_path, "r", encoding="utf-8") as f:
                    all_chapters[chapter_file] = f.read()
                chapter_count += 1
            except Exception as e:
                print(f"  WARNING: failed to read {full_path}: {e}")
    print(f"  Total chapters read: {chapter_count}")

    print("\n[5/5] Generating embedded_data.js...")
    parts = []
    parts.append(f"var WORKS_CATALOG = {json.dumps(works_catalog, ensure_ascii=False, indent=2)};\n")
    parts.append(f"var UNIVERSES_CATALOG = {json.dumps(universes_catalog, ensure_ascii=False, indent=2)};\n")
    parts.append(f"var MANIFESTS = {json.dumps(all_manifests, ensure_ascii=False, indent=2)};\n")
    parts.append("var CHAPTERS = {};\n")
    for chapter_path in sorted(all_chapters.keys()):
        escaped_html = escape_for_js_string(all_chapters[chapter_path])
        escaped_path = escape_for_js_string(chapter_path)
        parts.append(f'CHAPTERS["{escaped_path}"] = "{escaped_html}";\n')

    with open(output_js, "w", encoding="utf-8") as f:
        f.write("\n".join(parts))
    output_size = os.path.getsize(output_js)

    print("\nCopying style.css...")
    src_css = os.path.join(source_dir, "style.css")
    dst_css = os.path.join(output_dir, "style.css")
    if os.path.exists(src_css):
        shutil.copy2(src_css, dst_css)
        print(f"  Copied style.css ({os.path.getsize(dst_css):,} bytes)")
    else:
        print("  WARNING: style.css not found at source")

    print("\n" + "=" * 60)
    print("SUMMARY")
    print("=" * 60)
    print(f"  Works:     {len(works_catalog)}")
    print(f"  Universes: {len(universes_catalog)}")
    print(f"  Images:    {img_count}")
    print(f"  Manifests: {manifest_count}")
    print(f"  Chapters:  {chapter_count}")
    print(f"  Output:    {output_js}")
    print(f"  Size:      {output_size:,} bytes ({output_size / 1024 / 1024:.2f} MB)")
    print("=" * 60)
    print("Done!")
    return 0


if __name__ == "__main__":
    sys.exit(main())
