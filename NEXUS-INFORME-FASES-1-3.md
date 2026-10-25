# Informe de implementación: Fases 1, 2 y 3 de Nexus Launcher

Fecha: 2026-10-25 · Fase 4: no hecha, a la espera de tus datos.

Reglas respetadas en todas las fases: un push solo a la rama de cada fase (nunca a `16-dev`), sin tocar la clave de firma ni el versionado, sin curvas Bézier nuevas ni niveles de calidad, y módulo y launcher en ramas distintas. Todo cambio del módulo se deshace desde recovery con:

```
touch /data/adb/modules/nexus_launcher_systemless/disable
```

**Importante:** nada de esto se ha probado en Android 17 ni en el Redmi 2312DRA50G. Lo que se afirma es "compila / pasa tipos / pasa pruebas"; el funcionamiento real está pendiente del móvil.

| Fase | Rama | Tipo | Commit | Estado |
|---|---|---|---|---|
| 1 | `diag/montajes-selinux` | Módulo, solo lectura | bd808f8 | Hecha y subida |
| 2 | `modulo/integracion-sin-overlay` | Módulo | fce7fda | Hecha y subida |
| 3 | `launcher/resortes-inicio` | Launcher | 0cc1a93 | Hecha y subida |
| 4 | — | — | — | No hecha (espera datos tuyos) |

---

## Fase 1 — Diagnóstico de montajes y SELinux (`diag/montajes-selinux`)

### Hecho
- `magisk-module/tools/nexus-quickstep`:
  - Nuevo comando `mounts-map`: mapa de montajes por proceso usando `/proc/<pid>/ns/mnt`, `/proc/<pid>/mountinfo` y `/proc/<pid>/root`. El módulo se cuenta por id, porque mountinfo muestra rutas relativas al dispositivo.
  - Ampliaciones del diagnóstico: `diag_modules`, `diag_hybrid` (hybrid_mount: `disable_umount`, `default_mode`) y `diag_avc` (denegaciones SELinux, hasta 40 líneas por origen), llamadas desde `build_diag`.
  - Ayudante `tmo` (temporizador con `timeout` o, si falta, con `sleep`+`kill`) y límites `READ_LIMIT/PS_LIMIT/PM_LIMIT/PROBE_LIMIT` para que nada se cuelgue.
  - `capture_probe` acepta ruta de salida; `mounts-map.txt` entra en la exportación.
- `magisk-module/webroot/index.html`: tarjeta "Mapa de montajes" (`renderMounts`, botones `btn-mounts` y `btn-mounts-copy`).
- `magisk-module/PRUEBAS-EN-DISPOSITIVO.md` (sección 7) y `magisk-module/README.md`.

### Verificado
- Sintaxis de shell con dash y bash.
- Prueba con procesos simulados y espacios de montaje.
- Arnés ligero en node para el bloque de la WebUI.
- En el sandbox no se pudo leer el espacio de init ni `/proc/1/root`, así que "vs init" sale n/d. Es limitación del entorno, no un fallo del código.

### No verificado
- Todo en Android 17 / Redmi (formato real de mountinfo y avc, permisos de `ksu.exec`).

### Incidencias
- `ps` de procps 4.0.4 rechazó `-o PID,ARGS`: `mm_list` prueba varias formas y cae a `pidof`.
- Un primer `Edit` de `capture_probe` falló por texto distinto y se rehízo con el texto exacto.

---

## Fase 2 — Integración sin overlay (`modulo/integracion-sin-overlay`)

### Hecho
- `magisk-module/permissions/runtime.txt` (nuevo): READ_CONTACTS, READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_MEDIA_AUDIO, READ_EXTERNAL_STORAGE, con comentarios que explican lo excluido.
- `.github/scripts/build-magisk-module.sh`: valida la lista (está en el manifest, no está en `allow.txt`, está en `not-privileged.txt`, nombres válidos), genera `system/product/etc/default-permissions/default-permissions-nexus.xml`, valida estructura/XML, comprueba la entrada en el zip y añade el recuento al resumen.
- `magisk-module/tools/nexus-quickstep`: comando `home-role status|on|off` (rol `android.app.role.HOME` vía `cmd role`), con guardado del titular previo en `home-role-prev` para poder restaurarlo. Se niega a actuar si el `help` de `cmd role` no muestra los subcomandos.
- `magisk-module/webroot/index.html`: tarjeta "App de Inicio", diálogo `dlg-home`, `renderHomeRole`, `loadHome`, `homeAction`.
- `PRUEBAS-EN-DISPOSITIVO.md` (sección 8: D1–D4, H1–H5) y `README.md`.
- No se toca el overlay estático.

### Verificado
- Sintaxis de shell (dash/bash).
- Construcción con `aapt2` simulado, incluidos 6 casos de fallo.
- `cmd role` simulado.
- Arnés node de la WebUI.

