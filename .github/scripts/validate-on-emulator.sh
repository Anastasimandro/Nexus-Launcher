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
# mutable: fases 1 y 4-7 (overlay mutable). static / config: solo la fase 8 correspondiente, cada una
# en su propio emulador para que un remount anterior no contamine el resultado.
VARIANT="${VARIANT:-mutable}"
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
# `grep -q` cierra la tubería en cuanto encuentra algo y, con pipefail, hace fallar a adb por SIGPIPE
# cuando la salida es larga (cmd overlay list): se guarda la salida y se busca después.
overlay_up() { local o; o="$(adb shell cmd overlay list --user 0 2>/dev/null)"; [[ $o == android* || $o == *$'\nandroid'* ]]; }
wait_boot() {
  adb wait-for-device
  local deadline=$((SECONDS + ${BOOT_DEADLINE:-600}))
  until [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" \
           && ( -z "${old_boot:-}" || "$(adb shell cat /proc/sys/kernel/random/boot_id 2>/dev/null | tr -d '\r')" != "$old_boot" ) ]]; do
    (( SECONDS > deadline )) && { say "!! el arranque no terminó en $(( ${BOOT_DEADLINE:-600} / 60 )) min ($((${BOOT_DEADLINE:-600})) s)"; return 1; }
    sleep 4
  done
  # sys.boot_completed llega antes de que estén los servicios (en la primera prueba, `cmd role` y
  # `pm path com.android.systemui` fallaban): se espera a que respondan.
  local sdeadline=$((SECONDS + 300))
  until adb shell service check role 2>/dev/null | grep -q "found" \
      && adb shell pm path com.android.systemui 2>/dev/null | grep -q "^package:" \
      && overlay_up; do
    (( SECONDS > sdeadline )) && { say "!! los servicios no respondieron en 5 min"; break; }
    sleep 4
  done
  wait_stable
}
# El emulador a veces aborta surfaceflinger (RegionSampling) ~25 s después del arranque y el framework
# se reinicia: se espera a que system_server conserve el mismo pid 40 s con los servicios respondiendo.
wait_stable() {
  local deadline=$((SECONDS + 300)) last="" since=$SECONDS pid ok
  while (( SECONDS < deadline )); do
    pid="$(adb shell pidof system_server 2>/dev/null | tr -d '\r')"
    ok=0
    if overlay_up \
       && adb shell service check package 2>/dev/null | grep -q found \
       && [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]]; then ok=1; fi
    echo "$(date +%T) estable? ok=$ok pid=${pid:-none} anterior=${last:-none}" >> "$out/progress.log"
    if [[ $ok == 1 && -n $pid && $pid == "$last" ]]; then
      (( SECONDS - since >= 40 )) && return 0
    else
      since=$SECONDS; last="$pid"
    fi
    sleep 4
  done
  echo "$(date +%T) wait_stable agotado" >> "$out/progress.log"
  say "!! el sistema no se estabilizó en 5 min (pid system_server: ${last:-ninguno})"
  return 1
}
# Si el ejecutor muere (ha pasado dos veces sin dejar rastro), lo ya medido queda en la rama de resultados.
checkpoint() {
  [[ -n ${GITHUB_TOKEN:-} ]] || return 0
  echo "$(date +%T) checkpoint: $*" >> "$out/progress.log"
  "$here/push-results.sh" "$out" "android-$VARIANT-partial" > /dev/null 2>&1 || true
}
REBOOTS=0
reboot_device() {
  # El logcat solo conserva el último arranque: se guarda el de cada fase antes de reiniciar.
  REBOOTS=$((REBOOTS + 1))
  adb logcat -d -b all 2>/dev/null | tail -n 30000 | gzip -9 > "$out/logcat-before-reboot-$REBOOTS.txt.gz"
  checkpoint "antes del reinicio $REBOOTS"
  # Número de arranque anterior: sys.boot_completed seguiría en 1 mientras el dispositivo se apaga.
  local old_boot; old_boot="$(adb shell cat /proc/sys/kernel/random/boot_id 2>/dev/null | tr -d '\r')"
  adb reboot
  # Esperar a que el dispositivo caiga de verdad (el emulador puede tardar minutos).
  local down=$((SECONDS + 240))
  until [[ "$(adb get-state 2>/dev/null)" != device ]]; do (( SECONDS > down )) && break; sleep 2; done
  sleep 3
  if [[ -n ${EARLY_LOG:-} ]]; then
    # Si system_server cae en bucle, el logcat del primer intento se pierde: se guarda desde el principio.
    adb wait-for-device
    ( timeout 90 adb logcat -b all -v threadtime > "$EARLY_LOG" 2>&1 ) &
    sleep 1
  fi
  wait_boot
  adb root > /dev/null 2>&1
  adb wait-for-device
  sleep 4
}
fg() { dsh dumpsys activity activities | grep -E "topResumedActivity|mResumedActivity" | head -n 2; }
# Permisos de Nexus ahora mismo: concedidos / permitidos en la lista blanca pero no concedidos.
perm_state() {
  local tag="$1"
  dsh dumpsys package "$PKG" > "$out/dumpsys-package-nexus-$tag.txt"
  sed -n '/install permissions:/,/User 0:/p' "$out/dumpsys-package-nexus-$tag.txt" | grep -E "granted=true" \
    | sed -E 's/^[[:space:]]*([A-Za-z0-9_.]+): granted=true.*/\1/' | sort -u > "$out/granted-$tag.txt"
  comm -23 "$out/allow-list.txt" "$out/granted-$tag.txt" > "$out/not-granted-$tag.txt"
  echo "[$tag] permisos permitidos y no concedidos: $(wc -l < "$out/not-granted-$tag.txt") ($(paste -sd' ' "$out/not-granted-$tag.txt" | sed 's/android.permission.//g'))"
}
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

