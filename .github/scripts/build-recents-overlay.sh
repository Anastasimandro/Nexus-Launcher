#!/usr/bin/env bash
# Genera el APK del overlay de recientes (RRO) que hace de Nexus el proveedor de Quickstep.
#
# Uso: build-recents-overlay.sh SALIDA.apk
#   Opcionales: AAPT2, ZIPALIGN, APKSIGNER, ANDROID_JAR (si no, se buscan en el SDK de Android)
#               OVERLAY_DIR (por defecto magisk-module/overlay)
#               KEYSTORE_PROPERTIES (por defecto keystore.properties): storeFile, storePassword,
#               keyAlias y keyPassword. Sin ese archivo se firma con una clave temporal, que
#               vale para comprobar la generación pero no para publicar.
#
# El overlay solo define config_recentsComponentName; el resto del módulo lo monta
# build-magisk-module.sh, que llama a este script.
set -euo pipefail
export LC_ALL=C

out_apk="${1:?falta el APK de salida}"
overlay_dir="${OVERLAY_DIR:-magisk-module/overlay}"
props="${KEYSTORE_PROPERTIES:-keystore.properties}"
overlay_pkg="app.nexus.launcher.overlay.recents"
resource="config_recentsComponentName"
component="app.nexus.launcher/com.android.quickstep.RecentsActivity"

fail() {
  echo "::error title=Overlay de recientes::$1" >&2
  exit 1
}

[[ -f $overlay_dir/AndroidManifest.xml ]] || fail "Falta $overlay_dir/AndroidManifest.xml."
[[ -f $overlay_dir/res/values/config.xml ]] || fail "Falta $overlay_dir/res/values/config.xml."

# --- Herramientas del SDK ---
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
newest_build_tools() {
  [[ -n $sdk && -d $sdk/build-tools ]] || return 1
  ls "$sdk/build-tools" | sort -V | tail -n 1
}
if [[ -z "${AAPT2:-}" || -z "${ZIPALIGN:-}" || -z "${APKSIGNER:-}" ]]; then
  bt=$(newest_build_tools) || fail "No encuentro el SDK de Android (build-tools)."
  AAPT2="${AAPT2:-$sdk/build-tools/$bt/aapt2}"
  ZIPALIGN="${ZIPALIGN:-$sdk/build-tools/$bt/zipalign}"
  APKSIGNER="${APKSIGNER:-$sdk/build-tools/$bt/apksigner}"
fi
if [[ -z "${ANDROID_JAR:-}" ]]; then
  [[ -n $sdk && -d $sdk/platforms ]] || fail "No encuentro el SDK de Android (platforms)."
  while IFS= read -r platform; do
    if [[ -f $sdk/platforms/$platform/android.jar ]]; then
      ANDROID_JAR="$sdk/platforms/$platform/android.jar"
      break
    fi
  done < <(ls "$sdk/platforms" | grep -E '^android-[0-9]' | sort -V -r)
fi
for tool in "$AAPT2" "$ZIPALIGN" "$APKSIGNER"; do
  [[ -x $tool ]] || fail "No existe o no se puede ejecutar: $tool"
done
[[ -n "${ANDROID_JAR:-}" && -f $ANDROID_JAR ]] || fail "No encuentro android.jar en el SDK."

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

# --- Recursos y enlace ---
"$AAPT2" compile --dir "$overlay_dir/res" -o "$work/res.zip" || fail "aapt2 compile falló."
# El overlay define su propio recurso con el mismo nombre que el del framework: el sistema los
# empareja por nombre al aplicarlo, así que no hace falta que el recurso sea público en android.jar.
"$AAPT2" link -o "$work/unsigned.apk" -I "$ANDROID_JAR" \
  --manifest "$overlay_dir/AndroidManifest.xml" \
  --min-sdk-version 34 --target-sdk-version "${OVERLAY_TARGET_SDK:-37}" \
  --version-code 1 --version-name 1 \
  "$work/res.zip" || fail "aapt2 link falló."
"$ZIPALIGN" -f -p 4 "$work/unsigned.apk" "$work/aligned.apk" || fail "zipalign falló."

# --- Firma ---
read_prop() { sed -n "s/^$1=//p" "$props" | head -n 1; }
if [[ -f $props ]]; then
  store=$(read_prop storeFile)
  [[ -f $store ]] || fail "storeFile de $props no existe: '$store'."
  store_pass=$(read_prop storePassword)
  key_alias=$(read_prop keyAlias)
  key_pass=$(read_prop keyPassword)
  signer="clave de Nexus"
else
  command -v keytool >/dev/null 2>&1 || fail "Sin $props y sin keytool no se puede firmar el overlay."
  store="$work/temp.jks"
  store_pass="temporal"
  key_alias="temporal"
  key_pass="temporal"
  keytool -genkeypair -keystore "$store" -storepass "$store_pass" -keypass "$key_pass" \
    -alias "$key_alias" -keyalg RSA -keysize 2048 -validity 3650 -dname "CN=Nexus overlay temporal" \
    >/dev/null 2>&1 || fail "keytool no pudo crear la clave temporal."
  signer="clave temporal"
  echo "::notice title=Overlay de recientes::Firmado con una clave temporal (no hay $props)." >&2
fi
"$APKSIGNER" sign --ks "$store" --ks-pass "pass:$store_pass" --ks-key-alias "$key_alias" \
  --key-pass "pass:$key_pass" --out "$work/signed.apk" "$work/aligned.apk" || fail "apksigner sign falló."
"$APKSIGNER" verify "$work/signed.apk" || fail "apksigner verify rechaza el overlay firmado."

# --- Comprobaciones del resultado ---
badging=$("$AAPT2" dump badging "$work/signed.apk" | sed -n '1p')
got_pkg=$(sed -n "s/.*package: name='\([^']*\)'.*/\1/p" <<<"$badging")
[[ $got_pkg == "$overlay_pkg" ]] || fail "El overlay es del paquete '$got_pkg' y debería ser '$overlay_pkg'."

manifest=$("$AAPT2" dump xmltree --file AndroidManifest.xml "$work/signed.apk")
grep -q 'E: overlay' <<<"$manifest" || fail "El manifiesto no tiene el elemento <overlay>."
grep -qE 'targetPackage.*"android"' <<<"$manifest" || fail "El overlay no apunta al paquete 'android'."
# Un overlay estático no se puede desactivar: este tiene que ser mutable. (ALLOW_STATIC_OVERLAY=1
# solo lo usa la validación en emulador para probar el efecto de un overlay aplicado desde el
# arranque; esa variante nunca va en el módulo.)
if grep -q 'isStatic' <<<"$manifest" && [[ ${ALLOW_STATIC_OVERLAY:-} != 1 ]]; then
  fail "El overlay declara isStatic: tiene que ser mutable para poder desactivarlo."
fi

resources=$("$AAPT2" dump resources "$work/signed.apk")
grep -q "string/$resource" <<<"$resources" || fail "El overlay no define string/$resource."
grep -qF "$component" <<<"$resources" || fail "El valor de $resource no es '$component'."

mkdir -p "$(dirname "$out_apk")"
cp "$work/signed.apk" "$out_apk"
echo "Overlay de recientes ($signer) → $out_apk" >&2
