# Módulo Magisk / KernelSU de Nexus

Plantilla del módulo `nexus_launcher_systemless` («Nexus Launcher Systemless Overlay»). Convierte a Nexus en app **privilegiada** de `/product` sin tocar `/system` (systemless), para que pueda pedir sus propias transiciones de ventana sin depender de Shizuku.

**No se empaqueta a mano.** Cada release lo genera `.github/scripts/build-magisk-module.sh` con el APK recién compilado y publica `Nexus-MagiskModule-vX.Y.Z.zip` junto a `Nexus-vX.Y.Z.apk`.

## Qué lleva el zip

```
module.prop                                              version y versionCode = los del APK
customize.sh                                             comprueba Android 14+, /product y que el zip está completo
META-INF/com/google/android/{update-binary,updater-script}
system/product/priv-app/Nexus/Nexus.apk                  el APK de esa release
system/product/etc/permissions/privapp-permissions-nexus.xml   lista blanca de permisos (generada)
overlay-static/NexusRecentsOverlay.apk                   overlay ESTÁTICO que hace de Nexus el proveedor de Quickstep (fuera de system/: no se monta de serie)
post-fs-data.sh                                          copia ese overlay a /product/overlay solo si se pidió con `enable`
service.sh                                               vigilante: si SystemUI no se enlaza a Nexus, lo desactiva y reinicia
system/bin/nexus-quickstep                               pide, quita y comprueba el overlay
```

En este directorio están `module.prop` (con `@VERSION@` y `@VERSION_CODE@`), `customize.sh`, `META-INF/`, las listas de `permissions/`, el código del overlay en `overlay/` y la herramienta en `tools/`. El APK y el XML los pone el script, que también compila y firma el overlay (`.github/scripts/build-recents-overlay.sh`).

## Instalar

1. Descarga `Nexus-MagiskModule-vX.Y.Z.zip` de la última release.
2. Magisk o KernelSU → Módulos → instalar desde almacenamiento → elige el zip.
3. Reinicia una vez.
4. Abre Nexus. En el informe de errores aparece la línea `systemPrivileges: privilegiado, …` si todo fue bien (también en logcat, etiqueta `NexusRootSystem`).

Flashea el módulo de la release **más reciente**: es el que lleva el APK con el `versionCode` más alto. Si tienes instalada una versión más nueva que la del módulo, Android puede ignorar la copia del módulo hasta que flashees uno igual o más nuevo.

## Actualizar sin volver a flashear (hot-swap)

Con el módulo puesto, Nexus es una app de sistema. Cuando instalas un APK nuevo de Nexus de la forma normal (queda en `/data/app`), Android lo trata como actualización de esa app de sistema y le aplica la lista de permisos del módulo, sin volver a flashear.

Para que Android lo acepte como actualización de la app de sistema hacen falta las mismas condiciones que para cualquier actualización: mismo paquete (`app.nexus.launcher`), **misma firma** y `versionCode` mayor. Los APK de las releases cumplen las tres.

Hay que volver a flashear el módulo cuando una versión nueva pida **permisos privilegiados nuevos**: la lista blanca la genera cada release a partir de lo que pide ese APK, pero no se garantiza que Android conceda a una actualización un permiso privilegiado que la copia del módulo no pedía.

## Qué permisos concede

`permissions/allow.txt` es la lista de los que se conceden. Son los que Nexus declara en `quickstep/AndroidManifest.xml`, con dos imprescindibles:

- `START_TASKS_FROM_RECENTS`: sin él, `overrideTaskTransition` falla con `SecurityException`.
- `CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS`: para las animaciones remotas.

Todo lo demás privilegiado que pide el APK (`WRITE_SECURE_SETTINGS`, `MANAGE_USERS`, `FORCE_STOP_PACKAGES`…) se **deniega de forma explícita** en el XML. Es a propósito: un permiso privilegiado de un priv-app que no aparece ni concedido ni denegado es una violación de la lista blanca y Android puede negarse a arrancar. `permissions/not-privileged.txt` lista los permisos normales/peligrosos (`INTERNET`, `READ_CONTACTS`…) que el script no toca. Si una librería nueva trae un permiso normal, el script lo denegará y lo avisará: añádelo a `not-privileged.txt`.

Que un permiso esté en la lista **no implica que tu ROM lo conceda**: Android solo concede los que en esa ROM tienen nivel `privileged` (o `recents`). Lo que se concedió de verdad lo informa `RootSystemManager` (`concedidos=N/19`, `faltan=…`).

