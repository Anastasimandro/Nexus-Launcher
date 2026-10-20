#!/usr/bin/env bash
# Instala una imagen de sistema de Android (google_apis, con root) y arranca un emulador.
# Autodetecta el nombre de la imagen para la API pedida: no se supone.
#
# Uso: emulator-up.sh API RESULTADOS
set -euo pipefail

api="${1:?falta la API}"
out="${2:?falta RESULTADOS}"
mkdir -p "$out"

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
export ANDROID_HOME="$sdk" ANDROID_SDK_ROOT="$sdk"
sm="$(ls "$sdk"/cmdline-tools/*/bin/sdkmanager | sort -V | tail -n 1)"
avdm="$(ls "$sdk"/cmdline-tools/*/bin/avdmanager | sort -V | tail -n 1)"
export PATH="$sdk/platform-tools:$sdk/emulator:$PATH"

yes | "$sm" --licenses > /dev/null 2>&1 || true
"$sm" --list 2>/dev/null > "$out/sdkmanager-list.txt" || true
grep -oE 'system-images;android-[0-9.]+[^ |]*' "$out/sdkmanager-list.txt" | sort -u > "$out/system-images.txt" || true

pkg="$(grep -E "^system-images;android-${api}(\.[0-9]+)?;google_apis;x86_64$" "$out/system-images.txt" | sort -V | tail -n 1 || true)"
if [[ -z $pkg ]]; then
  echo "::error title=Sin imagen::No hay imagen google_apis x86_64 para la API ${api}. Imágenes de esa API:"
  grep -E "android-${api}" "$out/system-images.txt" || echo "(ninguna)"
  exit 1
fi
echo "Imagen: $pkg" | tee "$out/image.txt"

timeout 1200 "$sm" --install "$pkg" "emulator" "platform-tools" > "$out/sdkmanager-install.txt" 2>&1 \
  || { echo "::error title=sdkmanager::fallo o tiempo agotado instalando $pkg"; tail -n 20 "$out/sdkmanager-install.txt"; exit 1; }
export ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.android/avd}"
mkdir -p "$ANDROID_AVD_HOME"
"$avdm" list device -c > "$out/avd-devices.txt" 2>&1 || true
# Se comprueba que el AVD exista de verdad: avdmanager ha salido sin error y sin crearlo.
for dev in pixel_6 pixel_5 ""; do
  echo no | "$avdm" create avd -n qs -k "$pkg" ${dev:+-d "$dev"} --force > "$out/avd-create.txt" 2>&1 || true
  [[ -f $ANDROID_AVD_HOME/qs.ini ]] && break
done
if [[ ! -f $ANDROID_AVD_HOME/qs.ini ]]; then
  echo "::error title=AVD::avdmanager no creó el AVD (ANDROID_AVD_HOME=$ANDROID_AVD_HOME)"
  tr '\r' '\n' < "$out/avd-create.txt" | tail -n 15
  ls -la "$ANDROID_AVD_HOME" "$HOME/.android" 2>&1 | head -20
  exit 1
fi
echo "AVD creado en $ANDROID_AVD_HOME" | tee -a "$out/image.txt"

# La partición /data por defecto es pequeña: con el módulo y el remount de overlayfs se llenaba
# (ENOSPC), no se podían escribir los idmaps y system_server entraba en bucle de caídas.
cfg="$ANDROID_AVD_HOME/qs.avd/config.ini"
if [[ -f $cfg ]]; then
  sed -i '/^disk.dataPartition.size=/d' "$cfg"
  echo "disk.dataPartition.size=8G" >> "$cfg"
  cp "$cfg" "$out/avd-config.ini"
fi
df -h / | tail -1

# Los procesos en segundo plano de un paso pueden morir con él: se ejecuta todo en el mismo paso.
nohup "$sdk/emulator/emulator" -avd qs -no-window -no-audio -no-boot-anim -no-snapshot \
  -gpu "${EMU_GPU:-swiftshader_indirect}" -memory 4096 -cores 3 -writable-system \
  > "$out/emulator.log" 2>&1 &
emu_pid=$!
echo "emulador lanzado (pid $emu_pid)"
ls -l /dev/kvm > "$out/kvm.txt" 2>&1 || true

adb start-server > /dev/null 2>&1
deadline=$((SECONDS + 900))
# Sin `adb wait-for-device` a secas: si el emulador muere, bloquearía el paso hasta el límite del job.
until adb get-state 2>/dev/null | grep -q device; do
  if ! kill -0 "$emu_pid" 2>/dev/null || (( SECONDS > deadline )); then
    echo "::error title=Emulador::El emulador no apareció en adb (¿murió o falta KVM?)"
    tail -n 40 "$out/emulator.log" || true
    exit 1
  fi
  sleep 5
done
until [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
  if ! kill -0 "$emu_pid" 2>/dev/null || (( SECONDS > deadline )); then
    echo "::error title=Emulador::No terminó de arrancar (murió o pasaron 15 minutos)"
    tail -n 40 "$out/emulator.log" || true
    exit 1
  fi
  sleep 5
done
sleep 5
echo "Emulador listo: $(adb shell getprop ro.build.version.release | tr -d '\r') (API $(adb shell getprop ro.build.version.sdk | tr -d '\r'))"
