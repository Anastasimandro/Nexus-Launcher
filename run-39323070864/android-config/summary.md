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
cmd: Can't find service: overlay
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

## Fase 8 (config): overlay inmutable aplicado desde el arranque (Nexus recibe los permisos 'recents' en el arranque de PMS)
!! el arranque no terminó en 2 min (150 s)
!! cmd overlay no respondió en 4 min
- config-overlay: exit 0
cmd: Can't find service: overlay
cmd: Can't find service: overlay
[config-overlay] permisos permitidos y no concedidos: 19 (ACCESS_HIDDEN_PROFILES ACCESS_HIDDEN_PROFILES_FULL ACCESS_SHORTCUTS ALLOW_SLIPPERY_TOUCHES BROADCAST_CLOSE_SYSTEM_DIALOGS CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS INTERNAL_SYSTEM_WINDOW MANAGE_ACCESSIBILITY MANAGE_ACTIVITY_TASKS MANAGE_KEY_GESTURES MONITOR_INPUT READ_FRAME_BUFFER REMOVE_TASKS SET_ORIENTATION START_TASKS_FROM_RECENTS STATUS_BAR STATUS_BAR_SERVICE STOP_APP_SWITCHES SYSTEM_APPLICATION_OVERLAY)
SystemUI enlazado al servicio de Quickstep de Nexus: NO
(overlay: missing)
--- caídas:
10-19 21:08:24.855   631   631 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:08:28.598   760   760 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:08:33.499   903   903 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:08:38.159  1036  1036 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:08:43.167  1173  1173 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:08:48.187  1314  1314 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:08:53.144  1452  1452 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:08:58.175  1587  1587 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:09:03.191  1722  1722 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:09:08.199  1856  1856 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:09:13.218  1994  1994 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:09:18.238  2127  2127 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:09:23.222  2259  2259 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:09:28.289  2393  2393 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:09:33.285  2527  2527 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:09:38.304  2659  2659 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:09:43.237  2793  2793 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:09:48.280  2926  2926 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:09:53.260  3064  3064 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:09:58.085  3197  3197 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:10:03.080  3330  3330 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:10:08.125  3466  3466 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:10:13.091  3600  3600 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:10:18.116  3734  3734 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:10:23.126  3867  3867 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:10:28.166  4001  4001 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:10:33.135  4137  4137 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:10:38.109  4271  4271 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:10:43.121  4406  4406 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 21:10:48.120  4541  4541 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
El overlay inmutable (config) NO se aplicó a config_recentsComponentName.

(fin)
