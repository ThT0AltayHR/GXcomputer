#!/usr/bin/env bash
set -euo pipefail

target="app/src/main/assets/components/wine/GE-proton-11_0-3-arm64ec.wcp"
parts=("${target}".part*)

if [[ ${#parts[@]} -eq 0 || ! -e "${parts[0]}" ]]; then
  echo "Büyük motor paketinin parçaları bulunamadı: ${target}.part*" >&2
  exit 1
fi

cat "${parts[@]}" > "$target"
echo "Birleştirildi: $target ($(du -h "$target" | cut -f1))"