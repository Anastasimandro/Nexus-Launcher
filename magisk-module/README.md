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
```

En este directorio solo están `module.prop` (con `@VERSION@` y `@VERSION_CODE@`), `customize.sh`, `META-INF/` y las listas de `permissions/`. El APK y el XML los pone el script.

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

## Límites

- **No está probado en un dispositivo.** El script, el instalador y la lógica de diagnóstico se han probado con simulaciones; falta un flasheo real en tu ROM.
- Solo vale para el paquete oficial `app.nexus.launcher` (sabor `github`), no para `.nightly` ni `.play`.
- El módulo **no** hace de Nexus el proveedor de recientes (`config_recentsComponentName`), así que no activa Quickstep ni los gestos del sistema. Tampoco existe un permiso `BIND_QUICKSTEP_SERVICE`: el servicio de Quickstep exige `STATUS_BAR_SERVICE` a quien se enlaza (SystemUI).
- Requiere Android 14 (API 34) o superior y una partición `/product`.

## Si algo no va

- `systemPrivileges: alternativo, … motivo=…` en el informe de errores dice por qué Nexus usa el modo alternativo (app normal, permiso sin conceder, o rechazo del sistema).
- En logcat, `PackageManager: Privileged permission … not in privapp-permissions allowlist` indica un permiso privilegiado sin resolver en el XML.
- `adb shell dumpsys package app.nexus.launcher` muestra si la app figura como `PRIVILEGED` y qué permisos tiene concedidos.