if [[ $VARIANT == mutable ]]; then
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
  # Otros jar del framework que no están en BOOTCLASSPATH (donde puede vivir WM Shell en Android 17).
  adb shell 'ls /system/framework/*.jar /system_ext/framework/*.jar /system_ext/priv-app/*/*.apk 2>/dev/null' \
    | tr -d '\r' | sort -u > "$out/extra-binaries.txt"
  while IFS= read -r f; do
    [[ -z $f ]] && continue
    grep -qxF "$f" "$out/bootclasspath.txt" && continue
    adb pull "$f" "$sysui/$(tr '/' '_' <<<"$f")" > /dev/null 2>&1 || echo "no se pudo bajar $f" >> "$out/static-pull-errors.txt"
  done < "$out/extra-binaries.txt"
  local ui
  ui="$(adb shell pm path com.android.systemui | head -n 1 | tr -d '\r' | sed 's/^package://')"
  echo "SystemUI: $ui" > "$out/systemui-path.txt"
  adb pull "$ui" "$sysui/SystemUI.apk" > /dev/null 2>&1 || echo "no se pudo bajar SystemUI" >> "$out/static-pull-errors.txt"
  du -sh "$sys" "$sysui" > "$out/static-sizes.txt" 2>&1
  local repo="$here/../.." mapping_args=()
  [[ -f $dist/mapping.txt ]] && mapping_args=(--mapping "$dist/mapping.txt")
  python3 -I "$here/dex_audit.py" --app "$apk" --device "$sys" --aidl-device "$sysui" --link --aidl \
    "${mapping_args[@]}" \
    --aidl-src "$repo/wmshell" "$repo/systemUI" "$repo/quickstep" "$repo/compatLib" "$repo/lawnchair/aidl" \
    --find ScreenCapture IRecentTasks ILauncherProxy IOverviewProxy ISystemUiProxy IRecentsAnimation IShellTransitions \
    --dump 'Landroid/window/DesktopModeFlags;' 'Landroid/window/DesktopExperienceFlags;' \
           'Landroid/view/IWindowManager;' 'Landroid/window/ScreenCapture;' 'Landroid/window/ScreenCaptureInternal;' \
           'Landroid/app/IActivityTaskManager;' 'Landroid/app/ActivityOptions;' \
           'Landroid/window/ITaskSnapshotManager;' 'Landroid/window/TaskSnapshotManager;' 'Landroid/window/TaskSnapshot;' 'Lcom/android/internal/protolog/common/IProtoLog;' \
           'Lcom/android/internal/protolog/ProtoLog;' 'Lcom/android/internal/protolog/ProtoLogImpl;' \
    > "$out/static-audit.txt" 2>&1
}
cap static-audit-run phase_static
grep -E "^== (ENLACE|AIDL|ESCANEO|BUSCAR)|^app:|^  (DIFERENCIA|AUSENTE)" "$out/static-audit.txt" | tee -a "$out/summary.md"
say ""
fi

