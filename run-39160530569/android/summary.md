# Validación en Android real (emulador)

ro.build.version.release=17
ro.build.version.sdk=37
ro.build.version.sdk_full=37.0
ro.build.version.codename=REL
ro.build.version.incremental=15611780
ro.build.fingerprint=google/sdk_gphone64_x86_64/emu64xa:17/CE2A.260420.019/15611780:userdebug/dev-keys
ro.build.type=userdebug
ro.build.flavor=sdk_gphone64_x86_64-userdebug
ro.product.cpu.abi=x86_64
id=uid=0(root) gid=0(root) groups=0(root),1004(input),1007(log),1011(adb),1015(sdcard_rw),1028(sdcard_r),1078(ext_data_rw),1079(ext_obb_rw),3001(net_bt_admin),3002(net_bt),3003(inet),3006(net_bw_stats),3009(readproc),3011(uhid),3012(readtracefs) context=u:r:su:s0
wm_size=Physical size: 1080x2400

## Fase 1: auditoría estática contra los binarios del sistema (sin módulo)
- static-audit-run: exit 0
app: 3 DEX, 11591 clases | sistema: 56 DEX, 63054 clases (con extra: 101419)
== ENLACE: clases que la app usa y el sistema no tiene (66)
== ENLACE: campos que faltan (2 clases)
== ENLACE: métodos que faltan (6 clases)
== ENLACE: total 92 (clases referenciadas: 13042, campos: 46261, métodos: 81038)
== AIDL: interfaces comparadas: 3; con diferencias: contadas arriba; solo en la app (no están en los binarios dados): 3
== AIDL: total de diferencias 0

## Fase 2: instalar el módulo como lo hace Magisk (remount)
- install-module: exit 0

## Fase 3: el módulo en Android real (app privilegiada y overlay)
- module-evidence: exit 0
overlay (nuestro):
--- app.nexus.launcher.overlay.recents
lookup antes:
com.google.android.apps.nexuslauncher/com.android.quickstep.RecentsActivity
probe:
cat: /data/user/0/app.nexus.launcher/files/quickstep-probe.txt: No such file or directory
permisos permitidos y NO concedidos:
android.permission.ACCESS_SHORTCUTS
android.permission.INTERNAL_SYSTEM_WINDOW
android.permission.MANAGE_ACCESSIBILITY
android.permission.MANAGE_ACTIVITY_TASKS
android.permission.MANAGE_KEY_GESTURES
android.permission.MONITOR_INPUT
android.permission.READ_FRAME_BUFFER
android.permission.REMOVE_TASKS
android.permission.SET_ORIENTATION
android.permission.STATUS_BAR_SERVICE
android.permission.SYSTEM_APPLICATION_OVERLAY

## Fase 4: activar el overlay y comprobar que SystemUI se enlaza a Nexus
- enable-overlay: exit 0
nexus-quickstep: el sistema no ha aprobado el overlay.
exit del primer enable (sin --force): 1
---
com.google.android.apps.nexuslauncher/com.android.quickstep.RecentsActivity
---
SystemUI enlazado al servicio de Quickstep de Nexus: NO
(overlay: unavailable)
--- caídas:
10-18 20:03:59.809  1292  1305 E AndroidRuntime: FATAL EXCEPTION: Thread-2
10-18 20:03:59.809  1292  1305 E AndroidRuntime: Process: com.google.android.cellbroadcastreceiver, PID: 1292
10-18 20:04:00.055  1315  1327 E AndroidRuntime: FATAL EXCEPTION: Thread-2
10-18 20:04:00.055  1315  1327 E AndroidRuntime: Process: com.google.android.cellbroadcastreceiver, PID: 1315
10-18 20:04:05.771  1331  1331 E AndroidRuntime: FATAL EXCEPTION: main
10-18 20:04:05.771  1331  1331 E AndroidRuntime: Process: com.google.android.dialer, PID: 1331
10-18 20:04:06.975  1629  1827 E AndroidRuntime: FATAL EXCEPTION: fg
10-18 20:04:06.975  1629  1827 E AndroidRuntime: Process: com.google.android.providers.media.module, PID: 1629
10-18 20:04:07.798  1870  1870 E AndroidRuntime: FATAL EXCEPTION: main
10-18 20:04:07.798  1870  1870 E AndroidRuntime: Process: com.google.android.dialer, PID: 1870
10-18 20:04:07.962  1627  1903 E AndroidRuntime: FATAL EXCEPTION: aiai-app-predictor-0
10-18 20:04:07.962  1627  1903 E AndroidRuntime: Process: com.google.android.as, PID: 1627
10-18 20:04:08.109  1968  1968 E AndroidRuntime: FATAL EXCEPTION: main
10-18 20:04:08.109  1968  1968 E AndroidRuntime: Process: android.process.media, PID: 1968
10-18 20:04:08.281  1593  1997 E AndroidRuntime: FATAL EXCEPTION: CredManRegistrationManager
10-18 20:04:08.281  1593  1997 E AndroidRuntime: Process: com.google.android.gms, PID: 1593
10-18 20:04:08.887  2076  2076 E AndroidRuntime: FATAL EXCEPTION: main
10-18 20:04:08.887  2076  2076 E AndroidRuntime: Process: android.process.media, PID: 2076
10-18 20:04:11.143  2237  2278 E AndroidRuntime: FATAL EXCEPTION: aiai-app-predictor-0
10-18 20:04:11.143  2237  2278 E AndroidRuntime: Process: com.google.android.as, PID: 2237
10-18 20:04:14.344  2343  2343 E AndroidRuntime: FATAL EXCEPTION: main
10-18 20:04:14.344  2343  2343 E AndroidRuntime: Process: com.google.android.gms, PID: 2343
10-18 20:04:15.869  2339  2428 E AndroidRuntime: FATAL EXCEPTION: WM.task-1
10-18 20:04:15.869  2339  2428 E AndroidRuntime: Process: com.google.android.as.oss, PID: 2339
10-18 20:04:16.419  1053  1097 E AndroidRuntime: FATAL EXCEPTION: launcher-loader
10-18 20:04:16.419  1053  1097 E AndroidRuntime: Process: com.google.android.apps.nexuslauncher, PID: 1053
10-18 20:04:16.843  2595  2684 E AndroidRuntime: FATAL EXCEPTION: aiai-app-predictor-0
10-18 20:04:16.843  2595  2684 E AndroidRuntime: Process: com.google.android.as, PID: 2595
10-18 20:04:17.959  2904  2904 E AndroidRuntime: FATAL EXCEPTION: main
10-18 20:04:17.959  2904  2904 E AndroidRuntime: Process: android.process.media, PID: 2904

