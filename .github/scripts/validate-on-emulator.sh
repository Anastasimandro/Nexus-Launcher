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
VARIANT="${VARIANT:-module}"
OVERLAY=app.nexus.launcher.overlay.recents
SERVICE=$PKG/com.android.quickstep.TouchInteractionService
TOOL=/data/local/tmp/nexus-quickstep
MOD=/data/local/tmp/nexusmod
STATE_DIR=/data/adb/nexus_quickstep

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
  sleep 3
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

# ---------------------------------------------------------------------------------------------
# Los recursos privados del framework (@*android:...) se compilan contra el SDK y fallan en ejecución
# si el sistema ya no los tiene: se comparan con el framework-res.apk real del dispositivo.
check_framework_resources() {
  local bt aapt2
  bt="$(ls "$sdk/build-tools" | sort -V | tail -n 1)"; aapt2="$sdk/build-tools/$bt/aapt2"
  adb pull /system/framework/framework-res.apk "$work/framework-res.apk" > /dev/null 2>&1 || return 1
  "$aapt2" dump resources "$work/framework-res.apk" > "$out/framework-res-dump.txt" 2>&1
  # El android.jar con el que Gradle compila (compileSdk 37, extensión 2): de él salen los IDs que
  # aapt2 graba en el APK para cada @*android:...
  local jar; jar="$(ls -d "$sdk"/platforms/android-37*/android.jar 2>/dev/null | sort -V | tail -n 1)"
  echo "android.jar de compilación: ${jar:-ninguno}"
  if [[ -n $jar ]]; then "$aapt2" dump resources "$jar" > "$out/build-jar-dump.txt" 2>&1; else : > "$out/build-jar-dump.txt"; fi
  [[ -s "$out/build-jar-dump.txt" ]] || echo "-" > "$out/build-jar-dump.txt"
  local bj="$out/build-jar-dump.txt"; [[ "$(head -c 1 "$bj")" == "-" && "$(wc -c < "$bj")" -le 2 ]] && bj="-"
  python3 -I "$here/check-framework-resources.py" "$out/framework-res-dump.txt" "$bj" \
    "$here/../../res" "$here/../../quickstep/res" "$here/../../lawnchair/res" "$here/../../systemUI" "$here/../../compatLib" 2>&1
}
cap framework-resources check_framework_resources > /dev/null 2>&1
cat "$out/framework-resources.txt" | tee -a "$out/summary.md"
rm -f "$out/framework-res-dump.txt" "$out/build-jar-dump.txt"
say ""

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

  adb shell mkdir -p /product/priv-app/Nexus /product/etc/permissions
  adb push "$work/module/system/product/priv-app/Nexus/Nexus.apk" /product/priv-app/Nexus/ > /dev/null
  adb push "$work/module/system/product/etc/permissions/privapp-permissions-nexus.xml" /product/etc/permissions/ > /dev/null
  adb shell 'restorecon -RF /product/priv-app/Nexus /product/etc/permissions' > /dev/null 2>&1
  # El módulo entero (con overlay-static/, post-fs-data.sh y la herramienta) como lo dejaría Magisk en
  # /data/adb/modules/<id>: aquí en /data/local/tmp, porque no hay Magisk en el emulador.
  adb shell rm -rf "$MOD"; adb push "$work/module" "$MOD" > /dev/null 2>&1
  adb shell "mkdir -p $MOD/system/product/overlay"
  dsh "ls -lR $MOD | head -40; ls -lZ /product/priv-app/Nexus /product/etc/permissions/privapp-permissions-nexus.xml; ls /product/overlay | grep -i nexus" > "$out/installed-files.txt"
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

S=""   # sufijo de los archivos de la fase de gestos (la repetición con el overlay estático usa "-static")
# Nexus tiene que ser el launcher de inicio para que los gestos de Inicio lo lleven a él.
ensure_home() {
  dsh "cmd role add-role-holder --user 0 android.app.role.HOME $PKG" > "$out/set-home$S.txt"
  dsh am start -a android.intent.action.MAIN -c android.intent.category.HOME > "$out/start-home$S.txt"
  sleep 8
  { echo "inicio resuelto: $(dsh cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1)"
    echo "actividad en primer plano: $(fg)"; } >> "$out/set-home$S.txt"
}
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

