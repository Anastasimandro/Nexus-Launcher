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

## Fase 2: instalar el módulo como lo hace Magisk (remount)
- install-module: exit 0

## Fase 3: el módulo en Android real (app privilegiada y overlay)
- module-evidence: exit 0
overlay (nuestro):
[ ] app.nexus.launcher.overlay.recents
lookup antes:
cmd: Can't find service: overlay
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

## Fase 8 (static): overlay inmutable aplicado desde el arranque (Nexus recibe los permisos 'recents' en el arranque de PMS)
- static-overlay: exit 0
android
[ ] app.nexus.launcher.overlay.recents
[ ] app.nexus.launcher.overlay.recents
com.google.android.apps.nexuslauncher/com.android.quickstep.RecentsActivity
[static-overlay] permisos permitidos y no concedidos: 11 (ACCESS_SHORTCUTS INTERNAL_SYSTEM_WINDOW MANAGE_ACCESSIBILITY MANAGE_ACTIVITY_TASKS MANAGE_KEY_GESTURES MONITOR_INPUT READ_FRAME_BUFFER REMOVE_TASKS SET_ORIENTATION STATUS_BAR_SERVICE SYSTEM_APPLICATION_OVERLAY)
SystemUI enlazado al servicio de Quickstep de Nexus: NO
(overlay: disabled)
--- caídas:
10-19 21:35:09.368  3232  3286 E AndroidRuntime: FATAL EXCEPTION: UiThreadHelper
10-19 21:35:09.368  3232  3286 E AndroidRuntime: Process: com.google.android.apps.nexuslauncher, PID: 3232
El overlay inmutable (static) NO se aplicó a config_recentsComponentName.

(fin)
