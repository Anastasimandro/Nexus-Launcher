#!/usr/bin/env bash
# EXPERIMENTO (no es parte del módulo): ¿puede Nexus ser el proveedor de recientes con un overlay
# NORMAL (no estático) activado con `cmd overlay enable` y un recálculo de permisos posterior?
#
# Por qué: el overlay estático hace abortar a zygote en el móvil del usuario (zygote mantiene abierto
# el APK del overlay y lo reabre en cada fork; la ocultación de root le quita el montaje del módulo).
# Un overlay no estático no entra en esa precarga. El problema conocido es el contrario: el sistema
# decide el paquete de recientes al arrancar PackageManagerService y solo entonces concede los
# permisos `recents`; un overlay activado después llegaba tarde. Aquí se mide si algún gesto
# posterior (reinstalar Nexus, reiniciar SystemUI, reiniciar el framework, reiniciar) los concede.
#
# Emulador Android 17 con root, NO el móvil del usuario. No falla nunca: lo que importa es la evidencia.
#
# Uso: experiment-dynamic-overlay.sh DIST RESULTADOS     (DIST con Nexus-v*.apk y Nexus-MagiskModule-v*.zip)
set -uo pipefail
export LC_ALL=C

dist="${1:?falta DIST}"
out="${2:?falta RESULTADOS}"
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
export PATH="$sdk/platform-tools:$sdk/emulator:$PATH"
mkdir -p "$out"
work="$(mktemp -d)"

apk="$(ls "$dist"/Nexus-v*.apk | head -n 1)"
zip="$(ls "$dist"/Nexus-MagiskModule-v*.zip | head -n 1)"
PKG=app.nexus.launcher
OVERLAY=app.nexus.launcher.overlay.recents
SERVICE=$PKG/com.android.quickstep.TouchInteractionService
KEY_PERMS="android.permission.MANAGE_ACTIVITY_TASKS android.permission.MONITOR_INPUT android.permission.READ_FRAME_BUFFER android.permission.REMOVE_TASKS android.permission.STATUS_BAR_SERVICE android.permission.START_TASKS_FROM_RECENTS"

: > "$out/summary.md"
say() { echo "$*" | tee -a "$out/summary.md"; }
dsh() { adb shell "$@" 2>&1 | tr -d '\r'; }

overlay_up() { local o; o="$(adb shell cmd overlay list --user 0 2>/dev/null)"; [[ $o == android* || $o == *$'\nandroid'* ]]; }

