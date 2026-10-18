#!/system/bin/sh
# Vigilante de Quickstep: se ejecuta en cada arranque (Magisk, KernelSU y APatch).
#
# Si el overlay de recientes está activo, comprueba que SystemUI llega a enlazarse con el servicio
# de Quickstep de Nexus y, si no, lo desactiva y reinicia SystemUI. No hace nada con el overlay
# desactivado (el estado de serie). La lógica está en `nexus-quickstep guard`.
MODDIR=${0%/*}
"$MODDIR/system/bin/nexus-quickstep" guard >/dev/null 2>&1 &
