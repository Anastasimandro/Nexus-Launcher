#!/usr/bin/env bash
# Calcula la versión de Nexus a publicar.
#
# Uso:  next-version.sh [VERSION_MANUAL] < lista_de_etiquetas
#
# Lee por la entrada estándar los nombres de las etiquetas existentes (una por línea)
# y escribe en la salida estándar pares CLAVE=VALOR (válidos para $GITHUB_OUTPUT):
#   version, tag, previous_tag, version_code
#
# Esquema: 0.1 -> 0.1.1 -> 0.1.2 -> ... (cada número de 0 a 99).
#   - "0.1" y "0.1.0" son la misma versión; se muestra como "0.1".
#   - Sin versión manual se sube el último número (parche) de la etiqueta más alta.
#   - Con versión manual (p. ej. 0.2) debe ser mayor que cualquier etiqueta existente.
#
# versionCode = 1_601_000_000 + mayor*10_000 + menor*100 + parche
# DEBE coincidir con el cálculo de build.gradle (el workflow lo comprueba contra el APK).
set -euo pipefail

requested="${1:-}"
tag_re='^v([0-9]+)\.([0-9]+)(\.([0-9]+))?$'
req_re='^v?([0-9]+)\.([0-9]+)(\.([0-9]+))?$'
max_part=99

fail() {
  echo "::error title=Versión no válida::$1" >&2
  exit 1
}

# Número comparable: mayor*1e8 + menor*1e4 + parche
key_of() { echo $(( $1 * 100000000 + $2 * 10000 + $3 )); }

best_key=-1
best_tag=""
best_maj=0
best_min=0
best_pat=0
while IFS= read -r tag || [[ -n "$tag" ]]; do
  tag="${tag//$'\r'/}"
  [[ $tag =~ $tag_re ]] || continue
  maj=$((10#${BASH_REMATCH[1]}))
  min=$((10#${BASH_REMATCH[2]}))
  pat=$((10#${BASH_REMATCH[4]:-0}))
  # Etiquetas fuera del rango 0-99 no son nuestras: se ignoran.
  if (( maj > max_part || min > max_part || pat > max_part )); then continue; fi
  key=$(key_of "$maj" "$min" "$pat")
  if (( key > best_key )); then
    best_key=$key
    best_tag=$tag
    best_maj=$maj
    best_min=$min
    best_pat=$pat
  fi
done

if [[ -n "$requested" ]]; then
  [[ $requested =~ $req_re ]] || fail "'$requested' no es una versión válida. Usa el formato 0.2 o 0.2.1."
  maj=$((10#${BASH_REMATCH[1]}))
  min=$((10#${BASH_REMATCH[2]}))
  pat=$((10#${BASH_REMATCH[4]:-0}))
  if (( maj > max_part || min > max_part || pat > max_part )); then
    fail "Cada número de la versión debe estar entre 0 y $max_part."
  fi
  key=$(key_of "$maj" "$min" "$pat")
  if (( key <= best_key )); then
    fail "La versión $requested no es mayor que la última publicada ($best_tag). Elige una más alta."
  fi
elif (( best_key < 0 )); then
  maj=0; min=1; pat=0          # primera release: 0.1
else
  maj=$best_maj; min=$best_min; pat=$((best_pat + 1))
  if (( pat > max_part )); then
    fail "El número de parche llegó a $max_part. Lanza el workflow a mano con la versión siguiente (p. ej. $maj.$((min + 1)))."
  fi
fi

if (( pat == 0 )); then version="$maj.$min"; else version="$maj.$min.$pat"; fi
version_code=$(( 1601000000 + maj * 10000 + min * 100 + pat ))

echo "version=$version"
echo "tag=v$version"
echo "previous_tag=$best_tag"
echo "version_code=$version_code"
