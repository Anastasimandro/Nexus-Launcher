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
wm_size=

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

## Fase 8 (configempty): overlay inmutable aplicado desde el arranque (Nexus recibe los permisos 'recents' en el arranque de PMS)
!! el arranque no terminó en 2 min (150 s)
!! cmd overlay no respondió en 4 min
- config-overlay: exit 0
cmd: Can't find service: overlay
cmd: Can't find service: overlay
[config-overlay] permisos permitidos y no concedidos: 19 (ACCESS_HIDDEN_PROFILES ACCESS_HIDDEN_PROFILES_FULL ACCESS_SHORTCUTS ALLOW_SLIPPERY_TOUCHES BROADCAST_CLOSE_SYSTEM_DIALOGS CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS INTERNAL_SYSTEM_WINDOW MANAGE_ACCESSIBILITY MANAGE_ACTIVITY_TASKS MANAGE_KEY_GESTURES MONITOR_INPUT READ_FRAME_BUFFER REMOVE_TASKS SET_ORIENTATION START_TASKS_FROM_RECENTS STATUS_BAR STATUS_BAR_SERVICE STOP_APP_SWITCHES SYSTEM_APPLICATION_OVERLAY)
SystemUI enlazado al servicio de Quickstep de Nexus: NO
(overlay: missing)
--- caídas:
10-19 21:07:44.438   628   628 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:07:48.570   749   749 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:07:53.159   900   900 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:07:58.143  1035  1035 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:08:03.179  1167  1167 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:08:08.193  1302  1302 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:08:13.311  1435  1435 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:08:18.258  1573  1573 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:08:23.255  1704  1704 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:08:28.408  1835  1835 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:08:33.248  1969  1969 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:08:38.344  2101  2101 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:08:43.300  2233  2233 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:08:48.275  2368  2368 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:08:53.375  2500  2500 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:08:58.329  2636  2636 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:09:03.340  2769  2769 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:09:08.362  2902  2902 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:09:13.348  3032  3032 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:09:18.263  3166  3166 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:09:23.244  3297  3297 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:09:28.187  3429  3429 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:09:33.241  3562  3562 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:09:38.195  3697  3697 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:09:43.376  3831  3831 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:09:48.242  3964  3964 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:09:53.215  4099  4099 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:09:58.255  4234  4234 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:10:03.219  4366  4366 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:10:08.327  4499  4499 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
El overlay inmutable (config) NO se aplicó a config_recentsComponentName.

(fin)