wait_boot() {
  adb wait-for-device
  local deadline=$((SECONDS + 600))
  until [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" \
           && ( -z "${old_boot:-}" || "$(adb shell cat /proc/sys/kernel/random/boot_id 2>/dev/null | tr -d '\r')" != "$old_boot" ) ]]; do
    (( SECONDS > deadline )) && { say "!! el arranque no terminó en 10 min"; return 1; }
    sleep 4
  done
  local sdeadline=$((SECONDS + 300))
  until adb shell service check role 2>/dev/null | grep -q "found" \
      && adb shell pm path com.android.systemui 2>/dev/null | grep -q "^package:" \
      && overlay_up; do
    (( SECONDS > sdeadline )) && { say "!! los servicios no respondieron en 5 min"; break; }
    sleep 4
  done
  sleep 20
}

reboot_device() {
  local old_boot; old_boot="$(adb shell cat /proc/sys/kernel/random/boot_id 2>/dev/null | tr -d '\r')"
  adb reboot
  local down=$((SECONDS + 240))
  until [[ "$(adb get-state 2>/dev/null)" != device ]]; do (( SECONDS > down )) && break; sleep 2; done
  sleep 3
  wait_boot
  adb root > /dev/null 2>&1; adb wait-for-device; sleep 4
}

# Permisos de Nexus ahora: cuántos de los clave están concedidos, y estado de recientes y enlace.
snapshot() {
  local tag="$1"
  dsh dumpsys package "$PKG" > "$out/dumpsys-package-$tag.txt"
  sed -n '/install permissions:/,/User 0:/p' "$out/dumpsys-package-$tag.txt" | grep -E "granted=true" \
    | sed -E 's/^[[:space:]]*([A-Za-z0-9_.]+): granted=true.*/\1/' | sort -u > "$out/granted-$tag.txt"
  local granted=0 missing=""
  for p in $KEY_PERMS; do
    if grep -qx "$p" "$out/granted-$tag.txt"; then granted=$((granted + 1)); else missing="$missing ${p#android.permission.}"; fi
  done
  local lookup bound
  lookup="$(dsh cmd overlay lookup --user 0 android android:string/config_recentsComponentName | head -n 1)"
  dsh dumpsys activity services "$SERVICE" > "$out/dumpsys-tis-$tag.txt"
  if sed '/Destroying services:/,$d' "$out/dumpsys-tis-$tag.txt" | grep -q "received=true"; then bound=sí; else bound=no; fi
  dsh cmd overlay list --user 0 "$OVERLAY" > "$out/overlay-list-$tag.txt"
  echo "| $tag | $granted/6 | ${missing:-ninguno} | ${lookup:-?} | $bound | $(tr '\n' ' ' < "$out/overlay-list-$tag.txt" | cut -c1-60) |" >> "$out/table.md"
  dsh logcat -d -b crash | grep -E "FATAL|Process:" | head -n 20 > "$out/crashes-$tag.txt"
}
: > "$out/table.md"
echo "| paso | permisos clave | faltan | config_recentsComponentName | SystemUI enlazado | overlay |" >> "$out/table.md"
echo "|---|---|---|---|---|---|" >> "$out/table.md"

say "# Experimento: overlay normal (no estático) + recálculo de permisos"
say ""
adb root > /dev/null 2>&1; adb wait-for-device; sleep 3
for p in ro.build.version.release ro.build.version.sdk ro.build.fingerprint; do echo "$p=$(adb shell getprop $p | tr -d '\r')" | tee -a "$out/summary.md"; done
say ""

# --- Preparación: Nexus como app privilegiada (igual que el módulo) + overlay NO estático en /product/overlay ---
unzip -q "$zip" -d "$work/module"

# Overlay no estático: mismo recurso, sin isStatic.
mkdir -p "$work/overlay-dyn"
cp -R "$here/../../magisk-module/overlay/res" "$work/overlay-dyn/res"
cat > "$work/overlay-dyn/AndroidManifest.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="app.nexus.launcher.overlay.recents">
    <overlay android:targetPackage="android" />
    <application android:hasCode="false" />
</manifest>
EOF
if ! OVERLAY_MUTABLE=1 OVERLAY_DIR="$work/overlay-dyn" bash "$here/build-recents-overlay.sh" "$work/NexusRecentsOverlay.apk" > "$out/build-overlay.txt" 2>&1; then
  say "!! no se pudo compilar el overlay no estático"; cat "$out/build-overlay.txt" | tee -a "$out/summary.md"; exit 0
fi
"${AAPT2:-$(ls "$sdk"/build-tools/*/aapt2 | sort -V | tail -n 1)}" dump xmltree --file AndroidManifest.xml "$work/NexusRecentsOverlay.apk" > "$out/overlay-manifest.txt" 2>&1

r="$(adb remount 2>&1)"; echo "$r" > "$out/remount-1.txt"
if grep -qiE "verity|reboot|failed|not " <<<"$r"; then
  adb disable-verity > "$out/disable-verity.txt" 2>&1
  reboot_device
  r="$(adb remount 2>&1)"; echo "$r" > "$out/remount-2.txt"
fi
dsh 'touch /product/.qs_probe && echo PRODUCT_WRITABLE && rm /product/.qs_probe' > "$out/product-writable.txt"
if ! grep -q PRODUCT_WRITABLE "$out/product-writable.txt"; then say "!! /product no es escribible: no se puede seguir"; exit 0; fi

adb shell mkdir -p /product/priv-app/Nexus /product/etc/permissions /product/overlay
adb push "$work/module/system/product/priv-app/Nexus/Nexus.apk" /product/priv-app/Nexus/ > /dev/null
adb push "$work/module/system/product/etc/permissions/privapp-permissions-nexus.xml" /product/etc/permissions/ > /dev/null
adb push "$work/NexusRecentsOverlay.apk" /product/overlay/NexusRecentsOverlay.apk > /dev/null
adb shell 'chmod 644 /product/overlay/NexusRecentsOverlay.apk; restorecon -RF /product/priv-app/Nexus /product/etc/permissions /product/overlay/NexusRecentsOverlay.apk' > /dev/null 2>&1
dsh 'ls -lZ /product/overlay/NexusRecentsOverlay.apk /product/priv-app/Nexus' > "$out/installed-files.txt"
reboot_device

# --- 0. Línea base: overlay presente pero sin activar ---
dsh "cmd role add-role-holder --user 0 android.app.role.HOME $PKG" > /dev/null
dsh monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1
sleep 20
snapshot "0-base"
dsh cmd overlay dump --user 0 "$OVERLAY" > "$out/overlay-dump-base.txt" 2>&1

# --- 1. Activar el overlay ---
dsh cmd overlay enable --user 0 "$OVERLAY" > "$out/enable.txt"
sleep 10
snapshot "1-activado"

# --- 2. Reiniciar SystemUI (que vuelva a leer el recurso y enlazar) ---
dsh am crash com.android.systemui > /dev/null 2>&1
sleep 25
snapshot "2-systemui-reiniciado"

# --- 3. Reinstalar Nexus como actualización de la app de sistema (dispara el recálculo de permisos del paquete) ---
dsh pm install -r -d "$apk" > "$out/reinstall.txt"
sleep 15
dsh am crash com.android.systemui > /dev/null 2>&1
sleep 25
snapshot "3-reinstalado"

# --- 4. Desinstalar la actualización y reinstalar para el usuario (otro camino de recálculo) ---
dsh pm uninstall --user 0 "$PKG" > "$out/uninstall-user.txt"
dsh cmd package install-existing --user 0 "$PKG" > "$out/install-existing.txt"
sleep 15
dsh am crash com.android.systemui > /dev/null 2>&1
sleep 25
snapshot "4-install-existing"

# --- 5. Reinicio del framework (system_server) con el overlay ya activado ---
adb shell "stop; start" > /dev/null 2>&1
sleep 20
wait_boot
snapshot "5-framework-reiniciado"

# --- 6. Reinicio completo con el overlay activado (estado persistido) ---
reboot_device
sleep 20
snapshot "6-reinicio"

# --- 7. ¿Funcionan los gestos si ha enlazado? Registro de lo que diga Quickstep ---
dsh logcat -d | grep -E "TouchInteraction|Quickstep|RecentsAnimation|NexusQuickstepProbe|SecurityException|STATUS_BAR_SERVICE" | tail -n 120 > "$out/logcat-quickstep.txt"

say "## Resultado"
say ""
cat "$out/table.md" | tee -a "$out/summary.md"
say ""
say "Lectura: si algún paso muestra 6/6 permisos y SystemUI enlazado = sí con config_recentsComponentName apuntando a Nexus, el overlay normal sirve. Si no, el sistema solo concede los permisos de recientes al arrancar con el overlay estático."
exit 0
