#!/bin/sh
# Patch main ofbiz-component.xml with project ofbiz-component.xml
# Usage: ./patch-ofbiz-component.sh /path/to/main/ofbiz-component.xml

set -e
PATCH_SRC="$(dirname "$0")/ofbiz-component.xml"
PATCH_TARGET="$1"

if [ -z "$PATCH_TARGET" ]; then
  echo "Usage: $0 /path/to/main/ofbiz-component.xml"
  exit 1
fi

if [ ! -f "$PATCH_SRC" ]; then
  echo "Project ofbiz-component.xml not found: $PATCH_SRC"
  exit 2
fi

if [ ! -f "$PATCH_TARGET" ]; then
  echo "Main ofbiz-component.xml not found: $PATCH_TARGET"
  exit 3
fi

# Backup original
cp "$PATCH_TARGET" "$PATCH_TARGET.bak"

# Simple patch: append project XML to main XML before closing tag
sed -e '/<\/ofbiz-component>/e cat "$PATCH_SRC"' "$PATCH_TARGET.bak" | sed '/<\/ofbiz-component>/q' > "$PATCH_TARGET"
echo "Patched $PATCH_TARGET with $PATCH_SRC"