# ---------------------------------------------------------------------------------------------
say "## Fase 2: instalar el módulo como lo hace Magisk (remount)"
phase_install() {
  unzip -q "$zip" -d "$work/module"
  adb root; adb wait-for-device
  dsh df -h /data > "$out/df-data-before.txt"
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
  # Nivel de protección de cada permiso que Quickstep necesita (¿lleva el indicador "recents"?).
  dsh pm list permissions -f > "$out/permissions-all.txt"
  for perm in $(cat "$out/allow-list.txt"); do
    grep -A6 -F "permission:$perm" "$out/permissions-all.txt" | grep -E "permission:|protectionLevel" | paste -sd' ' -
  done > "$out/permission-protection.txt"
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

if [[ $VARIANT == mutable ]]; then
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
  perm_state after-enable > "$out/perm-state-after-enable.txt"
  dsh logcat -d | grep -E "TouchInteraction|OverviewProxy|LauncherProxy|NexusQuickstep|InputConsumerCompat|QuickStep|AndroidRuntime" | tail -n 250 > "$out/logcat-quickstep.txt"
  dsh "cat /data/adb/nexus_quickstep/watchdog.log" > "$out/watchdog-log-1.txt"
  dsh "ls -la /data/adb/nexus_quickstep" > "$out/state-dir-1.txt"
}
cap enable-overlay phase_enable
{ cat "$out/enable-1.txt"; echo "---"; cat "$out/lookup-after-enable.txt"; echo "---"; cat "$out/tool-bound.txt"; echo "--- caídas:"; cat "$out/crashes-after-enable.txt"; } | tee -a "$out/summary.md"
say ""

# ---------------------------------------------------------------------------------------------
say "## Fase 4b: ¿concede el sistema a Nexus los permisos 'signature' al ser el proveedor de recientes?"
{ echo "protección de los permisos que pide Nexus:"; cat "$out/permission-protection.txt"; echo; cat "$out/perm-state-after-enable.txt"; } | tee -a "$out/summary.md"
phase_perms_second_boot() {
  reboot_device
  perm_state second-boot > "$out/perm-state-second-boot.txt"
  dsh "$TOOL bound" > "$out/tool-bound-second-boot.txt"
}
if grep -qx "android.permission.MANAGE_ACTIVITY_TASKS" "$out/granted-after-enable.txt"; then
  say "H0: MANAGE_ACTIVITY_TASKS concedido tras activar el overlay mutable y reiniciar."
else
  say "H0: NO concedido tras activar el overlay mutable y reiniciar. H1: un segundo reinicio."
  cap perms-second-boot phase_perms_second_boot
  cat "$out/perm-state-second-boot.txt" "$out/tool-bound-second-boot.txt" | tee -a "$out/summary.md"
fi
say ""

