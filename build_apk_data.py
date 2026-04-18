#!/usr/bin/env python3
"""
Build embedded_data.js for Eugene's Archives Android app.
Reads works.json, universes.json, manifests, chapters, and cover images
from the source project and generates a single JS file with all data embedded.
"""

import json
import os
import base64
import shutil
import sys

SOURCE_DIR = r"C:\Projects\eugenes-archives"
OUTPUT_DIR = r"C:\Projects\eugenes-archives-android\app\src\main\assets"

WORKS_JSON = os.path.join(SOURCE_DIR, "works.json")
UNIVERSES_JSON = os.path.join(SOURCE_DIR, "universes.json")
OUTPUT_JS = os.path.join(OUTPUT_DIR, "embedded_data.js")


def get_mime_type(filename):
    """Determine MIME type from file extension."""
    ext = os.path.splitext(filename)[1].lower()
    mime_map = {
        ".jpg": "image/jpeg",
        ".jpeg": "image/jpeg",
        ".png": "image/png",
        ".webp": "image/webp",
        ".gif": "image/gif",
        ".svg": "image/svg+xml",
    }
    return mime_map.get(ext, "application/octet-stream")


def encode_image_as_data_uri(image_path):
    """Read an image file and return a base64 data URI string."""
    full_path = os.path.join(SOURCE_DIR, image_path)
    if not os.path.exists(full_path):
        print(f"  WARNING: Image not found: {full_path}")
        return None
    mime = get_mime_type(full_path)
    with open(full_path, "rb") as f:
        data = base64.b64encode(f.read()).decode("ascii")
    return f"data:{mime};base64,{data}"


def escape_for_js_string(s):
    """Escape a string for safe embedding in a JS string literal (double-quoted)."""
    s = s.replace("\\", "\\\\")
    s = s.replace('"', '\\"')
    s = s.replace("\n", "\\n")
    s = s.replace("\r", "\\r")
    s = s.replace("\t", "\\t")
    # Escape backticks and ${} for template literal safety
    s = s.replace("`", "\\`")
    s = s.replace("${", "\\${")
    return s


def replace_covers_in_catalog(catalog):
    """Replace cover image paths with base64 data URIs in a catalog list."""
    image_count = 0
    for entry in catalog:
        cover = entry.get("cover")
        if cover and isinstance(cover, str) and cover.startswith("images/"):
            data_uri = encode_image_as_data_uri(cover)
            if data_uri:
                entry["cover"] = data_uri
                image_count += 1
                print(f"  Encoded cover: {cover} ({len(data_uri)} chars)")
    return image_count


def find_manifests(works_dir, work_id):
    """Find all manifest files for a given work ID."""
    work_path = os.path.join(works_dir, work_id)
    manifests = {}
    if not os.path.isdir(work_path):
        return manifests
    for lang in ["ru", "en"]:
        manifest_file = os.path.join(work_path, f"manifest_{lang}.json")
        if os.path.exists(manifest_file):
            manifests[lang] = manifest_file
    return manifests


