"""Fails when the merged release manifest requests a permission that docs/PERMISSIONS.md doesn't document.

Usage: python scripts/check_permissions.py <merged AndroidManifest.xml> docs/PERMISSIONS.md
"""

import re
import sys
import xml.etree.ElementTree as ElementTree
from pathlib import Path

ANDROID_NAMESPACE = "{http://schemas.android.com/apk/res/android}"
ANDROID_PREFIX = "android.permission."


def manifest_permissions(manifest_path: Path) -> set[str]:
    """Returns the short names of every <uses-permission> in the manifest."""
    root = ElementTree.parse(manifest_path).getroot()
    names = {element.get(f"{ANDROID_NAMESPACE}name") for element in root.iter("uses-permission")}
    return {name.removeprefix(ANDROID_PREFIX) for name in names if name}


def documented_permissions(document_path: Path) -> set[str]:
    """Returns every backticked permission-like token in the documentation table."""
    tokens = re.findall(r"`([A-Za-z0-9_.]+)`", document_path.read_text(encoding="utf-8"))
    return {token.removeprefix(ANDROID_PREFIX) for token in tokens if token.isupper() or "." in token}


def main() -> int:
    manifest_path, document_path = Path(sys.argv[1]), Path(sys.argv[2])
    undocumented = sorted(manifest_permissions(manifest_path) - documented_permissions(document_path))
    if undocumented:
        print("Undocumented permissions (add them to docs/PERMISSIONS.md and the store description):")
        for name in undocumented:
            print(f"  - {name}")
        return 1
    print("All permissions are documented.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
