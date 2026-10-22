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
#   overlay-static/NexusRecentsOverlay.apk           (FUERA de system/: no se monta de serie)
#   system/bin/nexus-quickstep
#   post-fs-data.sh                                  (monta el overlay solo si se pidió con `enable`)
#   service.sh                                       (vigilante de Quickstep)
#   uninstall.sh                                     (al quitar el módulo: restaura hybrid_mount y borra el estado)
#   webroot/index.html                               (WebUI: estado y control desde el gestor de root)

MIN_API=34
APK="$MODPATH/system/product/priv-app/Nexus/Nexus.apk"
ALLOWLIST="$MODPATH/system/product/etc/permissions/privapp-permissions-nexus.xml"
OVERLAY="$MODPATH/overlay-static/NexusRecentsOverlay.apk"
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
[ -f "$MODPATH/post-fs-data.sh" ] || abort "! El zip no lleva post-fs-data.sh: está incompleto"
[ -f "$MODPATH/uninstall.sh" ] || abort "! El zip no lleva uninstall.sh: está incompleto"
[ -f "$MODPATH/webroot/index.html" ] || abort "! El zip no lleva el WebUI (webroot/index.html): está incompleto"
# El overlay no debe estar en system/: si lo estuviera se aplicaría en todos los arranques.
[ ! -e "$MODPATH/system/product/overlay" ] || abort "! El zip lleva un overlay montable de serie: está mal construido"

set_perm_recursive "$MODPATH/system/product" 0 0 0755 0644
set_perm "$TOOL" 0 2000 0755
set_perm "$MODPATH/post-fs-data.sh" 0 0 0755
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755
set_perm_recursive "$MODPATH/webroot" 0 0 0755 0644

ui_print "- Nexus pasará a ser app privilegiada de /product"
ui_print "- Reinicia una vez para que Android lo registre"
ui_print "- Después actualiza Nexus como siempre (APK normal):"
ui_print "  conserva los permisos sin volver a flashear el módulo"
ui_print "- Gestos del sistema en Nexus (opcional, desactivado):"
ui_print "  tras reiniciar, abre Nexus una vez y pulsa el módulo en el"
ui_print "  gestor de root (WebUI): estado, activar, desactivar y diagnóstico."
ui_print "  Si el arranque con Quickstep falla, el módulo reinicia solo y el"
ui_print "  arranque siguiente va sin él, con un registro para diagnosticar."
ui_print "  (Sin WebUI: nexus-quickstep status | enable | disable --reboot)"
