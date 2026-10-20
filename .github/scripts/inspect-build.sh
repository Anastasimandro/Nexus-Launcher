#!/usr/bin/env bash
# Inspecciona los artefactos compilados con las herramientas reales del SDK de Android
# (aapt2, apksigner): APK, módulo y overlay. Solo lee y escribe informes en RESULTADOS.
#
# Uso: inspect-build.sh DIST RESULTADOS VERSION
set -uo pipefail
export LC_ALL=C

dist="${1:?falta DIST}"
out="${2:?falta RESULTADOS}"
version="${3:?falta VERSION}"
mkdir -p "$out"

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
bt="$sdk/build-tools/$(ls "$sdk/build-tools" | sort -V | tail -n 1)"
aapt2="$bt/aapt2"
apksigner="$bt/apksigner"
zip="$dist/Nexus-MagiskModule-v${version}.zip"
apk="$dist/Nexus-v${version}.apk"
work="$(mktemp -d)"

{
  echo "build-tools: $bt"
  "$aapt2" version 2>&1
  "$apksigner" --version 2>&1
} > "$out/tools.txt"

ok() { echo "OK    $*" >> "$out/summary.txt"; }
bad() { echo "FALLA $*" >> "$out/summary.txt"; }
: > "$out/summary.txt"

# --- APK de Nexus ---
"$aapt2" dump badging "$apk" > "$out/apk-badging.txt" 2>&1
"$aapt2" dump permissions "$apk" > "$out/apk-permissions.txt" 2>&1
"$apksigner" verify -v --print-certs "$apk" > "$out/apk-signature.txt" 2>&1 \
  && ok "APK de Nexus: firma válida" || bad "APK de Nexus: firma"
grep -q "package: name='app.nexus.launcher'" "$out/apk-badging.txt" \
  && ok "APK: paquete app.nexus.launcher" || bad "APK: paquete"
grep -q "versionName='${version}'" "$out/apk-badging.txt" \
  && ok "APK: versionName ${version}" || bad "APK: versionName"
# Permisos que pide el APK y que el módulo debe conceder u omitir.
grep -E "START_TASKS_FROM_RECENTS|CONTROL_REMOTE_APP_TRANSITION" "$out/apk-permissions.txt" \
  > "$out/apk-essential-permissions.txt"
[[ $(wc -l < "$out/apk-essential-permissions.txt") -ge 2 ]] \
  && ok "APK pide los dos permisos de transición" || bad "APK no pide los dos permisos de transición"

# --- Módulo ---
unzip -l "$zip" > "$out/module-listing.txt" 2>&1
mkdir -p "$work/module" && unzip -q "$zip" -d "$work/module"
cp "$work/module/module.prop" "$out/module.prop"
cp "$work/module/system/product/etc/permissions/privapp-permissions-nexus.xml" "$out/privapp-permissions-nexus.xml"
cmp -s "$apk" "$work/module/system/product/priv-app/Nexus/Nexus.apk" \
  && ok "módulo: lleva el mismo APK" || bad "módulo: el APK no coincide"
[[ -x $work/module/system/bin/nexus-quickstep ]] \
  && ok "módulo: nexus-quickstep ejecutable" || bad "módulo: nexus-quickstep sin bit de ejecución"
[[ -f $work/module/service.sh ]] \
  && ok "módulo: lleva service.sh" || echo "NOTA  módulo: sin service.sh" >> "$out/summary.txt"
[[ -f $work/module/post-fs-data.sh ]] \
  && ok "módulo: lleva post-fs-data.sh" || bad "módulo: sin post-fs-data.sh (no podría aplicar el overlay en el arranque)"

# --- Overlay (el que va dentro del módulo) ---
ov="$work/module/overlay-static/NexusRecentsOverlay.apk"
if [[ -f $ov ]]; then
  "$aapt2" dump badging "$ov" > "$out/overlay-badging.txt" 2>&1
  "$aapt2" dump xmltree --file AndroidManifest.xml "$ov" > "$out/overlay-manifest.txt" 2>&1
  "$aapt2" dump resources "$ov" > "$out/overlay-resources.txt" 2>&1
  "$aapt2" dump configurations "$ov" > "$out/overlay-configurations.txt" 2>&1
  "$apksigner" verify -v --print-certs "$ov" > "$out/overlay-signature.txt" 2>&1 \
    && ok "overlay: firma válida" || bad "overlay: firma"
  grep -q 'E: overlay' "$out/overlay-manifest.txt" && ok "overlay: tiene <overlay>" || bad "overlay: sin <overlay>"
  grep -qE 'targetPackage.*"android"' "$out/overlay-manifest.txt" \
    && ok "overlay: target = android" || bad "overlay: target"
  grep -q 'isStatic.*=true' "$out/overlay-manifest.txt" \
    && ok "overlay: estático" || bad "overlay: no es estático (no se aplicaría en el arranque de PackageManagerService)"
  grep -qE 'priority.*=2147483647' "$out/overlay-manifest.txt" \
    && ok "overlay: prioridad máxima" || bad "overlay: prioridad distinta de la máxima"
  [[ ! -e $work/module/system/product/overlay/NexusRecentsOverlay.apk ]] \
    && ok "módulo: el overlay NO está en system/ (se copia solo si se activa)" \
    || bad "módulo: el overlay está en system/ y se aplicaría siempre"
  grep -q 'string/config_recentsComponentName' "$out/overlay-resources.txt" \
    && ok "overlay: define config_recentsComponentName" || bad "overlay: no define config_recentsComponentName"
  grep -q 'app.nexus.launcher/com.android.quickstep.RecentsActivity' "$out/overlay-resources.txt" \
    && ok "overlay: valor = RecentsActivity de Nexus" || bad "overlay: valor distinto"
  grep -E "targetSdkVersion|minSdkVersion|sdkVersion" "$out/overlay-badging.txt" > "$out/overlay-sdk.txt"
else
  bad "módulo: no lleva el overlay"
fi

# --- Recursos concretos que han fallado en el dispositivo (Resources$NotFoundException) ---
"$aapt2" dump resources "$apk" > "$work/apk-resources-full.txt" 2>&1
grep -E -A4 "0x7f0707ce|dimen/taskbar_phone_size|dimen/taskbar_stashed_size" "$work/apk-resources-full.txt" \
  > "$out/apk-resources-taskbar.txt" 2>&1 || true

# --- Clases de RecentsActivity y servicio en el APK (nombres que usa el overlay y SystemUI) ---
"$aapt2" dump xmltree --file AndroidManifest.xml "$apk" > "$out/apk-manifest.txt" 2>&1
grep -q 'com.android.quickstep.RecentsActivity' "$out/apk-manifest.txt" \
  && ok "APK: declara RecentsActivity" || bad "APK: no declara RecentsActivity"
grep -q 'com.android.quickstep.TouchInteractionService' "$out/apk-manifest.txt" \
  && ok "APK: declara TouchInteractionService" || bad "APK: no declara TouchInteractionService"
grep -q 'android.intent.action.QUICKSTEP_SERVICE' "$out/apk-manifest.txt" \
  && ok "APK: TouchInteractionService responde a QUICKSTEP_SERVICE" || bad "APK: falta la acción QUICKSTEP_SERVICE"

rm -rf "$work"
cat "$out/summary.txt"