# Nexus tiene que ser el launcher de inicio para que los gestos de Inicio lo lleven a él.
ensure_home() {
  dsh "cmd role add-role-holder --user 0 android.app.role.HOME $PKG" > "$out/set-home$S.txt"
  dsh am start -a android.intent.action.MAIN -c android.intent.category.HOME > "$out/start-home$S.txt"
  sleep 8
  { echo "inicio resuelto: $(dsh cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1)"
    echo "actividad en primer plano: $(fg)"; } >> "$out/set-home$S.txt"
}
# ---------------------------------------------------------------------------------------------
say "## Fase 5: gestos con el overlay activo (inyectados con 'input')"
S=""   # sufijo de los archivos de la fase de gestos (la repetición con el overlay estático usa "-static")
phase_gestures() {
  ensure_home
  dsh cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.gestural > "$out/gestural-nav$S.txt"
  sleep 12
  local size w h x ybot
  size="$(dsh wm size | grep -oE '[0-9]+x[0-9]+' | tail -n 1)"; w="${size%x*}"; h="${size#*x}"
  x=$((w / 2)); ybot=$((h - 8))
  echo "pantalla ${w}x${h}" > "$out/gesture-notes$S.txt"

  open_app() { dsh am start -a android.settings.SETTINGS > /dev/null; sleep 4; }
  step() { echo "=== $1" >> "$out/gesture-notes$S.txt"; }

  step "A. Inicio con gesto rápido"
  open_app; echo "antes: $(fg)" >> "$out/gesture-notes$S.txt"
  dsh input swipe $x $ybot $x $((h / 3)) 180; sleep 3
  echo "después: $(fg)" >> "$out/gesture-notes$S.txt"
  dsh dumpsys activity service "$SERVICE" > "$out/dumpsys-tis-after-home$S.txt"

  step "B. Inicio con gesto lento: captura a mitad del gesto (la ventana debe seguir el dedo)"
  open_app; adb exec-out screencap -p > "$out/shot-before$S.png" 2>/dev/null
  (adb shell input swipe $x $ybot $x $((h / 2)) 4000 > /dev/null 2>&1 &)
  sleep 2.5; adb exec-out screencap -p > "$out/shot-mid-gesture$S.png" 2>/dev/null
  sleep 3; adb exec-out screencap -p > "$out/shot-after$S.png" 2>/dev/null
  echo "después: $(fg)" >> "$out/gesture-notes$S.txt"

  step "C. Cancelación: subir y volver al punto de partida (debe quedarse en la app)"
  open_app
  dsh input motionevent DOWN $x $ybot; dsh input motionevent MOVE $x $((h / 2)); sleep 0.3
  dsh input motionevent MOVE $x $((h - 40)); dsh input motionevent MOVE $x $ybot; dsh input motionevent UP $x $ybot; sleep 3
  echo "después: $(fg)" >> "$out/gesture-notes$S.txt"

  step "D. Cancelación con ACTION_CANCEL a mitad del gesto"
  open_app
  dsh input motionevent DOWN $x $ybot; dsh input motionevent MOVE $x $((h / 2)); dsh input motionevent CANCEL $x $((h / 2)); sleep 3
  echo "después: $(fg)" >> "$out/gesture-notes$S.txt"

  step "E. Atrás"
  open_app; dsh input keyevent KEYCODE_BACK; sleep 2
  echo "después: $(fg)" >> "$out/gesture-notes$S.txt"

  step "F. Recientes: subir y mantener"
  open_app
  dsh input motionevent DOWN $x $ybot; dsh input motionevent MOVE $x $((h * 55 / 100)); sleep 1.5
  adb exec-out screencap -p > "$out/shot-recents-hold$S.png" 2>/dev/null
  dsh input motionevent UP $x $((h * 55 / 100)); sleep 3
  adb exec-out screencap -p > "$out/shot-recents$S.png" 2>/dev/null
  echo "después: $(fg)" >> "$out/gesture-notes$S.txt"
  dsh input keyevent KEYCODE_HOME; sleep 2

  step "G. Rotación (horizontal) y gesto de Inicio"
  dsh settings put system accelerometer_rotation 0; dsh settings put system user_rotation 1; sleep 3
  open_app
  size="$(dsh wm size | grep -oE '[0-9]+x[0-9]+' | tail -n 1)"
  dsh input swipe $((h / 2)) $((w - 8)) $((h / 2)) $((w / 3)) 180 ; sleep 3
  echo "después (apaisado): $(fg)" >> "$out/gesture-notes$S.txt"
  dsh settings put system user_rotation 0; sleep 2

  step "H. Bloqueo y desbloqueo, y gesto de Inicio"
  dsh input keyevent KEYCODE_SLEEP; sleep 3; dsh input keyevent KEYCODE_WAKEUP; sleep 2
  dsh wm dismiss-keyguard; sleep 2; dsh input swipe $x $((h * 3 / 4)) $x $((h / 4)) 200; sleep 2
  open_app
  dsh input swipe $x $ybot $x $((h / 3)) 180; sleep 3
  echo "después: $(fg)" >> "$out/gesture-notes$S.txt"

  dsh dumpsys activity service "$SERVICE" > "$out/dumpsys-tis-after-all-gestures$S.txt"
  crashes > "$out/crashes-after-gestures$S.txt"
  dsh logcat -d | grep -E "AbsSwipeUpHandler|RecentsAnimation|TaskAnimationManager|TouchInteraction|AndroidRuntime|FATAL" | tail -n 250 > "$out/logcat-gestures$S.txt"
}
cap gestures phase_gestures
cat "$out/gesture-notes$S.txt" | tee -a "$out/summary.md"
say ""