# =============================================================================================
# Flujo del módulo tal y como lo hará el usuario. En el emulador no hay Magisk, así que se
# reproduce lo que haría: post-fs-data.sh (el de verdad, en el shell de Android) corre con el
# dispositivo ya encendido y su resultado se "monta" copiándolo a /product/overlay antes de
# reiniciar; service.sh equivale a lanzar `nexus-quickstep guard` en segundo plano.
# Lo que NO se prueba aquí: el montaje de Magisk/KernelSU/APatch ni que ejecuten post-fs-data.sh antes
# de PackageManagerService (eso solo se puede comprobar en el dispositivo).
# =============================================================================================
tool() { dsh "NEXUS_MODDIR=$MOD $TOOL $*"; }
lookup() { dsh cmd overlay lookup --user 0 android android:string/config_recentsComponentName | head -n 1; }
boot_id() { adb shell cat /proc/sys/kernel/random/boot_id 2>/dev/null | tr -d '\r'; }
settle() { local until_=$((SECONDS + 240)); until overlay_up; do (( SECONDS > until_ )) && break; sleep 5; done; sleep 60; }

remount_rw() {
  adb root > /dev/null 2>&1; adb wait-for-device
  adb remount > "$out/remount-$1.txt" 2>&1
  if grep -qi "reboot" "$out/remount-$1.txt"; then
    reboot_device; adb root > /dev/null 2>&1; adb wait-for-device
    adb remount >> "$out/remount-$1.txt" 2>&1
  fi
}

# Lo que hace Magisk en el arranque: post-fs-data.sh y el montaje de system/ del módulo.
emulate_magisk() {
  remount_rw "$1"
  adb shell rm -f /product/overlay/NexusRecentsOverlay.apk "$MOD/system/product/overlay/NexusRecentsOverlay.apk"
  dsh "sh $MOD/post-fs-data.sh; echo rc=\$?; ls -l $MOD/system/product/overlay; ls -A $STATE_DIR; tail -n 4 $STATE_DIR/watchdog.log" > "$out/post-fs-data-$1.txt"
  if dsh "test -f $MOD/system/product/overlay/NexusRecentsOverlay.apk && echo si" | grep -q si; then
    adb shell "cp $MOD/system/product/overlay/NexusRecentsOverlay.apk /product/overlay/ && chmod 644 /product/overlay/NexusRecentsOverlay.apk && restorecon /product/overlay/NexusRecentsOverlay.apk"
    echo "montado: sí" >> "$out/post-fs-data-$1.txt"
  else
    echo "montado: no" >> "$out/post-fs-data-$1.txt"
  fi
}

# Tras un reinicio que ha pedido el propio dispositivo (el vigilante o `disable --reboot`).
after_device_reboot() {
  local old_boot="$1" down=$((SECONDS + 300))
  until [[ "$(adb get-state 2>/dev/null)" != device ]]; do
    (( SECONDS > down )) && { echo "el dispositivo no se reinició" >> "$out/summary.md"; return 1; }
    sleep 3
  done
  sleep 3
  wait_boot; adb root > /dev/null 2>&1; adb wait-for-device; sleep 4
}

start_guard() { adb shell "NEXUS_MODDIR=$MOD $1 nohup $TOOL guard > /data/local/tmp/guard-$2.out 2>&1 &"; }

# Estado del sistema en un momento dado: qué recientes usa, permisos, enlace y archivos de estado.
snapshot() {
  local tag="$1"
  { echo "## $tag"
    echo "config_recentsComponentName: $(lookup)"
    perm_state "$tag"
    echo "--- nexus-quickstep bound:"; tool bound
    echo "--- nexus-quickstep status:"; tool status
    echo "--- /data/adb/nexus_quickstep:"; dsh "ls -A $STATE_DIR; for f in tripped enabled healthy booting; do [ -f $STATE_DIR/\$f ] && echo \"\$f: \$(cat $STATE_DIR/\$f)\"; done"
    echo "--- watchdog.log:"; dsh "tail -n 12 $STATE_DIR/watchdog.log"
    echo "--- caídas:"; crashes
  } > "$out/snapshot-$tag.txt" 2>&1
  dsh dumpsys activity services "$SERVICE" > "$out/dumpsys-tis-$tag.txt"
}
check() {  # check "descripción" comando...
  local d="$1"; shift
  if "$@" > /dev/null 2>&1; then echo "OK    $d" >> "$out/checks.txt"; else echo "FALLA $d" >> "$out/checks.txt"; fi
}
has() { grep -q -- "$1" "$2" 2>/dev/null; }
: > "$out/checks.txt"

