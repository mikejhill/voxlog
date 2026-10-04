"""Sets the app version in voxlog-app/build.gradle.kts and creates the matching changelog file.

Usage: python scripts/bump_version.py 1.2.3

The version code is MAJOR*10000 + MINOR*100 + PATCH, so it always increases with the version name.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BUILD_FILE = ROOT / "voxlog-app" / "build.gradle.kts"
CHANGELOG_DIR = ROOT / "fastlane" / "metadata" / "android" / "en-US" / "changelogs"
SEMVER = re.compile(r"^(\d+)\.(\d{1,2})\.(\d{1,2})$")


def version_code(version_name: str) -> int:
    """Maps MAJOR.MINOR.PATCH to MAJOR*10000 + MINOR*100 + PATCH."""
    match = SEMVER.match(version_name)
    if match is None:
        raise ValueError(f"Expected MAJOR.MINOR.PATCH with MINOR and PATCH below 100, got {version_name!r}")
    major, minor, patch = (int(part) for part in match.groups())
    return major * 10_000 + minor * 100 + patch


def main() -> int:
    version_name = sys.argv[1]
    code = version_code(version_name)
    text = BUILD_FILE.read_text(encoding="utf-8")
    current = int(re.search(r"versionCode = (\d+)", text).group(1))
    if code <= current:
        print(f"Version code {code} must be greater than the current {current}.")
        return 1
    text = re.sub(r"versionCode = \d+", f"versionCode = {code}", text)
    text = re.sub(r'versionName = "[^"]+"', f'versionName = "{version_name}"', text)
    BUILD_FILE.write_text(text, encoding="utf-8")
    changelog = CHANGELOG_DIR / f"{code}.txt"
    if not changelog.exists():
        CHANGELOG_DIR.mkdir(parents=True, exist_ok=True)
        changelog.write_text("TODO: describe user-visible changes (max 500 characters).\n", encoding="utf-8")
    print(f"Version {version_name} ({code}). Edit {changelog.relative_to(ROOT)} before tagging.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