### No verificado y avisos (tu pedido: avisar, no buscar otro camino)
- **Que default-permissions y `cmd role` funcionen en Android 17 es una hipótesis.** La sintaxis de `cmd role` no se pudo confirmar en fuentes públicas (la web de AOSP no era accesible).
- Default-permissions puede aplicarse solo al crear usuario o al cambiar la huella (fingerprint) del sistema.
- Se espera un conflicto de fusión en la línea `usage()` entre las ramas de Fase 1 y 2.

### Incidencias
- Se eliminaron funciones sobrantes del borrador de `home-role`.
- El arnés de la WebUI imprimía antes de acabar lo asíncrono: `homeAction` ahora devuelve su promesa (inocuo).
- `jsdom` no se pudo instalar; se usó un arnés propio. Un error de comillas con `sed` se resolvió con Python.

### Vuelta atrás
- `touch /data/adb/modules/nexus_launcher_systemless/disable` desde recovery.
- `nexus-quickstep home-role off` restaura el titular anterior.

---

## Fase 3 — Resortes de inicio (`launcher/resortes-inicio`)

### Hecho
- `quickstep/src/com/android/quickstep/util/RectFSpringAnim.java`: los tres resortes (x, y y escala) leen rigidez y amortiguación de `NexusMotion.profile.close` en las dos ramas de `enableScalingRevealHomeAnimation` (en la antigua, también los dos `FlingSpringAnim`). Se lee una vez en `start()`, sin asignaciones por fotograma. La escala deja de tener rigidez distinta en horizontal y tabletas.
- `lawnchair/src/app/lawnchair/animation/MotionSpec.kt`: `CLOSE_DAMPING` de 0,88 a **0,80**. La apertura (170 / 0,90) no cambia.
- Registro en `NexusGesture`: rama activa, rigidez, amortiguación y velocidad inicial al arrancar `RectFSpringAnim`.
- `lawnchair/src/app/lawnchair/animation/HomeSpringParams.kt` (nuevo): parte pura que acota rigidez (1–5000) y amortiguación (0,05–10) y sustituye NaN/infinitos por el cierre por defecto. Evita excepciones de `SpringForce`.
- `tests/multivalentTests/src/app/lawnchair/animation/HomeSpringParamsTest.kt` (nuevo): 6 pruebas.

### Sobre `app_open_x`
**Sigue siendo una curva Bézier.** Es `quickstep/res/interpolator/app_open_x.xml`, un `pathInterpolator` con tres tramos cúbicos. Lo carga `QuickstepTransitionManager` (línea 325) y se usa en el eje X de la ventana (línea 823) y en la reapertura (línea 1097). No se ha tocado.

### Verificado
- Pruebas JVM: **43/43 pasan** (37 anteriores + 6 nuevas), compiladas con el compilador de Kotlin de Gradle y ejecutadas con JUnit. La prueba del helper se compiló con un `NexusMotion` de mentira, porque el real depende de Android.
- Sintaxis de `RectFSpringAnim.java`: javac solo da errores de símbolos ausentes (dependencias), no de sintaxis.
- Spotless/ktlint no ejecutado; repaso a mano de líneas largas y de `when` multilínea (no hay nuevos).

### No verificado (requiere el móvil o CI)
- Compilación real de `RectFSpringAnim.java`.
- Todo el efecto visual.

### Efectos a vigilar
- El 0,80 también afecta a `NexusMotion.gestureEndInterpolator` (final del gesto).
- La escala pasa de 500 / 0,99 a 320 / 0,80: habrá algo de rebote, acotado a 1 con `setMaxValue(1f)`.

### Para probar en el móvil
1. Instala una compilación de esta rama.
2. Abre una app y vuelve a inicio con el gesto: lento, rápido y diagonal.
3. Fíjate en el rebote del cierre, que no se pase del icono y que no haya tirón al soltar.
4. Manda `adb logcat -s NexusGesture` o el archivo de `Download/Nexus-Logs/`. Busca "RectFSpringAnim: rama=…".

### Vuelta atrás
Revertir el commit 0cc1a93. No afecta al módulo.

---

## Cómo obtener algo instalable

Las ramas de fase no disparan CI ni release: el CI corre solo en ramas `*-dev` y la release solo en `16-dev`, tags o ejecución manual. Opciones: fusionar en `16-dev` (publica release si el cambio no es solo `.md`) o lanzar a mano el flujo "Nexus release" sobre la rama. Pendiente de tu decisión. (Corregí un mensaje mío anterior de la Fase 1 que decía "flashear la release de esta rama", que era incorrecto.)

## Otros detalles técnicos

- El clon es superficial: para que el hook de parada vea la rama hay que crear `refs/remotes/origin/<rama>` a mano tras el push (hecho en la Fase 3).
- Commits en español con los trailers `Co-Authored-By: Claude Sonnet 5.5` y `Claude-Session`.

## Pendiente

- Fase 4: no se hace hasta recibir tus datos.
- Decidir cómo generar el zip/APK probable (ver arriba).
- Resultados de las pruebas en el móvil de las Fases 1, 2 y 3.
