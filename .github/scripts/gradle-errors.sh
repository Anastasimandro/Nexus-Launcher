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

clean=$(sed 's/\x1b\[[0-9;]*m//g' "$log" | cut -c1-400)

# Primero el bloque "What went wrong" completo (ahí están el motivo y las infracciones) y
# después el resto de líneas que parecen errores.
block=$(printf '%s\n' "$clean" | awk '/What went wrong/{c=40} c>0{print; c--}')
others=$(printf '%s\n' "$clean" | grep -E "$pattern" | awk '!seen[$0]++' | head -30)
lines=$(printf '%s\n%s\n' "$block" "$others" | awk 'NF && !seen[$0]++' | head -70)
if [ -z "$lines" ]; then
  lines=$(printf '%s\n' "$clean" | tail -30)
fi

# Los comandos de workflow no admiten saltos de línea: se codifican.
encoded=$(printf '%s\n' "$lines" | sed -e 's/%/%25/g' -e 's/\r/%0D/g' | awk '{printf "%s%%0A", $0}')
echo "::error title=${title}::${encoded}"