# ---------------------------------------------------------------------------------------------
say "## Fase 6: recuperación manual (nexus-quickstep disable)"
phase_disable() {
  { echo "--- cmd overlay list:"; dsh cmd overlay list --user 0 "$OVERLAY"
    echo "--- pm path:"; dsh pm path "$OVERLAY"
    echo "--- service check overlay:"; dsh service check overlay
    echo "--- lookup:"; dsh cmd overlay lookup --user 0 android android:string/config_recentsComponentName; } > "$out/disable-pre.txt" 2>&1
  dsh "sh -x $TOOL disable" > "$out/disable-trace.txt" 2>&1
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
fi

# ---------------------------------------------------------------------------------------------
say "## Fase 8 ($VARIANT): overlay inmutable aplicado desde el arranque (Nexus recibe los permisos 'recents' en el arranque de PMS)"
static_apk="$dist/NexusRecentsOverlayStatic.apk"
# Las consultas a overlay/permisos se hacen con el sistema ya asentado: en la prueba anterior se lanzaron
# antes de que `overlay` estuviera registrado y no valían como evidencia.
settle_overlay() {
  local until_=$((SECONDS + 240))
  until overlay_up; do
    (( SECONDS > until_ )) && { echo "!! cmd overlay no respondió en 4 min" >> "$out/summary.md"; break; }
    sleep 5
  done
  wait_stable
}
collect_overlay() {
  local tag="$1"
  dsh df -h /data /product /system_ext > "$out/df-$tag.txt"
  dsh cmd overlay list --user 0 > "$out/$tag-overlay-list-all.txt"
  dsh cmd overlay list --user 0 "$OVERLAY" > "$out/$tag-overlay-list.txt"
  dsh dumpsys overlay "$OVERLAY" > "$out/$tag-overlay-dumpsys.txt"
  dsh cmd overlay lookup --user 0 android android:string/config_recentsComponentName > "$out/$tag-overlay-lookup.txt"
  perm_state "$tag-overlay" > "$out/perm-state-$tag-overlay.txt"
  dsh "$TOOL bound" > "$out/tool-bound-$tag-overlay.txt"
  dsh dumpsys activity services "$SERVICE" > "$out/dumpsys-tis-$tag-overlay.txt"
  crashes > "$out/crashes-$tag-overlay.txt"
  # Cómo ve el sistema un overlay estático real de la imagen frente al nuestro.
  { for p in com.google.android.overlay.googleconfig com.google.android.overlay.pixelconfigcommon com.android.internal.ranchu.commonoverlay; do
      dsh dumpsys overlay "$p" | grep -E "^[a-z].*:0 \{|mBaseCodePath|mState|mIsMutable|mPriority"; done
    echo "--- dumpsys package (nuestro overlay):"; dsh dumpsys package "$OVERLAY" | grep -iE "overlay|static|codePath|flags=|pkgFlags|privateFlags"
  } > "$out/real-static-overlays-$tag.txt"
  dsh logcat -d -b crash > "$out/crash-buffer-$tag.txt"
  dsh logcat -d | grep -E "SystemUiProxy|TouchInteraction|OverviewProxy|LauncherProxy|NoSuchMethod|NoSuchField|NoClassDef|RemoteException|SecurityException|AbsSwipeUpHandler" | tail -n 300 > "$out/logcat-$tag-overlay.txt"
}
remount_rw() {
  adb root > /dev/null 2>&1; adb wait-for-device
  adb remount > "$out/remount-$1.txt" 2>&1
  # `adb remount` tras desactivar verity pide reiniciar: se repite hasta que quede en rw.
  if grep -qi "reboot" "$out/remount-$1.txt"; then
    reboot_device; adb root > /dev/null 2>&1; adb wait-for-device
    adb remount >> "$out/remount-$1.txt" 2>&1
  fi
}
phase_static_overlay() {
  dsh cmd overlay disable --user 0 "$OVERLAY" > /dev/null
  remount_rw static
  # Mismo paquete: el APK mutable se quita antes de poner el estático.
  adb shell rm -f /product/overlay/NexusRecentsOverlay.apk
  adb push "$static_apk" /product/overlay/NexusRecentsOverlay.apk > "$out/static-push.txt" 2>&1
  adb shell 'restorecon -RF /product/overlay' > /dev/null 2>&1
  { echo "host:   $(sha256sum "$static_apk" | cut -d' ' -f1)"
    echo "device: $(dsh sha256sum /product/overlay/NexusRecentsOverlay.apk)"
    dsh ls -lZ /product/overlay/ | grep -i nexus; } > "$out/static-push-check.txt"
  # OMS guarda el estado de cada overlay (incluido si era mutable) en overlays.xml; el mismo paquete
  # registrado antes como mutable conservaría ese estado aunque el APK nuevo sea estático.
  dsh "grep -A3 'app.nexus.launcher.overlay.recents' /data/system/overlays.xml" > "$out/overlays-xml-before-wipe.txt"
  if [[ ${WIPE_OVERLAY_STATE:-1} == 1 ]]; then
    dsh "rm -f /data/system/overlays.xml; rm -rf /data/system/package_cache" > "$out/wipe-overlay-state.txt"
  fi
  reboot_device
  settle_overlay
  collect_overlay static
}
# 8b: el APK mutable de siempre, pero declarado inmutable y activo por defecto en overlay/config/config.xml.
phase_config_overlay() {
  dsh cmd overlay disable --user 0 "$OVERLAY" > /dev/null
  remount_rw config
  adb shell mkdir -p /product/overlay/config
  case "$VARIANT" in
    configdir) : ;;   # solo el directorio, sin config.xml
    configempty) adb shell 'printf "%s\n" "<config>" "</config>" > /product/overlay/config/config.xml' ;;
    *) adb shell 'printf "%s\n" "<config>" "    <overlay package=\"app.nexus.launcher.overlay.recents\" enabled=\"true\" mutable=\"false\" />" "</config>" > /product/overlay/config/config.xml' ;;
  esac
  adb shell 'restorecon -RF /product/overlay' > /dev/null 2>&1
  dsh "cat /product/overlay/config/config.xml; ls -lZ /product/overlay /product/overlay/config | grep -iE 'nexus|config'" > "$out/config-overlay-files.txt"
  dsh df -h /data /product /system_ext > "$out/df-config-before-reboot.txt"
  BOOT_DEADLINE=150 EARLY_LOG="$out/early-boot-config.txt" reboot_device
  settle_overlay
  collect_overlay config
}
effective() { grep -q "app.nexus.launcher/" "$1" 2>/dev/null; }

