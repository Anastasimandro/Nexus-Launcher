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

- framework-resources: exit 0
== RECURSOS DEL FRAMEWORK: 866 referencias, 14793 recursos en el sistema
  todas existen

## Fase 2: instalar el módulo como lo hace Magisk (remount)
- install-module: exit 0

## Fase 3: el módulo en Android real (app privilegiada y overlay)
- module-evidence: exit 0
overlay (nuestro):
cmd: Can't find service: overlay
lookup antes:
cmd: Can't find service: overlay
probe:
cat: /data/user/0/app.nexus.launcher/files/quickstep-probe.txt: No such file or directory
permisos permitidos y NO concedidos:
android.permission.ACCESS_HIDDEN_PROFILES
android.permission.ACCESS_HIDDEN_PROFILES_FULL
android.permission.ACCESS_SHORTCUTS
android.permission.ALLOW_SLIPPERY_TOUCHES
android.permission.BROADCAST_CLOSE_SYSTEM_DIALOGS
android.permission.CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS
android.permission.INTERNAL_SYSTEM_WINDOW
android.permission.MANAGE_ACCESSIBILITY
android.permission.MANAGE_ACTIVITY_TASKS
android.permission.MANAGE_KEY_GESTURES
android.permission.MONITOR_INPUT
android.permission.READ_FRAME_BUFFER
android.permission.REMOVE_TASKS
android.permission.SET_ORIENTATION
android.permission.START_TASKS_FROM_RECENTS
android.permission.STATUS_BAR
android.permission.STATUS_BAR_SERVICE
android.permission.STOP_APP_SWITCHES
android.permission.SYSTEM_APPLICATION_OVERLAY

## Fase 8 (static): overlay inmutable aplicado desde el arranque (Nexus recibe los permisos 'recents' en el arranque de PMS)
- static-overlay: exit 0
android
app.nexus.launcher/com.android.quickstep.RecentsActivity
[static-overlay] permisos permitidos y no concedidos: 0 ()
SystemUI enlazado al servicio de Quickstep de Nexus: NO
(overlay: missing)
--- caídas:
10-20 07:39:43.189  1038  1038 E AndroidRuntime: FATAL EXCEPTION: main
10-20 07:39:43.189  1038  1038 E AndroidRuntime: Process: com.google.android.apps.nexuslauncher, PID: 1038
10-20 07:39:45.429  1659  1659 E AndroidRuntime: FATAL EXCEPTION: main
10-20 07:39:45.429  1659  1659 E AndroidRuntime: Process: com.google.android.apps.nexuslauncher, PID: 1659
10-20 07:39:47.469  1425  1425 E AndroidRuntime: FATAL EXCEPTION: main
10-20 07:39:47.469  1425  1425 E AndroidRuntime: Process: app.nexus.launcher, PID: 1425
10-20 07:39:53.347  2639  2639 E AndroidRuntime: FATAL EXCEPTION: main
10-20 07:39:53.347  2639  2639 E AndroidRuntime: Process: app.nexus.launcher, PID: 2639
El overlay inmutable (static) se aplicó: config_recentsComponentName apunta a Nexus.
- gestures-final: exit 0
pantalla 1080x2400
=== A. Inicio con gesto rápido
antes:     topResumedActivity=ActivityRecord{165280006 u0 com.android.settings/.homepage.SettingsHomepageActivity t8}
después:     topResumedActivity=ActivityRecord{165280006 u0 com.android.settings/.homepage.SettingsHomepageActivity t8}
=== B. Inicio con gesto lento: captura a mitad del gesto (la ventana debe seguir el dedo)
después:     topResumedActivity=ActivityRecord{165280006 u0 com.android.settings/.homepage.SettingsHomepageActivity t8}
=== C. Cancelación: subir y volver al punto de partida (debe quedarse en la app)
después:     topResumedActivity=ActivityRecord{165280006 u0 com.android.settings/.homepage.SettingsHomepageActivity t8}
=== D. Cancelación con ACTION_CANCEL a mitad del gesto
después:     topResumedActivity=ActivityRecord{165280006 u0 com.android.settings/.homepage.SettingsHomepageActivity t8}
=== E. Atrás
después:       topResumedActivity=ActivityRecord{80498168 u0 app.nexus.launcher/app.lawnchair.LawnchairLauncher t10}
=== F. Recientes: subir y mantener
después:       topResumedActivity=ActivityRecord{249456012 u0 com.google.android.apps.nexuslauncher/.NexusLauncherActivity t2}
=== G. Rotación (horizontal) y gesto de Inicio
después (apaisado): 
=== H. Bloqueo y desbloqueo, y gesto de Inicio
después:       topResumedActivity=ActivityRecord{245716765 u0 com.google.android.apps.nexuslauncher/.NexusLauncherActivity t2}

(fin)