## Gestos del sistema en Nexus (proveedor de Quickstep)

Que Nexus pueda pedir transiciones propias (lo de arriba) **no** hace que reciba el gesto de Inicio: eso lo decide SystemUI, que enlaza con el servicio `QUICKSTEP_SERVICE` del paquete que dice el recurso `config_recentsComponentName` del framework. Mientras apunte al launcher del sistema, el gesto es suyo y Nexus solo anima después.

El módulo trae un overlay de recursos que cambia **solo esa cadena** a `app.nexus.launcher/com.android.quickstep.RecentsActivity`. No toca SystemUI ni ninguna comprobación de seguridad y es el mismo mecanismo que usa QuickSwitch, sin depender de él. Con él, SystemUI enlaza con `TouchInteractionService` de Nexus y le entrega los eventos del gesto.

**Por qué es estático.** `PackageManagerService` calcula el paquete de recientes una sola vez al arrancar, y solo a ese paquete le concede los permisos `recents`. Un overlay normal (`cmd overlay enable`) entra después y llega tarde: SystemUI se enlaza a Nexus pero Nexus no tiene permisos y el servicio falla (se comprobó en un emulador). Por eso el overlay es `isStatic` con prioridad 9999 (el máximo que admite `PackageManager`; con más lo rechaza) y tiene que estar en `/product/overlay` antes de ese arranque. Para que flashear el módulo no cambie nada, el APK va **fuera** de `system/` y `post-fs-data.sh` lo copia al montaje solo si existe `/data/adb/nexus_quickstep/enabled`.

Viene **desactivado**. Se controla desde el **WebUI del módulo** (`webroot/`): en KernelSU o APatch, Módulos → pulsar el módulo; en Magisk, con una app como MMRL o KsuWebUI. Muestra el estado, activa, desactiva, reinicia y exporta un diagnóstico a Descargas (`Nexus-diagnostico.tar.gz`). Sin WebUI, los mismos comandos (como root):

```
su -c nexus-quickstep status            # overlay efectivo, comprobación de Nexus, estado del vigilante
su -c nexus-quickstep enable            # lo pide; REINICIA para aplicarlo
su -c nexus-quickstep bound             # ¿SystemUI está enlazado al servicio de Nexus? (exit 0 = sí)
su -c nexus-quickstep disable --reboot  # lo quita y reinicia: el gesto vuelve al launcher del sistema
su -c nexus-quickstep diag --export     # informe + registros del último arranque fallido, en Descargas
su -c nexus-quickstep logs              # copia todos los registros a /sdcard/Nexus-Logs
```

Abre Nexus al menos una vez antes de `enable`: al arrancar comprueba si este Android tiene lo que usa Quickstep y lo deja en `files/quickstep-probe.txt`. **`enable` se niega si falta algo** o si el vigilante ya desactivó el overlay con esta misma versión de Nexus; `enable --force` se salta ambas cosas.

Comprobación a mano de que SystemUI está enlazado: `dumpsys activity services app.nexus.launcher/com.android.quickstep.TouchInteractionService` debe mostrar `received=true` con SystemUI como cliente (un fallo muestra `binder=null received=false`), y `cmd overlay lookup --user 0 android android:string/config_recentsComponentName` debe devolver el componente de Nexus.

### Recuperación