case "$VARIANT" in
  static|config|configempty|configdir)
    if [[ ! -f $static_apk ]]; then
      say "No hay APK de overlay estático en dist: no se prueba."
    else
      if [[ $VARIANT == static ]]; then cap static-overlay phase_static_overlay; else cap config-overlay phase_config_overlay; fi
      t=$VARIANT; [[ $t == config* ]] && t=config
      { cat "$out/$t-overlay-list-all.txt" 2>/dev/null | grep -iE "^android|nexus" ; cat "$out/$t-overlay-list.txt" "$out/$t-overlay-lookup.txt" "$out/perm-state-$t-overlay.txt" "$out/tool-bound-$t-overlay.txt" 2>/dev/null
        echo "--- caídas:"; cat "$out/crashes-$t-overlay.txt" 2>/dev/null; } | tee -a "$out/summary.md"
      if effective "$out/$t-overlay-lookup.txt"; then
        say "El overlay inmutable ($t) se aplicó: config_recentsComponentName apunta a Nexus."
        S="-$t"
        cap gestures-final phase_gestures
        cat "$out/gesture-notes$S.txt" | tee -a "$out/summary.md"
        S=""
      else
        say "El overlay inmutable ($t) NO se aplicó a config_recentsComponentName."
      fi
    fi ;;
  *) say "(variante mutable: la fase 8 se prueba en los trabajos static y config)" ;;
esac
say ""

adb logcat -d -b main,system,crash 2>/dev/null | gzip -9 > "$out/logcat-full.txt.gz"
say "(fin)"
