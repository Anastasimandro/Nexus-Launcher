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
app: 3 DEX, 11591 clases | sistema: 56 DEX, 63054 clases (con extra: 181010)
== BUSCAR 'ScreenCapture': 403 clases
== BUSCAR 'IRecentTasks': 16 clases
== BUSCAR 'ILauncherProxy': 4 clases
== BUSCAR 'IOverviewProxy': 0 clases
== BUSCAR 'ISystemUiProxy': 4 clases
== BUSCAR 'IRecentsAnimation': 8 clases
== BUSCAR 'IShellTransitions': 9 clases
== ESCANEO de bytecode: 69717 métodos recorridos, 0 desalineados (debe ser 0)
== ENLACE: clases que la app usa y el sistema no tiene (66)
== ENLACE: campos que faltan (2 clases)
== ENLACE: métodos que faltan (6 clases)
== ENLACE: total 92 (clases referenciadas: 13042, campos: 46264, métodos: 81066)
== AIDL: interfaces comparadas: 3; con diferencias: contadas arriba; solo en la app (no están en los binarios dados): 3
== AIDL: total de diferencias 0
  AUSENTE  Lcom/android/wm/shell/shared/IFocusTransitionListener2 (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lcom/android/wm/shell/shared/IHomeTransitionListener2 (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lcom/android/wm/shell/shared/IShellTransitions2 (la interfaz no está en el sistema con ese nombre)
  DIFERENCIA Lcom/android/wm/shell/back/IBackAnimation
  DIFERENCIA Lcom/android/wm/shell/bubbles/IBubbles
  DIFERENCIA Lcom/android/wm/shell/bubbles/IBubblesListener
  DIFERENCIA Lcom/android/wm/shell/common/pip/IPip
  DIFERENCIA Lcom/android/wm/shell/common/pip/IPipAnimationListener
  AUSENTE  Lcom/android/wm/shell/desktopmode/IDesktopMode (la interfaz no está en el sistema con ese nombre)
  DIFERENCIA Lcom/android/wm/shell/desktopmode/IDesktopTaskListener
  DIFERENCIA Lcom/android/wm/shell/desktopmode/IMoveToDesktopCallback
  DIFERENCIA Lcom/android/wm/shell/draganddrop/IDragAndDrop
  DIFERENCIA Lcom/android/wm/shell/onehanded/IOneHanded
  DIFERENCIA Lcom/android/wm/shell/recents/IRecentTasks
  DIFERENCIA Lcom/android/wm/shell/recents/IRecentTasksListener
  DIFERENCIA Lcom/android/wm/shell/recents/IRecentsAnimationController
  DIFERENCIA Lcom/android/wm/shell/recents/IRecentsAnimationRunner
  DIFERENCIA Lcom/android/wm/shell/splitscreen/ISplitScreen
  DIFERENCIA Lcom/android/wm/shell/splitscreen/ISplitScreenListener
  DIFERENCIA Lcom/android/wm/shell/splitscreen/ISplitSelectListener
  DIFERENCIA Lcom/android/wm/shell/startingsurface/IStartingWindow
  AUSENTE  Lcom/android/systemui/animation/shared/IOriginTransitions (la interfaz no está en el sistema con ese nombre)
  DIFERENCIA Lcom/android/systemui/shared/recents/ILauncherProxy
  AUSENTE  Lcom/android/systemui/shared/recents/IOverviewProxy (la interfaz no está en el sistema con ese nombre)
  DIFERENCIA Lcom/android/systemui/shared/recents/ISystemUiProxy
  DIFERENCIA Lcom/android/systemui/shared/system/smartspace/ILauncherUnlockAnimationController
  DIFERENCIA Lcom/android/systemui/shared/system/smartspace/ISysuiUnlockAnimationController
  DIFERENCIA Lcom/android/systemui/unfold/progress/IUnfoldAnimation
  DIFERENCIA Lcom/android/systemui/unfold/progress/IUnfoldTransitionListener
  AUSENTE  Lamirz/aidlbridge/IBridge (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lamirz/aidlbridge/IBridgeCallback (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lapp/lawnchair/root/IRootHelper (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lcom/google/android/libraries/launcherclient/ILauncherOverlay (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lcom/google/android/libraries/launcherclient/ILauncherOverlayCallback (la interfaz no está en el sistema con ese nombre)
== AIDL-FUENTES: 28 interfaces comparadas, 11 ausentes en el sistema, 142 diferencias

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

## Fase 4: activar el overlay y comprobar que SystemUI se enlaza a Nexus
- enable-overlay: exit 0
nexus-quickstep: Nexus aún no ha comprobado la compatibilidad: ábrelo una vez (o usa --force).
exit del primer enable (sin --force): 1
---
app.nexus.launcher/com.android.quickstep.RecentsActivity
---
SystemUI enlazado al servicio de Quickstep de Nexus: NO
(overlay: enabled)
--- caídas:
10-19 07:43:58.544  1423  1423 E AndroidRuntime: FATAL EXCEPTION: main
10-19 07:43:58.544  1423  1423 E AndroidRuntime: Process: app.nexus.launcher, PID: 1423
10-19 07:44:05.110  2024  2024 E AndroidRuntime: FATAL EXCEPTION: main
10-19 07:44:05.110  2024  2024 E AndroidRuntime: Process: app.nexus.launcher, PID: 2024

## Fase 4b: ¿concede el sistema a Nexus los permisos 'signature' al ser el proveedor de recientes?
protección de los permisos que pide Nexus:




















[after-enable] permisos permitidos y no concedidos: 11 (ACCESS_SHORTCUTS INTERNAL_SYSTEM_WINDOW MANAGE_ACCESSIBILITY MANAGE_ACTIVITY_TASKS MANAGE_KEY_GESTURES MONITOR_INPUT READ_FRAME_BUFFER REMOVE_TASKS SET_ORIENTATION STATUS_BAR_SERVICE SYSTEM_APPLICATION_OVERLAY)
H0: NO concedido tras activar el overlay mutable y reiniciar. H1: un segundo reinicio.
- perms-second-boot: exit 1
[second-boot] permisos permitidos y no concedidos: 11 (ACCESS_SHORTCUTS INTERNAL_SYSTEM_WINDOW MANAGE_ACCESSIBILITY MANAGE_ACTIVITY_TASKS MANAGE_KEY_GESTURES MONITOR_INPUT READ_FRAME_BUFFER REMOVE_TASKS SET_ORIENTATION STATUS_BAR_SERVICE SYSTEM_APPLICATION_OVERLAY)
SystemUI enlazado al servicio de Quickstep de Nexus: NO
(overlay: enabled)

## Fase 5: gestos con el overlay activo (inyectados con 'input')
- gestures: exit 0
pantalla 1080x2400
=== A. Inicio con gesto rápido
antes:     topResumedActivity=ActivityRecord{8799335 u0 com.android.settings/.homepage.SettingsHomepageActivity t9}
después:     topResumedActivity=ActivityRecord{8799335 u0 com.android.settings/.homepage.SettingsHomepageActivity t9}
=== B. Inicio con gesto lento: captura a mitad del gesto (la ventana debe seguir el dedo)
después:     topResumedActivity=ActivityRecord{8799335 u0 com.android.settings/.homepage.SettingsHomepageActivity t9}
=== C. Cancelación: subir y volver al punto de partida (debe quedarse en la app)
después:     topResumedActivity=ActivityRecord{8799335 u0 com.android.settings/.homepage.SettingsHomepageActivity t9}
=== D. Cancelación con ACTION_CANCEL a mitad del gesto
después:     topResumedActivity=ActivityRecord{8799335 u0 com.android.settings/.homepage.SettingsHomepageActivity t9}
=== E. Atrás
después:       topResumedActivity=ActivityRecord{38247544 u0 com.google.android.apps.nexuslauncher/.NexusLauncherActivity t2}
=== F. Recientes: subir y mantener
después:       topResumedActivity=ActivityRecord{143544776 u0 com.google.android.apps.nexuslauncher/.NexusLauncherActivity t2}
=== G. Rotación (horizontal) y gesto de Inicio
después (apaisado):       topResumedActivity=ActivityRecord{231747387 u0 com.google.android.apps.nexuslauncher/.NexusLauncherActivity t2}
=== H. Bloqueo y desbloqueo, y gesto de Inicio
después:     topResumedActivity=ActivityRecord{202097923 u0 com.android.settings/.homepage.SettingsHomepageActivity t11}

## Fase 6: recuperación manual (nexus-quickstep disable)
- disable-overlay: exit 0
Overlay desactivado y SystemUI reiniciado: el gesto vuelve al launcher del sistema.
Reinicia cuando puedas para que todo el sistema lo asuma.
com.google.android.apps.nexuslauncher/com.android.quickstep.RecentsActivity
SystemUI enlazado al servicio de Quickstep de Nexus: NO
(overlay: disabled)
tras el gesto, con el overlay desactivado:       topResumedActivity=ActivityRecord{219476350 u0 com.google.android.apps.nexuslauncher/.NexusLauncherActivity t2}

## Fase 7: recuperación automática (Nexus no consigue iniciar Quickstep)
- failure-recovery: exit 1
Overlay app.nexus.launcher.overlay.recents: disabled
config_recentsComponentName ahora: com.google.android.apps.nexuslauncher/com.android.quickstep.RecentsActivity
Comprobación de Nexus: sdk=37 compatible=true
  Ausente pero tolerado por Nexus: DesktopModeFlags.ENABLE_DESKTOP_APP_LAUNCH_ALTTAB_TRANSITIONS_BUGFIX,DesktopModeFlags.ENABLE_DESKTOP_APP_LAUNCH_TRANSITIONS_BUGFIX,DesktopModeFlags.ENABLE_DESKTOP_TRAMPOLINE_CLOSE_ANIMATION_BUGFIX,DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_EXIT_BY_MINIMIZE_TRANSITION_BUGFIX,DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_QUICK_SWITCH,DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_TASKBAR_RUNNING_APPS,DesktopModeFlags.ENABLE_QUICKSWITCH_DESKTOP_SPLIT_BUGFIX,DesktopModeFlags.ENABLE_START_LAUNCH_TRANSITION_FROM_TASKBAR_BUGFIX,DesktopModeFlags.ENABLE_TASKBAR_RECENTS_LAYOUT_TRANSITION,DesktopExperienceFlags.ENABLE_DESKTOP_APP_LAUNCH_BUGFIX,DesktopExperienceFlags.ENABLE_DESKTOP_FIRST_FULLSCREEN_REFOCUS_BUGFIX,DesktopExperienceFlags.ENABLE_DESKTOP_WINDOWING_PIP,DesktopExperienceFlags.ENABLE_FREEFORM_DISPLAY_LAUNCH_PARAMS,DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND,DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_FRONTEND,DesktopExperienceFlags.ENABLE_PINNING_APP_WITH_CONTEXT_MENU,DesktopExperienceFlags.ENABLE_REJECT_HOME_TRANSITION,DesktopExperienceFlags.ENABLE_TASKBAR_RECENT_TASKS_THROTTLE_BUGFIX
  Permisos de sistema aún no concedidos a Nexus: android.permission.MANAGE_ACTIVITY_TASKS,android.permission.MONITOR_INPUT,android.permission.READ_FRAME_BUFFER,android.permission.REMOVE_TASKS
Vigilante: desactivó el overlay -> 2026-10-19 07:49:56 SystemUI no se enlazó al servicio de Nexus en 60 s
Los cambios del overlay solo se aplican al reiniciar.
com.google.android.apps.nexuslauncher/com.android.quickstep.RecentsActivity
2026-10-19 07:44:05 arranque con el overlay activo: vigilando (espera 150s, vigilancia 300s)
2026-10-19 07:48:56 arranque con el overlay activo: vigilando (espera 60s, vigilancia 30s)
2026-10-19 07:49:56 DESACTIVO el overlay: SystemUI no se enlazó al servicio de Nexus en 60 s
2026-10-19 07:49:56 overlay desactivado y SystemUI reiniciado
tras el gesto, con el vigilante ya disparado: 
nexus-quickstep: el vigilante ya desactivó el overlay con esta versión de Nexus (1601009900): 2026-10-19 07:49:56 SystemUI no se enlazó al servicio de Nexus en 60 s. Actualiza Nexus o usa --force.

## Fase 8: overlay estático (aplicado desde el arranque), solo si el mutable no recibió los permisos
- static-overlay: exit 0
cmd: Can't find service: overlay
cmd: Can't find service: overlay
[static-overlay] permisos permitidos y no concedidos: 11 (ACCESS_SHORTCUTS INTERNAL_SYSTEM_WINDOW MANAGE_ACCESSIBILITY MANAGE_ACTIVITY_TASKS MANAGE_KEY_GESTURES MONITOR_INPUT READ_FRAME_BUFFER REMOVE_TASKS SET_ORIENTATION STATUS_BAR_SERVICE SYSTEM_APPLICATION_OVERLAY)
SystemUI enlazado al servicio de Quickstep de Nexus: NO
(overlay: disabled)
--- caídas:
10-19 07:51:25.910  1057  1113 E AndroidRuntime: FATAL EXCEPTION: UiThreadHelper
10-19 07:51:25.910  1057  1113 E AndroidRuntime: Process: com.google.android.apps.nexuslauncher, PID: 1057
El overlay estático no llegó a aplicarse. 8b: overlay inmutable por overlay/config/config.xml.
!! el arranque no terminó en 10 min
- config-overlay: exit 2
cmd: Can't find service: overlay
cmd: Can't find service: overlay
[config-overlay] permisos permitidos y no concedidos: 19 (ACCESS_HIDDEN_PROFILES ACCESS_HIDDEN_PROFILES_FULL ACCESS_SHORTCUTS ALLOW_SLIPPERY_TOUCHES BROADCAST_CLOSE_SYSTEM_DIALOGS CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS INTERNAL_SYSTEM_WINDOW MANAGE_ACCESSIBILITY MANAGE_ACTIVITY_TASKS MANAGE_KEY_GESTURES MONITOR_INPUT READ_FRAME_BUFFER REMOVE_TASKS SET_ORIENTATION START_TASKS_FROM_RECENTS STATUS_BAR STATUS_BAR_SERVICE STOP_APP_SWITCHES SYSTEM_APPLICATION_OVERLAY)
SystemUI enlazado al servicio de Quickstep de Nexus: NO
(overlay: missing)
--- caídas:
10-19 07:52:41.317   627   627 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 07:52:45.078   748   748 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 07:52:49.728   880   880 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 07:52:54.760  1009  1009 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 07:52:59.819  1141  1141 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 07:53:04.793  1274  1274 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 07:53:09.761  1408  1408 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 07:53:14.826  1542  1542 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 07:53:19.795  1677  1677 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 07:53:24.779  1808  1808 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 07:53:29.879  1944  1944 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 07:53:34.834  2078  2078 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 07:53:39.842  2220  2220 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 07:53:44.848  2353  2353 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 07:53:49.885  2479  2479 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 07:53:54.920  2622  2622 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 07:53:59.877  2757  2757 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 07:54:04.942  2891  2891 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 07:54:09.948  3032  3032 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 07:54:14.993  3167  3167 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 07:54:19.951  3298  3298 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 07:54:25.009  3433  3433 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 07:54:29.950  3568  3568 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 07:54:34.934  3701  3701 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 07:54:39.968  3834  3834 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 07:54:45.047  3970  3970 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 07:54:49.931  4106  4106 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 07:54:55.016  4237  4237 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 07:55:00.019  4370  4370 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
10-19 07:55:05.007  4503  4503 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main
- gestures-final: exit 0
pantalla x
=== A. Inicio con gesto rápido
antes: 
después: 
=== B. Inicio con gesto lento: captura a mitad del gesto (la ventana debe seguir el dedo)
después: 
=== C. Cancelación: subir y volver al punto de partida (debe quedarse en la app)
después: 
=== D. Cancelación con ACTION_CANCEL a mitad del gesto
después: 
=== E. Atrás
después: 
=== F. Recientes: subir y mantener
después: 
=== G. Rotación (horizontal) y gesto de Inicio
después (apaisado): 
=== H. Bloqueo y desbloqueo, y gesto de Inicio
después: 

(fin)
