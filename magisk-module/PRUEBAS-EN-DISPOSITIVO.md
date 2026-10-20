# Protocolo de prueba en el dispositivo (Quickstep desde el módulo)

Nada de esto se ha podido ejecutar todavía en un móvil real. Cada prueba lleva lo que debe ocurrir y qué guardar. Si una falla, no sigas: haz la recuperación y envía lo que se pide al final.

## 0. Preparación

- Un PC con `adb` y el móvil con depuración USB, **antes** de activar nada. Que el PC pueda ejecutar `adb shell su -c ...`.
- Flashea el módulo de la release, reinicia, abre Nexus una vez (escribe la comprobación de compatibilidad) y ponlo como launcher de Inicio.
- Sin el overlay, todo debe ir como siempre. `su -c nexus-quickstep status` debe decir `Pedido (enabled): no`.

## 1. Comandos exactos

| Qué | Comando (como root) |
|---|---|
| Estado | `nexus-quickstep status` |
| Activar (pide el overlay; se aplica al reiniciar) | `nexus-quickstep enable` y reiniciar |
| ¿SystemUI enlazado a Nexus? (exit 0 = sí) | `nexus-quickstep bound` |
| Comprobación manual del enlace | `dumpsys activity services app.nexus.launcher/com.android.quickstep.TouchInteractionService` → `received=true` y SystemUI como cliente (fallo: `binder=null received=false`) |
| ¿Qué proveedor de recientes usa el sistema? | `cmd overlay lookup --user 0 android android:string/config_recentsComponentName` |
| ¿Se montó el APK? | `ls -l /product/overlay/NexusRecentsOverlay.apk` y `tail /data/adb/nexus_quickstep/watchdog.log` |
| Deshacer | `nexus-quickstep disable --reboot` |
| Saltar la comprobación o el bloqueo del vigilante | `nexus-quickstep enable --force` |

`cmd overlay list` **no** muestra el overlay estático: no lo uses para saber si está aplicado.

## 2. Recuperación (aprende esto antes de activar)

1. **Normal:** `su -c nexus-quickstep disable --reboot`.
2. **Sin gestos pero con el sistema arrancado:** el vigilante lo desactiva solo y reinicia si SystemUI no se enlaza a Nexus en 180 s o pierde el enlace 60 s seguidos en los primeros 300 s.
3. **Bucle de arranque:** `post-fs-data.sh` deja un marcador `booting`; si el arranque siguiente lo encuentra, quita el overlay. Con eso un solo reinicio fallido basta para volver.
4. **Si el móvil no arranca ni así:** desde el recovery o con `adb` en modo seguro, `touch /data/adb/modules/nexus_launcher_systemless/disable` (módulo desactivado en Magisk, KernelSU y APatch) o borra `/data/adb/nexus_quickstep/enabled`. Reinicia. No hace falta reinstalar la ROM: el overlay solo existe en el montaje del módulo.

## 3. Verificación previa (antes de tocar gestos)

| # | Prueba | Esperado |
|---|---|---|
| P1 | `enable`, reiniciar, `status` | `Overlay efectivo: sí`; `config_recentsComponentName` = `app.nexus.launcher/com.android.quickstep.RecentsActivity` |
| P2 | `watchdog.log` | `post-fs-data: overlay copiado…` y, a los pocos segundos, `SystemUI enlazado al servicio de Nexus tras Ns` |
| P3 | `nexus-quickstep bound` | exit 0 |
| P4 | `dumpsys package app.nexus.launcher` | permisos `START_TASKS_FROM_RECENTS`, `CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS` y compañía concedidos |
| P5 | `logcat -b crash` | sin `FATAL EXCEPTION` de `app.nexus.launcher` |

Si P1 dice que no es efectivo: o hay otro overlay de la imagen con más prioridad, o el gestor de módulos no montó `system/product/overlay` antes de que arrancara `PackageManagerService` (en KernelSU reciente puede hacer falta un metamódulo de montaje). Envía `watchdog.log` y `ls -l /product/overlay`.

## 4. Gestos (captura `adb logcat -b all -v threadtime` durante todo el bloque)

| # | Prueba | Esperado |
|---|---|---|
| G1 | Abre una app, gesto de Inicio **rápido** | Vuelve a Nexus; sin cierre brusco |
| G2 | Gesto de Inicio **lento**, parando a mitad | La ventana de la app **sigue el dedo** y se reduce; al soltar, termina en su icono |
| G3 | Subir y volver al punto de partida | Cancela: la app sigue en primer plano |
| G4 | Subir, mantener y soltar (Recientes) | Abre Recientes; elegir una app la abre con animación |
| G5 | Interrumpir el gesto con otro dedo / pantalla apagada a mitad | No se queda una ventana congelada ni un icono fantasma |
| G6 | Atrás desde una app y desde Nexus | Atrás de siempre |
| G7 | Gesto de Inicio con la pantalla en horizontal y de vuelta | Funciona en ambas orientaciones |
| G8 | Bloquear y desbloquear; gesto de Inicio justo después | Funciona; `bound` sigue en exit 0 |
| G9 | Abrir una app de pantalla dividida / flotante, gesto de Inicio | Sin cierre de Nexus ni de SystemUI |
| G10 | Cambio de modo de navegación (gestos ↔ 3 botones) y vuelta | Sin cierre; los gestos vuelven |

## 5. Recuperación ante fallo (provócalo a propósito)

| # | Prueba | Esperado |
|---|---|---|
| R1 | Con el overlay activo: `pm disable app.nexus.launcher/com.android.quickstep.TouchInteractionService`, reiniciar | En ~3 minutos el vigilante reinicia solo; arranca sin overlay; `watchdog.log` explica por qué; `tripped` presente |
| R2 | Tras R1: `pm enable …TouchInteractionService`, `enable` | Se **niega** (mismo Nexus); `enable --force` lo permite |
| R3 | `disable --reboot` | Vuelve a los recientes de serie; `config_recentsComponentName` ya no apunta a Nexus |
| R4 | `touch /data/adb/nexus_quickstep/booting` con `enabled` presente y reiniciar | El arranque va sin overlay; `tripped` con `version=bootloop` |

## 6. Qué enviar si algo falla

`nexus-quickstep status`, `/data/adb/nexus_quickstep/watchdog.log`, `adb logcat -b all -v threadtime -d` (justo después del fallo), `dumpsys activity services app.nexus.launcher/com.android.quickstep.TouchInteractionService`, el informe de errores de Nexus (`quickstepProbe:`) y la versión exacta de la ROM (`getprop ro.build.fingerprint`).
