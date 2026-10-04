"""Validates the fastlane/F-Droid store listing: length limits, required files and screenshots.

Usage: python scripts/check_store_metadata.py
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LISTING = ROOT / "fastlane" / "metadata" / "android" / "en-US"
SCREENSHOT_BASELINES = ROOT / "voxlog-app" / "src" / "test" / "screenshots"

LIMITS = {"title.txt": 50, "short_description.txt": 80, "full_description.txt": 4000}
CHANGELOG_LIMIT = 500


def check_text_files(errors: list[str]) -> None:
    for name, limit in LIMITS.items():
        path = LISTING / name
        if not path.exists():
            errors.append(f"missing {path.relative_to(ROOT)}")
            continue
        length = len(path.read_text(encoding="utf-8").strip())
        if length == 0 or length > limit:
            errors.append(f"{name} must be 1-{limit} characters (is {length})")
    for changelog in (LISTING / "changelogs").glob("*.txt"):
        text = changelog.read_text(encoding="utf-8").strip()
        if len(text) > CHANGELOG_LIMIT or text.startswith("TODO"):
            errors.append(f"changelog {changelog.name} is unfinished or over {CHANGELOG_LIMIT} characters")


def check_screenshots(errors: list[str]) -> None:
    published = LISTING / "images" / "phoneScreenshots"
    for graphic in ("icon.png", "featureGraphic.png"):
        if not (LISTING / "images" / graphic).exists():
            errors.append(f"missing store graphic images/{graphic}")
    expected = {p.name.replace("_light", "") for p in SCREENSHOT_BASELINES.glob("*_light.png") if not p.name.startswith("x_")}
    actual = {p.name for p in published.glob("*.png")}
    if expected != actual:
        errors.append(f"store screenshots out of date; run ./gradlew :voxlog-app:generateStoreScreenshots ({sorted(expected ^ actual)})")
        return
    for name in expected:
        baseline = SCREENSHOT_BASELINES / name.replace(".png", "_light.png")
        if baseline.read_bytes() != (published / name).read_bytes():
            errors.append(f"store screenshot {name} differs from its baseline; regenerate it")


def main() -> int:
    errors: list[str] = []
    check_text_files(errors)
    check_screenshots(errors)
    for error in errors:
        print(f"ERROR: {error}")
    if not errors:
        print("Store metadata OK.")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
