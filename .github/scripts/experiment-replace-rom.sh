#!/usr/bin/env bash
# EXPERIMENTO (no es parte del módulo): opción A. Nexus compilado con el applicationId del launcher de
# recientes de la ROM (Pixel Launcher) y puesto EN SU LUGAR, sin overlay. Si el paquete coincide con
# config_recentsComponentName, PackageManager le concede los permisos `recents` en el arranque normal.
#
# Mide, tras un reinicio completo: permisos clave concedidos, si SystemUI se enlaza a su
# TouchInteractionService, quién es el launcher de Inicio y si hay caídas.
# Emulador Android 17 con root, NO el móvil del usuario. No falla nunca: lo que importa es la evidencia.
#
# Uso: experiment-replace-rom.sh APK RESULTADOS
set -uo pipefail
export LC_ALL=C

apk="${1:?falta el APK}"
out="${2:?falta RESULTADOS}"
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
export PATH="$sdk/platform-tools:$sdk/emulator:$PATH"
mkdir -p "$out"
: > "$out/summary.md"
say() { echo "$*" | tee -a "$out/summary.md"; }
dsh() { adb shell "$@" 2>&1 | tr -d '\r'; }

KEY_PERMS="android.permission.MANAGE_ACTIVITY_TASKS android.permission.MONITOR_INPUT android.permission.READ_FRAME_BUFFER android.permission.REMOVE_TASKS android.permission.STATUS_BAR_SERVICE android.permission.START_TASKS_FROM_RECENTS"
AAPT2="$(ls "$sdk"/build-tools/*/aapt2 | sort -V | tail -n 1)"
PKG="$("$AAPT2" dump badging "$apk" | sed -n "1s/.*package: name='\([^']*\)'.*/\1/p")"
SERVICE=$PKG/com.android.quickstep.TouchInteractionService
say "# Experimento A: Nexus con el paquete del launcher de la ROM ($PKG)"

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
      && adb shell pm path com.android.systemui 2>/dev/null | grep -q "^package:"; do
    (( SECONDS > sdeadline )) && { say "!! los servicios no respondieron en 5 min"; break; }
    sleep 4
  done
  sleep 25
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

snapshot() {
  local tag="$1"
  dsh dumpsys package "$PKG" > "$out/dumpsys-package-$tag.txt"
  sed -n '/install permissions:/,/User 0:/p' "$out/dumpsys-package-$tag.txt" | grep -E "granted=true" \
    | sed -E 's/^[[:space:]]*([A-Za-z0-9_.]+): granted=true.*/\1/' | sort -u > "$out/granted-$tag.txt"
  local granted=0 missing=""
  for p in $KEY_PERMS; do
    if grep -qx "$p" "$out/granted-$tag.txt"; then granted=$((granted + 1)); else missing="$missing ${p#android.permission.}"; fi
  done
  local rc home bound
  rc="$(dsh cmd overlay lookup --user 0 android android:string/config_recentsComponentName | head -n 1)"
  home="$(dsh cmd shortcut get-default-launcher | head -n 1)"
  dsh dumpsys activity services "$SERVICE" > "$out/dumpsys-tis-$tag.txt"
  if sed '/Destroying services:/,$d' "$out/dumpsys-tis-$tag.txt" | grep -q "received=true"; then bound=sí; else bound=no; fi
  local abi; abi="$(grep -m1 -oE 'primaryCpuAbi=[^ ]+' "$out/dumpsys-package-$tag.txt")"
  echo "| $tag | $granted/6 | ${missing:-ninguno} | ${rc:-?} | $bound | ${home:-?} | $abi |" >> "$out/table.md"
  dsh logcat -d -b crash | head -n 120 > "$out/crashes-$tag.txt"
}
: > "$out/table.md"
echo "| paso | permisos clave | faltan | config_recentsComponentName | SystemUI enlazado | launcher de Inicio | ABI |" >> "$out/table.md"
echo "|---|---|---|---|---|---|---|" >> "$out/table.md"

adb root > /dev/null 2>&1; adb wait-for-device; sleep 3
for p in ro.build.version.release ro.build.version.sdk ro.build.fingerprint; do echo "$p=$(adb shell getprop $p | tr -d '\r')" | tee -a "$out/summary.md"; done

# --- Qué launcher de recientes trae la ROM y dónde vive ---
for _ in $(seq 1 60); do
  dsh cmd overlay lookup --user 0 android android:string/config_recentsComponentName | head -n 1 | grep -q '/' && break
  sleep 5
done
RC="$(dsh cmd overlay lookup --user 0 android android:string/config_recentsComponentName | head -n 1)"
ROM_PKG="${RC%%/*}"
say "config_recentsComponentName de la ROM: $RC"
dsh pm path "$ROM_PKG" > "$out/rom-pm-path.txt"
dsh dumpsys package "$ROM_PKG" | grep -E "codePath|privateFlags|versionName|signatures|flags=" > "$out/rom-package-info.txt"
dsh "pm list packages -f | grep -E '$ROM_PKG|launcher'" >> "$out/rom-pm-path.txt"
if [[ "$ROM_PKG" != "$PKG" ]]; then say "!! La variante usa $PKG y la ROM usa $ROM_PKG: el experimento no aplica"; exit 0; fi

ROM_APK="$(grep -m1 '^package:' "$out/rom-pm-path.txt" | sed 's/^package://')"
say "APK del launcher de la ROM: $ROM_APK"
ROM_DIR="$(dirname "$ROM_APK")"
dsh "ls -laZR $ROM_DIR" > "$out/rom-dir-before.txt"
dsh "find /system /system_ext /product /vendor -name 'privapp-permissions*' 2>/dev/null | xargs grep -l '$PKG' 2>/dev/null" > "$out/rom-privapp-xml.txt"

