#!/usr/bin/env bash
# Comprueba que el APK a publicar es el esperado y que Android lo aceptará como
# actualización de la versión instalada.
#
# Uso: verify-apk.sh APK VERSION VERSION_CODE
#   Opcionales: AAPT2 y APKSIGNER (rutas; por defecto el build-tools más reciente del SDK)
#               PINNED_FILE (huella de firma aceptada; por defecto .github/nexus-signing-sha256.txt)
# Escribe "cert_sha256=<huella>" en la salida estándar si todo es correcto.
set -euo pipefail

apk="${1:?falta el APK}"
want_name="${2:?falta la versión}"
want_code="${3:?falta el versionCode}"
want_pkg="app.nexus.launcher"
pinned_file="${PINNED_FILE:-.github/nexus-signing-sha256.txt}"

fail() {
  echo "::error title=APK no válido::$1" >&2
  exit 1
}

[[ -f $apk ]] || fail "No existe el APK '$apk'."

tools_dir=""
if [[ -z "${AAPT2:-}" || -z "${APKSIGNER:-}" ]]; then
  sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  [[ -n $sdk && -d $sdk/build-tools ]] || fail "No encuentro el SDK de Android (build-tools)."
  tools_dir="$sdk/build-tools/$(ls "$sdk/build-tools" | sort -V | tail -n 1)"
fi
aapt2="${AAPT2:-$tools_dir/aapt2}"
apksigner="${APKSIGNER:-$tools_dir/apksigner}"

# --- Paquete, versionCode y versionName reales del APK ---
badging=$("$aapt2" dump badging "$apk" | sed -n '1p')
got_pkg=$(sed -n "s/.*package: name='\([^']*\)'.*/\1/p" <<<"$badging")
got_code=$(sed -n "s/.*versionCode='\([^']*\)'.*/\1/p" <<<"$badging")
got_name=$(sed -n "s/.*versionName='\([^']*\)'.*/\1/p" <<<"$badging")

[[ $got_pkg == "$want_pkg" ]] ||
  fail "El paquete del APK es '$got_pkg' y debería ser '$want_pkg'. Con otro paquete Android lo instala como app distinta."
[[ $got_name == "$want_name" ]] ||
  fail "versionName del APK '$got_name' no coincide con la versión calculada '$want_name'."
[[ $got_code == "$want_code" ]] ||
  fail "versionCode del APK '$got_code' no coincide con el esperado '$want_code' (revisa que build.gradle y next-version.sh calculen lo mismo)."

# --- Firma: válida y con la clave de siempre ---
sig=$("$apksigner" verify --print-certs "$apk") || fail "apksigner no ha validado la firma del APK."
got_cert=$(awk '/certificate SHA-256 digest/ { print tolower($NF); exit }' <<<"$sig")
[[ $got_cert =~ ^[0-9a-f]{64}$ ]] || fail "No pude leer la huella de la firma del APK."

[[ -f $pinned_file ]] || fail "Falta el archivo con la huella de firma ($pinned_file)."
pinned=$(grep -v '^[[:space:]]*#' "$pinned_file" | tr -d '[:space:]' | tr 'A-F' 'a-f')
[[ $pinned =~ ^[0-9a-f]{64}$ ]] || fail "El archivo $pinned_file no contiene una huella SHA-256 válida."

if [[ $got_cert != "$pinned" ]]; then
  fail "El APK está firmado con otra clave (SHA-256 $got_cert) y no con la de las versiones anteriores ($pinned). Si se publicara, Android no dejaría actualizar encima y habría que desinstalar. Revisa los secrets KEYSTORE, KEYSTORE_PASSWORD, KEY_ALIAS y KEY_PASSWORD."
fi

echo "APK correcto: $got_pkg $got_name (versionCode $got_code), firma $got_cert" >&2
echo "cert_sha256=$got_cert"
