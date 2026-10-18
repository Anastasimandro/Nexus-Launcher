#!/system/bin/sh
# shellcheck shell=sh disable=SC2034
#
# Instalador de "Nexus Launcher Systemless Overlay" (Magisk, KernelSU y APatch).
#
# El gestor del módulo ya ha descomprimido el zip en $MODPATH y define API, ui_print,
# abort y set_perm_recursive. Aquí solo se comprueba que el sistema es compatible y que el
# zip está completo; el módulo en sí son los archivos de system/product.
#
# Estructura del zip (la genera .github/scripts/build-magisk-module.sh):
#   system/product/priv-app/Nexus/Nexus.apk
#   system/product/etc/permissions/privapp-permissions-nexus.xml
#   system/product/overlay/NexusRecentsOverlay.apk   (desactivado hasta `nexus-quickstep enable`)
#   system/bin/nexus-quickstep
#   service.sh                                       (vigilante de Quickstep)

MIN_API=34
APK="$MODPATH/system/product/priv-app/Nexus/Nexus.apk"
ALLOWLIST="$MODPATH/system/product/etc/permissions/privapp-permissions-nexus.xml"
OVERLAY="$MODPATH/system/product/overlay/NexusRecentsOverlay.apk"
TOOL="$MODPATH/system/bin/nexus-quickstep"

ui_print "- Nexus Launcher Systemless Overlay"
ui_print "- Android API $API"

if [ "$API" -lt "$MIN_API" ]; then
  abort "! Hace falta Android 14 o superior (API $MIN_API); este sistema es API $API"
fi

[ -d /product ] || abort "! Este sistema no tiene la partición /product"
[ -f "$APK" ] || abort "! El zip no lleva Nexus.apk: está incompleto"
[ -f "$ALLOWLIST" ] || abort "! El zip no lleva la lista de permisos: está incompleto"
[ -f "$OVERLAY" ] || abort "! El zip no lleva el overlay de recientes: está incompleto"
[ -f "$TOOL" ] || abort "! El zip no lleva nexus-quickstep: está incompleto"
[ -f "$MODPATH/service.sh" ] || abort "! El zip no lleva service.sh: está incompleto"

set_perm_recursive "$MODPATH/system/product" 0 0 0755 0644
set_perm "$TOOL" 0 2000 0755

ui_print "- Nexus pasará a ser app privilegiada de /product"
ui_print "- Reinicia una vez para que Android lo registre"
ui_print "- Después actualiza Nexus como siempre (APK normal):"
ui_print "  conserva los permisos sin volver a flashear el módulo"
ui_print "- Gestos del sistema en Nexus (opcional, desactivado):"
ui_print "  tras reiniciar, abre Nexus una vez y, con root, ejecuta"
ui_print "  nexus-quickstep status   (para ver si este Android lo admite)"
ui_print "  nexus-quickstep enable   (y reinicia)"
ui_print "  nexus-quickstep bound    (comprueba que SystemUI se enlazó a Nexus)"
ui_print "  Si en el arranque SystemUI no se enlaza a Nexus, el módulo"
ui_print "  desactiva el overlay solo. A mano: nexus-quickstep disable"
