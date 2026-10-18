#!/usr/bin/env bash
# Valida el módulo de Nexus y el overlay de recientes en un Android real (emulador con root).
# Cada fase es independiente y deja sus datos en RESULTADOS; ninguna falla el script: lo que
# importa es la evidencia, no que todo salga bien.
#
# Lo que prueba es un emulador de AOSP/Google APIs, NO el dispositivo del usuario: el módulo se
# instala a mano (remount) con los mismos archivos que Magisk monta, y service.sh se sustituye por
# la misma orden (`nexus-quickstep guard`) lanzada a mano.
#
# Uso: validate-on-emulator.sh DIST RESULTADOS
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
TOOL=/data/local/tmp/nexus-quickstep

: > "$out/summary.md"
say() { echo "$*" | tee -a "$out/summary.md"; }
# Ejecuta en el dispositivo y quita los \r.
dsh() { adb shell "$@" 2>&1 | tr -d '\r'; }
# Guarda la salida de un comando en un archivo y anota su código de salida en el resumen.
cap() {
  local name="$1"; shift
  "$@" > "$out/$name.txt" 2>&1
  local rc=$?
  echo "- $name: exit $rc" >> "$out/summary.md"
  return $rc
}
wait_boot() {
  adb wait-for-device
  local deadline=$((SECONDS + 600))
  until [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
    (( SECONDS > deadline )) && { say "!! el arranque no terminó en 10 min"; return 1; }
    sleep 4
  done
  sleep 3
}
reboot_device() {
  adb reboot
  sleep 12
  wait_boot
  adb root > /dev/null 2>&1
  adb wait-for-device
  sleep 4
}
fg() { dsh dumpsys activity activities | grep -E "topResumedActivity|mResumedActivity" | head -n 2; }
crashes() { dsh logcat -d -b crash | grep -E "FATAL|Process:|NoSuchMethodError|NoSuchFieldError|NoClassDefFoundError|UnsatisfiedLinkError" | head -n 30; }

# ---------------------------------------------------------------------------------------------
say "# Validación en Android real (emulador)"
say ""
adb root > /dev/null 2>&1; adb wait-for-device; sleep 3
{
  for p in ro.build.version.release ro.build.version.sdk ro.build.version.sdk_full ro.build.version.codename \
           ro.build.version.incremental ro.build.fingerprint ro.build.type ro.build.flavor ro.product.cpu.abi; do
    echo "$p=$(adb shell getprop $p | tr -d '\r')"
  done
  echo "id=$(adb shell id | tr -d '\r')"
  echo "wm_size=$(adb shell wm size | tr -d '\r')"
} > "$out/device-info.txt"
cat "$out/device-info.txt" | tee -a "$out/summary.md"
say ""

adb push "$here/../../magisk-module/tools/nexus-quickstep" "$TOOL" > /dev/null 2>&1
adb shell chmod 755 "$TOOL"

# ---------------------------------------------------------------------------------------------
say "## Fase 1: auditoría estática contra los binarios del sistema (sin módulo)"
phase_static() {
  local sys="$work/sys" sysui="$work/sysui"
  mkdir -p "$sys" "$sysui"
  local bcp
  bcp="$(adb shell 'echo $BOOTCLASSPATH' | tr -d '\r')"
  if [[ -z $bcp ]]; then
    bcp="/system/framework/framework.jar:/system/framework/ext.jar:/system/framework/services.jar"
    echo "BOOTCLASSPATH vacío: se usa una lista mínima" > "$out/static-warning.txt"
  fi
  echo "$bcp" | tr ':' '\n' > "$out/bootclasspath.txt"
  while IFS= read -r f; do
    [[ -z $f ]] && continue
    adb pull "$f" "$sys/$(tr '/' '_' <<<"$f")" > /dev/null 2>&1 || echo "no se pudo bajar $f" >> "$out/static-pull-errors.txt"
  done < "$out/bootclasspath.txt"
  local ui
  ui="$(adb shell pm path com.android.systemui | head -n 1 | tr -d '\r' | sed 's/^package://')"
  echo "SystemUI: $ui" > "$out/systemui-path.txt"
  adb pull "$ui" "$sysui/SystemUI.apk" > /dev/null 2>&1 || echo "no se pudo bajar SystemUI" >> "$out/static-pull-errors.txt"
  du -sh "$sys" "$sysui" > "$out/static-sizes.txt" 2>&1
  python3 -I "$here/dex_audit.py" --app "$apk" --device "$sys" --aidl-device "$sysui" --link --aidl \
    --dump 'Landroid/window/DesktopModeFlags;' 'Landroid/window/DesktopExperienceFlags;' \
           'Landroid/view/IWindowManager;' 'Lcom/android/wm/shell/recents/IRecentTasks;' \
           'Lcom/android/wm/shell/recents/IRecentTasks$Stub;' \
    > "$out/static-audit.txt" 2>&1
}
cap static-audit-run phase_static
grep -E "^== (ENLACE|AIDL)|^app:" "$out/static-audit.txt" | tee -a "$out/summary.md"
say ""

# ---------------------------------------------------------------------------------------------
say "## Fase 2: instalar el módulo como lo hace Magisk (remount)"
phase_install() {
  unzip -q "$zip" -d "$work/module"
  adb root; adb wait-for-device
  local r
  r="$(adb remount 2>&1)"; echo "$r" > "$out/remount-1.txt"
  if grep -qiE "verity|reboot|failed|not " <<<"$r"; then
    adb disable-verity > "$out/disable-verity.txt" 2>&1
    reboot_device
    r="$(adb remount 2>&1)"; echo "$r" > "$out/remount-2.txt"
  fi
  dsh 'touch /product/.qs_probe && echo PRODUCT_WRITABLE && rm /product/.qs_probe' > "$out/product-writable.txt"
  dsh 'mount | grep -E " /product | /system "' >> "$out/product-writable.txt"
  grep -q PRODUCT_WRITABLE "$out/product-writable.txt" || return 1

  adb shell mkdir -p /product/priv-app/Nexus /product/overlay /product/etc/permissions
  adb push "$work/module/system/product/priv-app/Nexus/Nexus.apk" /product/priv-app/Nexus/ > /dev/null
  adb push "$work/module/system/product/etc/permissions/privapp-permissions-nexus.xml" /product/etc/permissions/ > /dev/null
  adb push "$work/module/system/product/overlay/NexusRecentsOverlay.apk" /product/overlay/ > /dev/null
  adb shell 'restorecon -RF /product/priv-app/Nexus /product/overlay /product/etc/permissions' > /dev/null 2>&1
  dsh 'ls -lZ /product/priv-app/Nexus /product/overlay /product/etc/permissions/privapp-permissions-nexus.xml' > "$out/installed-files.txt"
  reboot_device
}
cap install-module phase_install
say ""

# ---------------------------------------------------------------------------------------------
say "## Fase 3: el módulo en Android real (app privilegiada y overlay)"
phase_module() {
  dsh pm list packages -s | grep -E "nexus" > "$out/pm-system-packages.txt"
  dsh dumpsys package "$PKG" > "$out/dumpsys-package-nexus.txt"
  grep -E "versionCode|codePath|flags=|privateFlags|pkgFlags|installPermissionsFixed" "$out/dumpsys-package-nexus.txt" | head -n 12 > "$out/nexus-flags.txt"
  # Permisos concedidos frente a los de allow.txt
  sed -n '/install permissions:/,/User 0:/p' "$out/dumpsys-package-nexus.txt" | grep -E "granted=true" \
    | sed -E 's/^[[:space:]]*([A-Za-z0-9_.]+): granted=true.*/\1/' | sort -u > "$out/granted-permissions.txt"
  grep -E '^android\.permission\.' "$here/../../magisk-module/permissions/allow.txt" | sort -u > "$out/allow-list.txt"
  comm -23 "$out/allow-list.txt" "$out/granted-permissions.txt" > "$out/allowed-but-NOT-granted.txt"
  dsh logcat -d -s PackageManager | grep -iE "privapp|Privileged permission|allowlist" | head -n 40 > "$out/privapp-log.txt"
  dsh cmd overlay list --user 0 > "$out/overlay-list-all.txt"
  dsh cmd overlay list --user 0 "$OVERLAY" > "$out/overlay-list-ours.txt"
  dsh dumpsys overlay "$OVERLAY" > "$out/dumpsys-overlay-ours.txt"
  dsh cmd overlay lookup --user 0 android android:string/config_recentsComponentName > "$out/lookup-before.txt"
  dsh "cmd role add-role-holder --user 0 android.app.role.HOME $PKG" > "$out/set-home.txt"
  dsh am start -a android.intent.action.MAIN -c android.intent.category.HOME > "$out/start-home.txt"
  sleep 30
  dsh "cat /data/user/0/$PKG/files/quickstep-probe.txt" > "$out/probe-file.txt"
  dsh logcat -d -s NexusQuickstepProbe InputConsumerCompat NexusRootSystem > "$out/probe-log.txt"
  dsh "$TOOL status" > "$out/tool-status-initial.txt"
}
cap module-evidence phase_module
{ echo "overlay (nuestro):"; cat "$out/overlay-list-ours.txt"; echo "lookup antes:"; cat "$out/lookup-before.txt"
  echo "probe:"; cat "$out/probe-file.txt"; echo "permisos permitidos y NO concedidos:"; cat "$out/allowed-but-NOT-granted.txt"; } | tee -a "$out/summary.md"
say ""

# ---------------------------------------------------------------------------------------------
say "## Fase 4: activar el overlay y comprobar que SystemUI se enlaza a Nexus"
phase_enable() {
  dsh "$TOOL enable" > "$out/enable-1.txt"; local rc=$?
  echo "exit del primer enable (sin --force): $(adb shell "$TOOL enable >/dev/null 2>&1; echo \$?" | tr -d '\r')" >> "$out/enable-1.txt"
  if ! grep -q "Overlay activado" "$out/enable-1.txt"; then
    dsh "$TOOL enable --force" > "$out/enable-2-force.txt"
  fi
  dsh cmd overlay list --user 0 "$OVERLAY" > "$out/overlay-list-after-enable.txt"
  dsh dumpsys overlay "$OVERLAY" > "$out/dumpsys-overlay-after-enable.txt"
  dsh cmd overlay lookup --user 0 android android:string/config_recentsComponentName > "$out/lookup-after-enable.txt"
  reboot_device
  # Lo que hace service.sh en un dispositivo con Magisk, lanzado a mano.
  adb shell "NEXUS_GUARD_WAIT=150 nohup $TOOL guard > /data/local/tmp/guard.out 2>&1 &"
  sleep 90
  dsh "$TOOL bound" > "$out/tool-bound.txt"
  dsh "$TOOL status" > "$out/tool-status-after-reboot.txt"
  dsh dumpsys activity services "$SERVICE" > "$out/dumpsys-tis-service.txt"
  dsh dumpsys activity service SystemUIService > "$out/dumpsys-systemui-full.txt"
  grep -n -i -B2 -A25 -E "OverviewProxyService|LauncherProxyService" "$out/dumpsys-systemui-full.txt" | head -n 120 > "$out/dumpsys-systemui-proxy.txt"
  rm -f "$out/dumpsys-systemui-full.txt"
  crashes > "$out/crashes-after-enable.txt"
  dsh logcat -d | grep -E "TouchInteraction|OverviewProxy|LauncherProxy|NexusQuickstep|InputConsumerCompat|QuickStep|AndroidRuntime" | tail -n 250 > "$out/logcat-quickstep.txt"
  dsh "cat /data/adb/nexus_quickstep/watchdog.log" > "$out/watchdog-log-1.txt"
  dsh "ls -la /data/adb/nexus_quickstep" > "$out/state-dir-1.txt"
}
cap enable-overlay phase_enable
{ cat "$out/enable-1.txt"; echo "---"; cat "$out/lookup-after-enable.txt"; echo "---"; cat "$out/tool-bound.txt"; echo "--- caídas:"; cat "$out/crashes-after-enable.txt"; } | tee -a "$out/summary.md"
say ""

# ---------------------------------------------------------------------------------------------
say "## Fase 5: gestos con el overlay activo (inyectados con 'input')"
phase_gestures() {
  dsh cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.gestural > "$out/gestural-nav.txt"
  sleep 12
  local size w h x ybot
  size="$(dsh wm size | grep -oE '[0-9]+x[0-9]+' | tail -n 1)"; w="${size%x*}"; h="${size#*x}"
  x=$((w / 2)); ybot=$((h - 8))
  echo "pantalla ${w}x${h}" > "$out/gesture-notes.txt"

  open_app() { dsh am start -a android.settings.SETTINGS > /dev/null; sleep 4; }
  step() { echo "=== $1" >> "$out/gesture-notes.txt"; }

  step "A. Inicio con gesto rápido"
  open_app; echo "antes: $(fg)" >> "$out/gesture-notes.txt"
  dsh input swipe $x $ybot $x $((h / 3)) 180; sleep 3
  echo "después: $(fg)" >> "$out/gesture-notes.txt"
  dsh dumpsys activity service "$SERVICE" > "$out/dumpsys-tis-after-home.txt"

  step "B. Inicio con gesto lento: captura a mitad del gesto (la ventana debe seguir el dedo)"
  open_app; adb exec-out screencap -p > "$out/shot-before.png" 2>/dev/null
  (adb shell input swipe $x $ybot $x $((h / 2)) 4000 > /dev/null 2>&1 &)
  sleep 2.5; adb exec-out screencap -p > "$out/shot-mid-gesture.png" 2>/dev/null
  sleep 3; adb exec-out screencap -p > "$out/shot-after.png" 2>/dev/null
  echo "después: $(fg)" >> "$out/gesture-notes.txt"

  step "C. Cancelación: subir y volver al punto de partida (debe quedarse en la app)"
  open_app
  dsh input motionevent DOWN $x $ybot; dsh input motionevent MOVE $x $((h / 2)); sleep 0.3
  dsh input motionevent MOVE $x $((h - 40)); dsh input motionevent MOVE $x $ybot; dsh input motionevent UP $x $ybot; sleep 3
  echo "después: $(fg)" >> "$out/gesture-notes.txt"

  step "D. Cancelación con ACTION_CANCEL a mitad del gesto"
  open_app
  dsh input motionevent DOWN $x $ybot; dsh input motionevent MOVE $x $((h / 2)); dsh input motionevent CANCEL $x $((h / 2)); sleep 3
  echo "después: $(fg)" >> "$out/gesture-notes.txt"

  step "E. Atrás"
  open_app; dsh input keyevent KEYCODE_BACK; sleep 2
  echo "después: $(fg)" >> "$out/gesture-notes.txt"

  step "F. Recientes: subir y mantener"
  open_app
  dsh input motionevent DOWN $x $ybot; dsh input motionevent MOVE $x $((h * 55 / 100)); sleep 1.5
  adb exec-out screencap -p > "$out/shot-recents-hold.png" 2>/dev/null
  dsh input motionevent UP $x $((h * 55 / 100)); sleep 3
  adb exec-out screencap -p > "$out/shot-recents.png" 2>/dev/null
  echo "después: $(fg)" >> "$out/gesture-notes.txt"
  dsh input keyevent KEYCODE_HOME; sleep 2

  step "G. Rotación (horizontal) y gesto de Inicio"
  dsh settings put system accelerometer_rotation 0; dsh settings put system user_rotation 1; sleep 3
  open_app
  size="$(dsh wm size | grep -oE '[0-9]+x[0-9]+' | tail -n 1)"
  dsh input swipe $((h / 2)) $((w - 8)) $((h / 2)) $((w / 3)) 180 ; sleep 3
  echo "después (apaisado): $(fg)" >> "$out/gesture-notes.txt"
  dsh settings put system user_rotation 0; sleep 2

  step "H. Bloqueo y desbloqueo, y gesto de Inicio"
  dsh input keyevent KEYCODE_SLEEP; sleep 3; dsh input keyevent KEYCODE_WAKEUP; sleep 2
  dsh wm dismiss-keyguard; sleep 2; dsh input swipe $x $((h * 3 / 4)) $x $((h / 4)) 200; sleep 2
  open_app
  dsh input swipe $x $ybot $x $((h / 3)) 180; sleep 3
  echo "después: $(fg)" >> "$out/gesture-notes.txt"

  dsh dumpsys activity service "$SERVICE" > "$out/dumpsys-tis-after-all-gestures.txt"
  crashes > "$out/crashes-after-gestures.txt"
  dsh logcat -d | grep -E "AbsSwipeUpHandler|RecentsAnimation|TaskAnimationManager|TouchInteraction|AndroidRuntime|FATAL" | tail -n 250 > "$out/logcat-gestures.txt"
}
cap gestures phase_gestures
cat "$out/gesture-notes.txt" | tee -a "$out/summary.md"
say ""

# ---------------------------------------------------------------------------------------------
say "## Fase 6: recuperación manual (nexus-quickstep disable)"
phase_disable() {
  dsh "$TOOL disable" > "$out/disable-1.txt"
  sleep 15
  dsh cmd overlay lookup --user 0 android android:string/config_recentsComponentName > "$out/lookup-after-disable.txt"
  dsh "$TOOL status" > "$out/tool-status-after-disable.txt"
  dsh "$TOOL bound" > "$out/tool-bound-after-disable.txt"
  dsh input keyevent KEYCODE_HOME; sleep 2
  dsh am start -a android.settings.SETTINGS > /dev/null; sleep 3
  local size w h x; size="$(dsh wm size | grep -oE '[0-9]+x[0-9]+' | tail -n 1)"; w="${size%x*}"; h="${size#*x}"; x=$((w / 2))
  dsh input swipe $x $((h - 8)) $x $((h / 3)) 180; sleep 3
  echo "tras el gesto, con el overlay desactivado: $(fg)" > "$out/gesture-after-disable.txt"
  crashes > "$out/crashes-after-disable.txt"
}
cap disable-overlay phase_disable
cat "$out/disable-1.txt" "$out/lookup-after-disable.txt" "$out/tool-bound-after-disable.txt" "$out/gesture-after-disable.txt" | tee -a "$out/summary.md"
say ""

# ---------------------------------------------------------------------------------------------
say "## Fase 7: recuperación automática (Nexus no consigue iniciar Quickstep)"
phase_failure() {
  dsh "$TOOL enable --force" > "$out/failure-enable.txt"
  # Nexus no puede ofrecer el servicio: SystemUI no tendrá a qué enlazarse.
  dsh pm disable "$PKG/com.android.quickstep.TouchInteractionService" > "$out/failure-disable-service.txt"
  reboot_device
  adb shell "NEXUS_GUARD_WAIT=60 NEXUS_GUARD_WATCH=30 nohup $TOOL guard > /data/local/tmp/guard2.out 2>&1 &"
  sleep 110
  dsh "$TOOL status" > "$out/failure-status.txt"
  dsh "$TOOL bound" > "$out/failure-bound.txt"
  dsh cmd overlay lookup --user 0 android android:string/config_recentsComponentName > "$out/failure-lookup.txt"
  dsh "cat /data/adb/nexus_quickstep/watchdog.log" > "$out/failure-watchdog-log.txt"
  dsh "ls -la /data/adb/nexus_quickstep; cat /data/adb/nexus_quickstep/tripped" > "$out/failure-state-dir.txt"
  dsh input keyevent KEYCODE_HOME; sleep 2
  dsh am start -a android.settings.SETTINGS > /dev/null; sleep 3
  local size w h x; size="$(dsh wm size | grep -oE '[0-9]+x[0-9]+' | tail -n 1)"; w="${size%x*}"; h="${size#*x}"; x=$((w / 2))
  dsh input swipe $x $((h - 8)) $x $((h / 3)) 180; sleep 3
  echo "tras el gesto, con el vigilante ya disparado: $(fg)" > "$out/failure-gesture.txt"
  # Con la misma versión de Nexus el vigilante ya falló: enable debe negarse.
  dsh pm enable "$PKG/com.android.quickstep.TouchInteractionService" > /dev/null
  dsh "$TOOL enable" > "$out/failure-enable-again.txt"
}
cap failure-recovery phase_failure
cat "$out/failure-status.txt" "$out/failure-lookup.txt" "$out/failure-watchdog-log.txt" "$out/failure-gesture.txt" "$out/failure-enable-again.txt" | tee -a "$out/summary.md"
say ""

adb logcat -d -b main,system,crash 2>/dev/null | gzip -9 > "$out/logcat-full.txt.gz"
say "(fin)"
