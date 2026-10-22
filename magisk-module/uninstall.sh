#!/system/bin/sh
# Se ejecuta al quitar el módulo desde el gestor de root (Magisk, KernelSU y APatch), antes de borrarlo.
# Deja el sistema como estaba: si Nexus cambió la configuración de hybrid_mount (plan A), se restaura,
# y se borra el estado del módulo. La carpeta Nexus-Logs del almacenamiento no se toca.
MODDIR=${0%/*}
STATE="${NEXUS_STATE_DIR:-/data/adb/nexus_quickstep}"
TOOL="$MODDIR/system/bin/nexus-quickstep"

if [ -f "$STATE/hybrid-changed" ] && [ -f "$TOOL" ]; then
  sh "$TOOL" umount-fix restore >/dev/null 2>&1
fi
rm -rf "$STATE"
exit 0