def main():
    print("=" * 60)
    print("Eugene's Archives - APK Data Builder")
    print("=" * 60)

    # Ensure output directory exists
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    # Read catalogs
    print("\n[1/5] Reading catalogs...")
    with open(WORKS_JSON, "r", encoding="utf-8") as f:
        works_catalog = json.load(f)
    print(f"  works.json: {len(works_catalog)} entries")

    with open(UNIVERSES_JSON, "r", encoding="utf-8") as f:
        universes_catalog = json.load(f)
    print(f"  universes.json: {len(universes_catalog)} entries")

    # Collect all work IDs from both catalogs
    all_ids = set()
    for entry in works_catalog:
        all_ids.add(entry["id"])
    for entry in universes_catalog:
        all_ids.add(entry["id"])
    print(f"  Total unique work IDs: {len(all_ids)}")

    # Encode cover images
    print("\n[2/5] Encoding cover images...")
    img_count = 0
    img_count += replace_covers_in_catalog(works_catalog)
    img_count += replace_covers_in_catalog(universes_catalog)
    print(f"  Total images encoded: {img_count}")

    # Find and read manifests
    print("\n[3/5] Reading manifests...")
    works_dir = os.path.join(SOURCE_DIR, "works")
    all_manifests = {}  # key: "{id}_{lang}", value: manifest dict
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
                chapter_count = len(manifest_data.get("chapters", []))
                print(f"  {key}: {chapter_count} chapters")
            except Exception as e:
                print(f"  WARNING: Failed to read {manifest_path}: {e}")

    print(f"  Total manifests: {manifest_count}")

    # Read all chapter HTML files
    print("\n[4/5] Reading chapter HTML files...")
    all_chapters = {}  # key: chapter file path (relative), value: HTML content
    chapter_count = 0

    for manifest_key, manifest_data in sorted(all_manifests.items()):
        chapters = manifest_data.get("chapters", [])
        for chapter in chapters:
            chapter_file = chapter.get("file", "")
            if not chapter_file:
                continue
            full_path = os.path.join(SOURCE_DIR, chapter_file)
            if not os.path.exists(full_path):
                print(f"  WARNING: Chapter file not found: {full_path}")
                continue
            try:
                with open(full_path, "r", encoding="utf-8") as f:
                    html_content = f.read()
                all_chapters[chapter_file] = html_content
                chapter_count += 1
            except Exception as e:
                print(f"  WARNING: Failed to read {full_path}: {e}")

    print(f"  Total chapters read: {chapter_count}")

    # Generate the JS file
    print("\n[5/5] Generating embedded_data.js...")

    parts = []

    # WORKS_CATALOG
    works_json_str = json.dumps(works_catalog, ensure_ascii=False, indent=2)
    parts.append(f"var WORKS_CATALOG = {works_json_str};\n")

    # UNIVERSES_CATALOG
    universes_json_str = json.dumps(universes_catalog, ensure_ascii=False, indent=2)
    parts.append(f"var UNIVERSES_CATALOG = {universes_json_str};\n")

    # MANIFESTS
    manifests_json_str = json.dumps(all_manifests, ensure_ascii=False, indent=2)
    parts.append(f"var MANIFESTS = {manifests_json_str};\n")

    # CHAPTERS - use escaped string assignments
    parts.append("var CHAPTERS = {};\n")
    for chapter_path in sorted(all_chapters.keys()):
        html = all_chapters[chapter_path]
        escaped_html = escape_for_js_string(html)
        escaped_path = escape_for_js_string(chapter_path)
        parts.append(f'CHAPTERS["{escaped_path}"] = "{escaped_html}";\n')

    output_content = "\n".join(parts)

    with open(OUTPUT_JS, "w", encoding="utf-8") as f:
        f.write(output_content)

    output_size = os.path.getsize(OUTPUT_JS)

    # Copy style.css
    print("\nCopying style.css...")
    src_css = os.path.join(SOURCE_DIR, "style.css")
    dst_css = os.path.join(OUTPUT_DIR, "style.css")
    if os.path.exists(src_css):
        shutil.copy2(src_css, dst_css)
        css_size = os.path.getsize(dst_css)
        print(f"  Copied style.css ({css_size:,} bytes)")
    else:
        print("  WARNING: style.css not found!")

    # Summary
    print("\n" + "=" * 60)
    print("SUMMARY")
    print("=" * 60)
    print(f"  Works catalog entries:     {len(works_catalog)}")
    print(f"  Universes catalog entries:  {len(universes_catalog)}")
    print(f"  Cover images embedded:     {img_count}")
    print(f"  Manifests embedded:        {manifest_count}")
    print(f"  Chapters embedded:         {chapter_count}")
    print(f"  Output file:               {OUTPUT_JS}")
    print(f"  Output size:               {output_size:,} bytes ({output_size / 1024 / 1024:.2f} MB)")
    print(f"  Style CSS copied:          {dst_css}")
    print("=" * 60)
    print("Done!")


if __name__ == "__main__":
    main()
