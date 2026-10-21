#!/system/bin/sh
# Aplica el overlay de recientes en el arranque, antes de PackageManagerService.
#
# Por qué aquí: el sistema decide qué paquete es el de recientes (config_recentsComponentName) una
# sola vez, cuando arranca PackageManagerService, y solo entonces concede a ese paquete los
# permisos `signature|recents`. Un overlay que se activa después no sirve (probado en Android 17).
# Magisk, KernelSU y APatch ejecutan este script antes de montar system/ del módulo, así que el
# APK copiado aquí entra en el montaje de ese arranque.
#
# Solo se copia si existe /data/adb/nexus_quickstep/enabled (lo crea `nexus-quickstep enable`).
# Sin ese archivo el módulo no cambia nada.
#
# Protección contra bucle de arranque: al aplicar el overlay se deja el marcador `booting`; el
# vigilante (service.sh) lo borra cuando el sistema ha arrancado y SystemUI se ha enlazado a Nexus.
# Si en el siguiente arranque el marcador sigue ahí, el anterior no terminó: se quita el archivo
# `enabled`, se anota y ese arranque va sin overlay.
#
# Registro y reinicio automático: al aplicar el overlay se arranca (en segundo plano) un registro
# del arranque (logcat y dmesg) y un temporizador que reinicia el móvil si el sistema no termina de
# arrancar en unos minutos. En el arranque siguiente, que ya va sin overlay, ese registro se guarda
# en /data/adb/nexus_quickstep/failed-boot/ (con pstore si el kernel lo conserva) para exportarlo
# desde el WebUI del módulo. Todo esto vive en `nexus-quickstep capture`.
MODDIR=${0%/*}
STATE="${NEXUS_STATE_DIR:-/data/adb/nexus_quickstep}"
SRC="$MODDIR/overlay-static/NexusRecentsOverlay.apk"
DST_DIR="$MODDIR/system/product/overlay"
DST="$DST_DIR/NexusRecentsOverlay.apk"
TOOL="$MODDIR/system/bin/nexus-quickstep"

mkdir -p "$STATE"
log() { echo "$(date '+%Y-%m-%d %H:%M:%S') post-fs-data: $*" >> "$STATE/watchdog.log"; }

# Siempre se parte de "sin overlay": si no hay archivo `enabled`, no queda nada montado.
rm -f "$DST"

if [ -f "$STATE/booting" ]; then
  # Se conserva el registro del arranque que no terminó antes de que nada lo borre.
  [ -f "$TOOL" ] && sh "$TOOL" capture keep >/dev/null 2>&1
  rm -f "$STATE/enabled" "$STATE/booting"
  # Sin fecha: en este punto del arranque el reloj aún no está ajustado (sale 1970).
  reason=$(cat "$STATE/abort-reason" 2>/dev/null)
  echo "el arranque anterior con el overlay no terminó${reason:+ ($reason)}" > "$STATE/tripped"
  rm -f "$STATE/abort-reason"
  echo "version=bootloop" > "$STATE/tripped.meta"
  log "el arranque anterior no terminó: overlay desactivado"
  exit 0
fi

[ -f "$STATE/enabled" ] || exit 0
[ -f "$MODDIR/disable" ] && exit 0
if [ ! -f "$SRC" ]; then
  log "falta $SRC: no se aplica el overlay"
  exit 0
fi

mkdir -p "$DST_DIR"
if cp "$SRC" "$DST" && chmod 0644 "$DST"; then
  chcon u:object_r:system_file:s0 "$DST" 2>/dev/null
  touch "$STATE/booting"
  log "overlay copiado a $DST"
  # En segundo plano y sin enganchar la salida: este script no debe esperar al registro.
  [ -f "$TOOL" ] && sh "$TOOL" capture start >/dev/null 2>&1
else
  rm -f "$DST"
  log "no se pudo copiar el overlay: arranque sin overlay"
fi
exit 0