- **El vigilante** (`service.sh` → `nexus-quickstep guard`): con el overlay aplicado espera a que SystemUI se enlace a Nexus; si no pasa en 180 s, o el enlace se pierde 60 s seguidos durante los 300 s siguientes, borra `enabled`, deja constancia (`tripped`) y **reinicia una vez**. El arranque siguiente va sin overlay. No depende de que Nexus arranque. Después, `enable` exige `--force` para esa misma versión de Nexus.
- **Bucle de arranque:** `post-fs-data.sh` deja un marcador `booting` y el vigilante lo borra al verificar el arranque. Si encuentra el marcador al arrancar, es que el arranque anterior con el overlay no terminó: quita `enabled` y arranca sin overlay.
- **Carpeta `Nexus-Logs`:** con el overlay aplicado, el módulo copia los registros del arranque cada 10 s a `/sdcard/Nexus-Logs/` en cuanto el almacenamiento está disponible (no lo está hasta que desbloqueas el móvil tras arrancar; antes se guardan en `/data/adb/nexus_quickstep/`). `RESUMEN.txt` dice en claro si el arranque fue bien o falló; `arranque-actual/` es este arranque y `arranque-fallido/` el último que no terminó (se copia al desbloquear tras el reinicio que arranca sin overlay).
- **Fallo conocido (visto en un Lunaris/Android 17 con KernelSU):** zygote mantiene abierto el APK del overlay y lo reabre en cada proceso nuevo; si el archivo del módulo no se ve desde ahí (el gestor de root desmonta los montajes de módulos para las apps), **todas las apps abortan**, SystemUI incluida, y el arranque no termina (`Failed open(/product/overlay/NexusRecentsOverlay.apk) … No such file`). El módulo lo detecta en el registro y reinicia solo sin overlay en unos segundos (comprobado en el móvil: ~7 s). Justo antes de reiniciar guarda `probe.txt` (en `arranque-fallido/`): qué ve de `/product/overlay` y de los montajes cada proceso (init, zygote, system_server), para saber quién deja de ver el archivo.
- **Arranque colgado:** al aplicar el overlay, `post-fs-data.sh` arranca un registro (logcat y dmesg) y un temporizador: si `sys.boot_completed` no llega en 6 minutos, el móvil se reinicia solo. Ese reinicio arranca sin overlay y guarda el registro en `/data/adb/nexus_quickstep/failed-boot/` (con pstore si el kernel lo conserva); el WebUI lo avisa y lo exporta. Un cuelgue del sistema antes de que corra el temporizador no se puede reiniciar desde aquí: en ese caso, reinicio manual (mantener el botón de encendido) y el siguiente arranque ya va sin overlay.
- **A mano:** `su -c nexus-quickstep disable --reboot`. Sin root: desactiva el módulo desde Magisk/KernelSU/APatch (modo seguro o recovery) o borra `/data/adb/nexus_quickstep/enabled` desde recovery, y reinicia.
- No hace falta reinstalar la ROM en ningún caso: el overlay vive en el montaje systemless del módulo, no en la imagen.

### Cosas que conviene saber

- No añadas un `overlay/config/config.xml` al módulo: si existe en una partición, el sistema ignora los overlays estáticos de esa partición que no figuren en él y `PackageManager` puede fallar al arrancar («Missing required system package»). Se comprobó en un emulador.
- OMS guarda el estado por overlay en `/data/system/overlays.xml`. Si alguna vez se registró `app.nexus.launcher.overlay.recents` como mutable (builds de prueba antiguas), ese estado puede seguir tratándolo como mutable aunque el APK ahora sea estático.

Protocolo de prueba en el dispositivo (gestos, cancelaciones, Atrás, Recientes, rotación, bloqueo, recuperación): [`PRUEBAS-EN-DISPOSITIVO.md`](PRUEBAS-EN-DISPOSITIVO.md).

## Límites

- **No está probado en un dispositivo.** Lo comprobado: compilación en CI, análisis estático y un emulador Android 17 (el overlay estático hace a Nexus proveedor de recientes y SystemUI se enlaza); el flujo del módulo se ejecutó en el emulador **simulando** a Magisk (`post-fs-data.sh` a mano). Falta un flasheo real: que Magisk/KernelSU/APatch monten el módulo antes de `PackageManagerService`, y los gestos en tu ROM.
- Solo vale para el paquete oficial `app.nexus.launcher` (sabor `github`), no para `.nightly` ni `.play`.
- Los gestos del sistema en Nexus (overlay de recientes) **no están probados en un dispositivo real**, ni que en Android 17 el resto de Quickstep funcione más allá de lo comprobado. Tampoco existe un permiso `BIND_QUICKSTEP_SERVICE`: el servicio de Quickstep exige `STATUS_BAR_SERVICE` a quien se enlaza (SystemUI).
- Requiere Android 14 (API 34) o superior y una partición `/product`.

## Si algo no va

- `systemPrivileges: alternativo, … motivo=…` en el informe de errores dice por qué Nexus usa el modo alternativo (app normal, permiso sin conceder, o rechazo del sistema).
- En logcat, `PackageManager: Privileged permission … not in privapp-permissions allowlist` indica un permiso privilegiado sin resolver en el XML.
- `adb shell dumpsys package app.nexus.launcher` muestra si la app figura como `PRIVILEGED` y qué permisos tiene concedidos.