# ---------------------------------------------------------------------------------------------
say "## Fase 4: activar con 'nexus-quickstep enable', aplicar en el arranque y verificar el enlace"
phase_enable_flow() {
  # Nexus escribe quickstep-probe.txt al arrancar y `enable` lo exige: se abre una vez, como haría el usuario.
  dsh monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1
  sleep 8
  tool status > "$out/flow-status-0.txt"
  tool enable > "$out/flow-enable.txt"; echo "exit: $?" >> "$out/flow-enable.txt"
  if ! has "exit: 0" "$out/flow-enable.txt"; then   # la prueba de Nexus puede negarse: se anota y se fuerza para seguir midiendo
    tool enable --force >> "$out/flow-enable.txt"; echo "exit (--force): $?" >> "$out/flow-enable.txt"
  fi
  dsh "ls -A $STATE_DIR" > "$out/flow-state-after-enable.txt"
  lookup > "$out/flow-lookup-before-reboot.txt"          # estático: antes de reiniciar no cambia nada
  emulate_magisk enable
  reboot_device
  start_guard "NEXUS_GUARD_WAIT=240 NEXUS_GUARD_WATCH=60" 1
  settle
  # El enlace puede tardar más de un minuto tras el arranque (se vio 145 s): se espera hasta 4 min antes de medir.
  local w=$((SECONDS + 240)); until tool bound > /dev/null 2>&1; do (( SECONDS > w )) && break; sleep 5; done
  echo "enlace tras ~$((240 - (w - SECONDS))) s" > "$out/bind-wait.txt"
  snapshot enabled
}
cap enable-flow phase_enable_flow
{ echo "enable:"; cat "$out/flow-enable.txt"; echo "estado tras enable: $(paste -sd' ' "$out/flow-state-after-enable.txt")"
  echo "lookup antes de reiniciar: $(cat "$out/flow-lookup-before-reboot.txt")"; echo; cat "$out/snapshot-enabled.txt"; } | tee -a "$out/summary.md"
check "enable pide el overlay (archivo enabled)" has enabled "$out/flow-state-after-enable.txt"
check "antes de reiniciar el overlay aún no es efectivo" bash -c "! grep -q app.nexus.launcher/ '$out/flow-lookup-before-reboot.txt'"
check "tras reiniciar config_recentsComponentName apunta a Nexus" has "config_recentsComponentName: app.nexus.launcher/" "$out/snapshot-enabled.txt"
check "tras reiniciar Nexus tiene los permisos de allow.txt (0 sin conceder)" has "permisos permitidos y no concedidos: 0 " "$out/snapshot-enabled.txt"
check "SystemUI enlazado al servicio de Nexus" has "Quickstep de Nexus: sí" "$out/snapshot-enabled.txt"
check "el vigilante dejó el arranque verificado (healthy)" has "^healthy:" "$out/snapshot-enabled.txt"
check "el marcador booting se borró" bash -c "! grep -q '^booting:' '$out/snapshot-enabled.txt'"
say ""

# ---------------------------------------------------------------------------------------------
say "## Fase 5: gestos con Nexus como proveedor de Quickstep"
S="-module"
cap gestures phase_gestures
cat "$out/gesture-notes$S.txt" | tee -a "$out/summary.md"
S=""
say ""

# ---------------------------------------------------------------------------------------------
say "## Fase 6: deshacer a mano ('nexus-quickstep disable --reboot')"
phase_manual_disable() {
  local ob; ob="$(boot_id)"
  tool disable --reboot > "$out/manual-disable.txt" 2>&1
  after_device_reboot "$ob"
  emulate_magisk disable     # sin 'enabled', post-fs-data.sh no vuelve a montar el APK
  reboot_device
  settle
  snapshot disabled
}
cap manual-disable phase_manual_disable
{ cat "$out/manual-disable.txt"; cat "$out/post-fs-data-disable.txt"; cat "$out/snapshot-disabled.txt"; } | tee -a "$out/summary.md"
check "disable --reboot: post-fs-data.sh no monta el APK" has "montado: no" "$out/post-fs-data-disable.txt"
check "tras disable el sistema vuelve a los recientes de serie" bash -c "grep -q 'config_recentsComponentName: ' '$out/snapshot-disabled.txt' && ! grep -q 'config_recentsComponentName: app.nexus.launcher/' '$out/snapshot-disabled.txt'"
check "tras disable SystemUI ya no se enlaza a Nexus" has "Quickstep de Nexus: NO" "$out/snapshot-disabled.txt"
say ""