## Fase 5: gestos con el overlay activo (inyectados con 'input')
- gestures: exit 0
pantalla 1080x2400
=== A. Inicio con gesto rápido
antes:     topResumedActivity=ActivityRecord{16697203 u0 com.android.settings/.homepage.SettingsHomepageActivity t7}
después:       topResumedActivity=ActivityRecord{18435849 u0 com.google.android.apps.nexuslauncher/.NexusLauncherActivity t2}
=== B. Inicio con gesto lento: captura a mitad del gesto (la ventana debe seguir el dedo)
después:       topResumedActivity=ActivityRecord{33765184 u0 com.google.android.apps.nexuslauncher/.NexusLauncherActivity t2}
=== C. Cancelación: subir y volver al punto de partida (debe quedarse en la app)
después: 
=== D. Cancelación con ACTION_CANCEL a mitad del gesto
después:       topResumedActivity=ActivityRecord{225510068 u0 com.google.android.apps.nexuslauncher/.NexusLauncherActivity t2}
=== E. Atrás
después:     topResumedActivity=ActivityRecord{239844656 u0 com.android.settings/.homepage.SettingsHomepageActivity t7}
=== F. Recientes: subir y mantener
después:       topResumedActivity=ActivityRecord{26955451 u0 com.google.android.apps.nexuslauncher/.NexusLauncherActivity t2}
=== G. Rotación (horizontal) y gesto de Inicio
después (apaisado): 
=== H. Bloqueo y desbloqueo, y gesto de Inicio
después: 

## Fase 6: recuperación manual (nexus-quickstep disable)
- disable-overlay: exit 2
nexus-quickstep: el overlay no está instalado.
cmd: Can't find service: overlay
SystemUI enlazado al servicio de Quickstep de Nexus: NO
(overlay: missing)
tras el gesto, con el overlay desactivado: 

## Fase 7: recuperación automática (Nexus no consigue iniciar Quickstep)
!! el arranque no terminó en 10 min
- failure-recovery: exit 1
Overlay app.nexus.launcher.overlay.recents: missing
  No está instalado: flashea el módulo de Nexus y reinicia.
config_recentsComponentName ahora: cmd: Failure calling service overlay: Broken pipe (32)
Comprobación de Nexus: sin datos (abre Nexus una vez).
Vigilante: sin verificar todavía.
Los cambios del overlay solo se aplican al reiniciar.
cmd: Failure calling service overlay: Broken pipe (32)
2026-10-18 20:04:16 arranque: overlay no activo, nada que vigilar
2026-10-18 20:18:41 arranque: overlay no activo, nada que vigilar
tras el gesto, con el vigilante ya disparado: 
nexus-quickstep: el overlay no está instalado: flashea el módulo y reinicia.

(fin)
