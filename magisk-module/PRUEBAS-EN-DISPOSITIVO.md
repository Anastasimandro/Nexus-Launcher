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

## 7. Mapa de montajes (diagnóstico de solo lectura)

Sirve para ver, **en un arranque normal y sin activar el overlay**, qué ve cada proceso de `/product/overlay`, de los montajes del módulo y de `Nexus.apk`. Es la información que faltó cuando zygote abortó al reabrir el APK del overlay. No monta, no desmonta, no escribe configuración ni toca `enabled`: solo guarda sus informes en `/data/adb/nexus_quickstep/` (`mounts-map.txt` y `mounts-probe.txt`).

**Cómo ejecutarlo:** WebUI → pestaña **Registros** → **Mapa de montajes** (con el móvil desbloqueado y el arranque terminado). Sin WebUI: `su -c nexus-quickstep mounts-map`. **Copiar resultado completo** deja todo el texto en el portapapeles; `diag --export` también incluye `mounts-map.txt` si existe.

**Qué sale:** una línea `proc|…` por proceso (init, zygote64, zygote, webview_zygote, app_zygote, system_server, systemui, app.nexus.launcher y una app normal):

| Columna | Qué es |
|---|---|
| pid, nombre, uid | El proceso. Un proceso que no está en marcha, o que no se puede leer, sale como `n/d` |
| ns | Inodo de su espacio de montajes (`/proc/<pid>/ns/mnt`) |
| frente a init | `igual` o `distinto` respecto al ns de init |
| `/product/overlay` | Líneas de su `mountinfo` que contienen esa ruta |
| módulo | Líneas de su `mountinfo` que contienen el **id del módulo** (`nexus_launcher_systemless`). Se busca por el id y no por la ruta completa porque `mountinfo` muestra la ruta relativa al dispositivo (`/adb/modules/…`) o la completa en `lowerdir=` |
| ve Nexus.apk | `sí` / `no` / `n/d` según `stat` de `/proc/<pid>/root/product/priv-app/Nexus/Nexus.apk`. `n/d` si no se puede mirar dentro de la raíz del proceso o la lectura se cortó por tiempo |

Cada lectura lleva un límite de tiempo (3 s; la lista de procesos 8 s; la de apps de terceros 12 s). Después viene la **sonda detallada**, que es `capture_probe` (la del arranque que falla) reutilizada, con tope de 25 s.

La «app normal» es la primera app de terceros en marcha; si no hay ninguna, la primera con uid de app (10000–19999) cuyo nombre no parezca de sistema. Su nombre sale en la fila.

**Qué mirar:** si `zygote64`/`zygote` ven el módulo y `Nexus.apk` de forma distinta a `init` o a una app normal, esa diferencia es lo que rompía la reapertura del overlay. Si una fila sale con `n/d` en todo, no se pudo leer ese proceso (SELinux o límite de tiempo): dilo, no lo interpretes como «no ve nada».

**`diag` ahora incluye:** versiones de KernelSU (`ksud`), lista de módulos de root con id, nombre, versión y estado (NeoZygisk y hybrid_mount marcados), `disable_umount` y `default_mode` de hybrid_mount (solo lectura) y hasta 40 líneas `avc: denied` de Nexus o `priv_app` de `logcat` y de `dmesg`.

**Qué enviar:** el texto de **Copiar resultado completo** y el informe de `diag` (**Guardar informe en Descargas**).

**Vuelta atrás:** no hay nada que deshacer (no cambia el sistema). Si el módulo da algún problema, `touch /data/adb/modules/nexus_launcher_systemless/disable` desde recovery.