# ---------------------------------------------------------------------------------------------
say "## Fase 7: recuperación automática (Nexus no consigue ofrecer el servicio de Quickstep)"
phase_auto_recovery() {
  tool enable > "$out/auto-enable.txt"
  # Nexus no puede ofrecer el servicio: SystemUI no tendrá a qué enlazarse.
  dsh pm disable "$PKG/com.android.quickstep.TouchInteractionService" > "$out/auto-disable-service.txt"
  emulate_magisk auto
  reboot_device
  local ob; ob="$(boot_id)"
  start_guard "NEXUS_GUARD_WAIT=60 NEXUS_GUARD_WATCH=30" 2
  # El vigilante tiene que reiniciar él solo (~60-90 s).
  BOOT_DEADLINE=300 after_device_reboot "$ob"; echo "reinicio por el vigilante: exit $?" > "$out/auto-reboot.txt"
  snapshot after-guard-reboot       # arranque que sigue al reinicio del vigilante
  # El APK sigue en /product porque aquí no hay montaje dinámico: se reproduce el siguiente arranque.
  emulate_magisk after-trip
  reboot_device
  settle
  snapshot after-trip
  tool enable > "$out/auto-enable-again.txt"; echo "exit: $?" >> "$out/auto-enable-again.txt"
  tool enable --force > "$out/auto-enable-force.txt"; echo "exit: $?" >> "$out/auto-enable-force.txt"
  tool disable > /dev/null 2>&1
  dsh pm enable "$PKG/com.android.quickstep.TouchInteractionService" > /dev/null
}
cap auto-recovery phase_auto_recovery
{ cat "$out/auto-reboot.txt"; cat "$out/snapshot-after-guard-reboot.txt"; cat "$out/post-fs-data-after-trip.txt"; cat "$out/snapshot-after-trip.txt"
  echo "--- enable tras el fallo:"; cat "$out/auto-enable-again.txt"; echo "--- enable --force:"; cat "$out/auto-enable-force.txt"; } | tee -a "$out/summary.md"
check "el vigilante reinició el dispositivo" has "exit 0" "$out/auto-reboot.txt"
check "tras el fallo no queda archivo enabled" bash -c "! grep -q '^enabled:' '$out/snapshot-after-guard-reboot.txt'"
check "el vigilante dejó constancia (tripped)" has "^tripped:" "$out/snapshot-after-guard-reboot.txt"
check "tras el fallo los recientes vuelven a los de serie" bash -c "! grep -q 'config_recentsComponentName: app.nexus.launcher/' '$out/snapshot-after-trip.txt'"
check "enable se niega tras el fallo con la misma versión" has "exit: 1" "$out/auto-enable-again.txt"
check "enable --force lo permite" has "exit: 0" "$out/auto-enable-force.txt"
say ""

# ---------------------------------------------------------------------------------------------
say "## Fase 8: protección contra bucle de arranque (marcador booting)"
phase_bootloop() {
  dsh "rm -f $STATE_DIR/tripped $STATE_DIR/tripped.meta; touch $STATE_DIR/enabled $STATE_DIR/booting"
  adb shell rm -f "$MOD/system/product/overlay/NexusRecentsOverlay.apk"
  dsh "sh $MOD/post-fs-data.sh; echo rc=\$?; ls -A $STATE_DIR; cat $STATE_DIR/tripped; ls $MOD/system/product/overlay" > "$out/bootloop-post-fs-data.txt"
  tool enable > "$out/bootloop-enable.txt"; echo "exit: $?" >> "$out/bootloop-enable.txt"
}
cap bootloop phase_bootloop
cat "$out/bootloop-post-fs-data.txt" "$out/bootloop-enable.txt" | tee -a "$out/summary.md"
check "bucle de arranque: post-fs-data.sh quita enabled" bash -c "! grep -qx enabled '$out/bootloop-post-fs-data.txt'"
check "bucle de arranque: no copia el APK" bash -c "! grep -q NexusRecentsOverlay '$out/bootloop-post-fs-data.txt'"
check "bucle de arranque: enable exige --force" has "exit: 1" "$out/bootloop-enable.txt"
say ""

say "## Resumen de comprobaciones (emulador Android $(grep -o 'ro.build.version.release=.*' "$out/device-info.txt"))"
cat "$out/checks.txt" | tee -a "$out/summary.md"
say ""

adb logcat -d -b main,system,crash 2>/dev/null | gzip -9 > "$out/logcat-full.txt.gz"
say "(fin)"
