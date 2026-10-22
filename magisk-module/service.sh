#!/system/bin/sh
# Vigilante de Quickstep: se ejecuta en cada arranque (Magisk, KernelSU y APatch).
#
# Si el overlay de recientes está aplicado, comprueba que SystemUI llega a enlazarse con el servicio
# de Quickstep de Nexus y, si no, quita el archivo `enabled` y reinicia (el siguiente arranque va sin
# overlay). No hace nada con el overlay desactivado (el estado de serie). La lógica está en
# `nexus-quickstep guard`; post-fs-data.sh es quien aplica el overlay.
MODDIR=${0%/*}
"$MODDIR/system/bin/nexus-quickstep" guard >/dev/null 2>&1 &
# Mantiene la app al día con el módulo: si la instalada es más antigua que el APK del módulo, la actualiza
# (cuando el sistema ya responde). Es una actualización normal: conserva los datos y no baja de versión.
"$MODDIR/system/bin/nexus-quickstep" sync-apk --wait >/dev/null 2>&1 &
