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

## Fase 8 (config): overlay inmutable aplicado desde el arranque (Nexus recibe los permisos 'recents' en el arranque de PMS)
!! el arranque no terminó en 10 min
!! cmd overlay no respondió en 4 min
- config-overlay: exit 0
cmd: Can't find service: overlay
cmd: Can't find service: overlay
[config-overlay] permisos permitidos y no concedidos: 19 (ACCESS_HIDDEN_PROFILES ACCESS_HIDDEN_PROFILES_FULL ACCESS_SHORTCUTS ALLOW_SLIPPERY_TOUCHES BROADCAST_CLOSE_SYSTEM_DIALOGS CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS INTERNAL_SYSTEM_WINDOW MANAGE_ACCESSIBILITY MANAGE_ACTIVITY_TASKS MANAGE_KEY_GESTURES MONITOR_INPUT READ_FRAME_BUFFER REMOVE_TASKS SET_ORIENTATION START_TASKS_FROM_RECENTS STATUS_BAR STATUS_BAR_SERVICE STOP_APP_SWITCHES SYSTEM_APPLICATION_OVERLAY)
SystemUI enlazado al servicio de Quickstep de Nexus: NO
(overlay: missing)
--- caídas:
10-19 20:37:28.609   629   629 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 20:37:32.209   757   757 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 20:37:37.004   897   897 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 20:37:41.885  1028  1028 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 20:37:46.861  1164  1164 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 20:37:51.887  1302  1302 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 20:37:56.915  1435  1435 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 20:38:01.862  1570  1570 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 20:38:06.909  1708  1708 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 20:38:11.941  1845  1845 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 20:38:16.967  1980  1980 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 20:38:21.958  2114  2114 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 20:38:26.922  2248  2248 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 20:38:31.957  2384  2384 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 20:38:36.946  2518  2518 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 20:38:42.012  2653  2653 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 20:38:46.968  2787  2787 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 20:38:52.009  2924  2924 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 20:38:57.029  3058  3058 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 20:39:01.969  3191  3191 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 20:39:07.069  3322  3322 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 20:39:12.039  3458  3458 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 20:39:17.061  3592  3592 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 20:39:22.079  3725  3725 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 20:39:27.014  3859  3859 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 20:39:32.031  3995  3995 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 20:39:37.024  4129  4129 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 20:39:42.031  4262  4262 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 20:39:47.102  4398  4398 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 20:39:52.135  4534  4534 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
El overlay inmutable (config) NO se aplicó a config_recentsComponentName.

(fin)
