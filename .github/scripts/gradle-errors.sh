#!/usr/bin/env bash
# Resume los errores de un log de Gradle en una anotación de GitHub. Los logs completos de
# Actions no siempre se pueden descargar, pero las anotaciones sí se leen por la API.
#
# Uso: gradle-errors.sh <log> [título]
set -u

log="${1:?Falta el fichero de log}"
title="${2:-Gradle}"

if [ ! -f "$log" ]; then
  echo "::warning title=${title}::No hay log en ${log}"
  exit 0
fi

pattern="^e: |error:|What went wrong|Execution failed|FAILED|Could not|Caused by|Unresolved|"
pattern="${pattern}ktlint|spotless|Step '|\\.kt:[0-9]+:[0-9]+|licensee|not allowed|Duplicate|Manifest merger"

lines=$(sed 's/\x1b\[[0-9;]*m//g' "$log" | grep -E "$pattern" | cut -c1-400 | awk '!seen[$0]++' | head -40)
if [ -z "$lines" ]; then
  lines=$(sed 's/\x1b\[[0-9;]*m//g' "$log" | tail -30 | cut -c1-400)
fi

# Los comandos de workflow no admiten saltos de línea: se codifican.
encoded=$(printf '%s\n' "$lines" | sed -e 's/%/%25/g' -e 's/\r/%0D/g' | awk '{printf "%s%%0A", $0}')
echo "::error title=${title}::${encoded}"