# --- Preparación ---
r="$(adb remount 2>&1)"; echo "$r" > "$out/remount-1.txt"
if grep -qiE "verity|reboot|failed|not " <<<"$r"; then
  adb disable-verity > "$out/disable-verity.txt" 2>&1
  reboot_device
  r="$(adb remount 2>&1)"; echo "$r" > "$out/remount-2.txt"
fi
dsh 'touch /product/.qs_probe && echo PRODUCT_WRITABLE && rm /product/.qs_probe' > "$out/product-writable.txt"
if ! grep -q PRODUCT_WRITABLE "$out/product-writable.txt"; then say "!! /product no es escribible"; exit 0; fi

# Línea base: el launcher de la ROM, sin tocar nada (tras el reinicio de disable-verity el emulador necesita un rato).
sleep 40
snapshot "0-rom-original"

# Quitar actualizaciones del paquete en /data (si las hubiera) para que mande la copia de la partición.
dsh pm uninstall-system-updates "$PKG" > "$out/uninstall-updates.txt"

# La allowlist de privapp: la de la ROM si existe + la nuestra con los mismos permisos que el módulo.
{
  echo '<?xml version="1.0" encoding="utf-8"?>'
  echo '<permissions>'
  echo "    <privapp-permissions package=\"$PKG\">"
  grep -vE '^\s*(#|$)' "$here/../../magisk-module/permissions/allow.txt" | sed -E 's/^\s*(\S+).*/        <permission name="\1"\/>/'
  # Igual que el módulo real: lo que el APK pide, es privilegiado y no se concede se deniega explícitamente
  # (si no, la ROM aborta el arranque: «Signature|privileged permissions not in privileged permission allowlist»).
  for perm in android.permission.FORCE_STOP_PACKAGES android.permission.INTERACT_ACROSS_USERS android.permission.MANAGE_USERS; do
    echo "        <deny-permission name=\"$perm\"/>"
  done
  echo '    </privapp-permissions>'
  echo '</permissions>'
} > "$out/privapp-permissions-experiment.xml"
# La allowlist se lee de la MISMA partición que la app (el Pixel Launcher vive en /system_ext: un XML en
# /product no le afecta y la ROM aborta el arranque).
ROM_PART="/$(cut -d/ -f2 <<<"$ROM_APK")"
say "Partición del launcher de la ROM: $ROM_PART"
adb shell "mkdir -p $ROM_PART/etc/permissions" > /dev/null
adb push "$out/privapp-permissions-experiment.xml" "$ROM_PART/etc/permissions/privapp-permissions-nexus-experiment.xml" > /dev/null

# Sustituir el APK de la ROM por el de Nexus; fuera los restos compilados del original.
adb shell "rm -rf $ROM_DIR/oat $ROM_DIR/lib $ROM_DIR/*.apk" > /dev/null 2>&1
adb push "$apk" "$ROM_APK" > /dev/null
adb shell "chmod 644 $ROM_APK; restorecon -RF $ROM_DIR $ROM_PART/etc/permissions" > /dev/null 2>&1
dsh "ls -laZR $ROM_DIR" > "$out/rom-dir-after.txt"
reboot_device
snapshot "1-sustituido-reinicio-1"

# Dar un momento a SystemUI y abrir Inicio.
dsh input keyevent KEYCODE_HOME > /dev/null
sleep 15
snapshot "2-tras-inicio"

# ¿Son los datos del Pixel Launcher (mismo paquete, otra firma y otro código) los que hacen caer la app?
dsh ls -laZ /data/data/$PKG > "$out/data-dir-before-clear.txt"
dsh pm clear "$PKG" > "$out/pm-clear.txt"
dsh logcat -c
dsh input keyevent KEYCODE_HOME > /dev/null
sleep 20
snapshot "2b-tras-pm-clear"

# El Pixel Launcher no tiene código nativo, así que PackageManager guardó primaryCpuAbi=null y no lo recalcula
# al cambiar el APK de la partición: faltan las librerías (libnrb.so...). Instalar Nexus encima, como
# actualización de la app de sistema (misma firma), recalcula ABI y librerías.
adb push "$apk" /data/local/tmp/nexus-update.apk > /dev/null
dsh pm install -r -d /data/local/tmp/nexus-update.apk > "$out/pm-install-update.txt"
dsh logcat -c
dsh input keyevent KEYCODE_HOME > /dev/null
sleep 25
dsh pm path "$PKG" > "$out/pm-path-after-update.txt"
snapshot "2c-actualizado-por-pm-install"

# Segundo reinicio completo (estado persistido, caché de PackageManager ya reconstruida).
reboot_device
sleep 20
snapshot "3-reinicio-2"

# --- ¿Funciona algo? ---
dsh logcat -d | grep -E "TouchInteraction|Quickstep|RecentsAnimation|SecurityException|STATUS_BAR_SERVICE|Launcher|FATAL" | tail -n 150 > "$out/logcat-quickstep.txt"
dsh "dumpsys package $PKG" | grep -E "versionName|codePath|signatures|privateFlags" > "$out/final-package-info.txt"
dsh "getprop | grep -E 'ro.build.version|control_privapp'" > "$out/props.txt"
dsh "logcat -d | grep -iE 'privileged permission|not in the allowlist|privapp'" > "$out/privapp-log.txt"

say ""
say "## Resultado"
say ""
cat "$out/table.md" | tee -a "$out/summary.md"
exit 0
