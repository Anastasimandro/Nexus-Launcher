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
app: 3 DEX, 11592 clases | sistema: 56 DEX, 63054 clases (con extra: 181010)
== BUSCAR 'ScreenCapture': 403 clases
== BUSCAR 'IRecentTasks': 16 clases
== BUSCAR 'ILauncherProxy': 4 clases
== BUSCAR 'IOverviewProxy': 0 clases
== BUSCAR 'ISystemUiProxy': 4 clases
== BUSCAR 'IRecentsAnimation': 8 clases
== BUSCAR 'IShellTransitions': 9 clases
== ESCANEO de bytecode: 69720 métodos recorridos, 0 desalineados (debe ser 0)
== ENLACE: clases que la app usa y el sistema no tiene (66)
== ENLACE: campos que faltan (2 clases)
== ENLACE: métodos que faltan (6 clases)
== ENLACE: total 92 (clases referenciadas: 13043, campos: 46265, métodos: 81069)
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

- framework-resources: exit 0
== RECURSOS DEL FRAMEWORK: 866 referencias, 14793 recursos en el sistema
  todas existen

## Fase 2: instalar el módulo como lo hace Magisk (remount)
- install-module: exit 0

## Fase 3: el módulo en Android real (app privilegiada y overlay)
- module-evidence: exit 0
overlay (nuestro):
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

## Fase 4: activar con 'nexus-quickstep enable', aplicar en el arranque y verificar el enlace
- enable-flow: exit 0
enable:
nexus-quickstep: Nexus aún no ha comprobado la compatibilidad: ábrelo una vez (o usa --force).
exit: 1
Overlay pedido. REINICIA para que el sistema lo aplique en el arranque.
Tras reiniciar: nexus-quickstep status  y  nexus-quickstep bound
Si SystemUI no llega a enlazarse con Nexus, el vigilante lo desactiva y reinicia solo.
Para deshacerlo: nexus-quickstep disable --reboot
exit (--force): 0
estado tras enable: enabled watchdog.log
lookup antes de reiniciar: com.google.android.apps.nexuslauncher/com.android.quickstep.RecentsActivity

## enabled
config_recentsComponentName: app.nexus.launcher/com.android.quickstep.RecentsActivity
[enabled] permisos permitidos y no concedidos: 0 ()
--- nexus-quickstep bound:
SystemUI enlazado al servicio de Quickstep de Nexus: NO
(overlay efectivo: sí; estado: missing)
--- nexus-quickstep status:
Pedido (enabled): sí. El módulo aplica el overlay en cada arranque.
Overlay app.nexus.launcher.overlay.recents en este arranque: missing
  APK en /product/overlay: sí
config_recentsComponentName ahora: app.nexus.launcher/com.android.quickstep.RecentsActivity
Overlay efectivo: sí (Nexus es el proveedor de recientes)
SystemUI enlazado al servicio de Nexus: NO
Comprobación de Nexus: sdk=37 compatible=true
  Ausente pero tolerado por Nexus: DesktopModeFlags.ENABLE_DESKTOP_APP_LAUNCH_ALTTAB_TRANSITIONS_BUGFIX,DesktopModeFlags.ENABLE_DESKTOP_APP_LAUNCH_TRANSITIONS_BUGFIX,DesktopModeFlags.ENABLE_DESKTOP_TRAMPOLINE_CLOSE_ANIMATION_BUGFIX,DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_EXIT_BY_MINIMIZE_TRANSITION_BUGFIX,DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_QUICK_SWITCH,DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_TASKBAR_RUNNING_APPS,DesktopModeFlags.ENABLE_QUICKSWITCH_DESKTOP_SPLIT_BUGFIX,DesktopModeFlags.ENABLE_START_LAUNCH_TRANSITION_FROM_TASKBAR_BUGFIX,DesktopModeFlags.ENABLE_TASKBAR_RECENTS_LAYOUT_TRANSITION,DesktopExperienceFlags.ENABLE_DESKTOP_APP_LAUNCH_BUGFIX,DesktopExperienceFlags.ENABLE_DESKTOP_FIRST_FULLSCREEN_REFOCUS_BUGFIX,DesktopExperienceFlags.ENABLE_DESKTOP_WINDOWING_PIP,DesktopExperienceFlags.ENABLE_FREEFORM_DISPLAY_LAUNCH_PARAMS,DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND,DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_FRONTEND,DesktopExperienceFlags.ENABLE_PINNING_APP_WITH_CONTEXT_MENU,DesktopExperienceFlags.ENABLE_REJECT_HOME_TRANSITION,DesktopExperienceFlags.ENABLE_TASKBAR_RECENT_TASKS_THROTTLE_BUGFIX
Vigilante: sin verificar todavía.
Marcador de arranque (booting): presente (el vigilante lo borra al verificar el arranque).
Los cambios solo se aplican al reiniciar.
--- /data/adb/nexus_quickstep:
booting
enabled
watchdog.log
enabled: 2026-10-20 11:34:56 version=1601009900
booting: 
--- watchdog.log:
2026-10-20 11:34:52 cmd overlay list no devolvió el overlay: cmd: Can't find service: overlay 
2026-10-20 11:34:55 cmd overlay list no devolvió el overlay:  
2026-10-20 11:34:56 post-fs-data: overlay copiado a /data/local/tmp/nexusmod/system/product/overlay/NexusRecentsOverlay.apk
2026-10-20 11:35:37 arranque con el overlay pedido: vigilando (espera 150s, vigilancia 60s)
2026-10-20 11:36:40 cmd overlay list no devolvió el overlay:  
2026-10-20 11:36:44 cmd overlay list no devolvió el overlay:  
--- caídas:
10-20 11:35:29.381  1037  1037 E AndroidRuntime: FATAL EXCEPTION: main
10-20 11:35:29.381  1037  1037 E AndroidRuntime: Process: com.google.android.apps.nexuslauncher, PID: 1037
10-20 11:35:33.201  1718  1718 E AndroidRuntime: FATAL EXCEPTION: main
10-20 11:35:33.201  1718  1718 E AndroidRuntime: Process: com.google.android.apps.nexuslauncher, PID: 1718
10-20 11:35:36.555  1432  1432 E AndroidRuntime: FATAL EXCEPTION: main
10-20 11:35:36.555  1432  1432 E AndroidRuntime: Process: app.nexus.launcher, PID: 1432
10-20 11:35:44.149  2635  2635 E AndroidRuntime: FATAL EXCEPTION: main
10-20 11:35:44.149  2635  2635 E AndroidRuntime: Process: app.nexus.launcher, PID: 2635

## Fase 5: gestos con Nexus como proveedor de Quickstep
- gestures: exit 0
pantalla 1080x2400
=== A. Inicio con gesto rápido
antes:     topResumedActivity=ActivityRecord{71681358 u0 com.android.settings/.homepage.SettingsHomepageActivity t8}
después:     topResumedActivity=ActivityRecord{71681358 u0 com.android.settings/.homepage.SettingsHomepageActivity t8}
=== B. Inicio con gesto lento: captura a mitad del gesto (la ventana debe seguir el dedo)
después:     topResumedActivity=ActivityRecord{71681358 u0 com.android.settings/.homepage.SettingsHomepageActivity t8}
=== C. Cancelación: subir y volver al punto de partida (debe quedarse en la app)
después:     topResumedActivity=ActivityRecord{71681358 u0 com.android.settings/.homepage.SettingsHomepageActivity t8}
=== D. Cancelación con ACTION_CANCEL a mitad del gesto
después:     topResumedActivity=ActivityRecord{71681358 u0 com.android.settings/.homepage.SettingsHomepageActivity t8}
=== E. Atrás
después:       topResumedActivity=ActivityRecord{72682140 u0 app.nexus.launcher/app.lawnchair.LawnchairLauncher t10}
=== F. Recientes: subir y mantener
después:     topResumedActivity=ActivityRecord{142170245 u0 com.android.settings/.homepage.SettingsHomepageActivity t11}
=== G. Rotación (horizontal) y gesto de Inicio
después (apaisado):       topResumedActivity=ActivityRecord{62686616 u0 com.google.android.apps.nexuslauncher/.NexusLauncherActivity t2}
=== H. Bloqueo y desbloqueo, y gesto de Inicio
después:     topResumedActivity=ActivityRecord{266194235 u0 com.android.settings/.homepage.SettingsHomepageActivity t14}

## Fase 6: deshacer a mano ('nexus-quickstep disable --reboot')
- manual-disable: exit 0
Overlay desactivado. Reiniciando: el gesto vuelve al launcher del sistema.
rc=0
total 0
watchdog.log
2026-10-20 11:35:37 arranque con el overlay pedido: vigilando (espera 150s, vigilancia 60s)
2026-10-20 11:36:40 cmd overlay list no devolvió el overlay:  
2026-10-20 11:36:44 cmd overlay list no devolvió el overlay:  
2026-10-20 11:38:04 SystemUI enlazado al servicio de Nexus tras 145s
montado: no
## disabled
config_recentsComponentName: com.google.android.apps.nexuslauncher/com.android.quickstep.RecentsActivity
[disabled] permisos permitidos y no concedidos: 11 (ACCESS_SHORTCUTS INTERNAL_SYSTEM_WINDOW MANAGE_ACCESSIBILITY MANAGE_ACTIVITY_TASKS MANAGE_KEY_GESTURES MONITOR_INPUT READ_FRAME_BUFFER REMOVE_TASKS SET_ORIENTATION STATUS_BAR_SERVICE SYSTEM_APPLICATION_OVERLAY)
--- nexus-quickstep bound:
SystemUI enlazado al servicio de Quickstep de Nexus: NO
(overlay efectivo: no; estado: missing)
--- nexus-quickstep status:
Pedido (enabled): no. El módulo no aplica el overlay (estado de serie).
Overlay app.nexus.launcher.overlay.recents en este arranque: missing
  APK en /product/overlay: no
config_recentsComponentName ahora: com.google.android.apps.nexuslauncher/com.android.quickstep.RecentsActivity
Overlay efectivo: no
Comprobación de Nexus: sdk=37 compatible=true
  Ausente pero tolerado por Nexus: DesktopModeFlags.ENABLE_DESKTOP_APP_LAUNCH_ALTTAB_TRANSITIONS_BUGFIX,DesktopModeFlags.ENABLE_DESKTOP_APP_LAUNCH_TRANSITIONS_BUGFIX,DesktopModeFlags.ENABLE_DESKTOP_TRAMPOLINE_CLOSE_ANIMATION_BUGFIX,DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_EXIT_BY_MINIMIZE_TRANSITION_BUGFIX,DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_QUICK_SWITCH,DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_TASKBAR_RUNNING_APPS,DesktopModeFlags.ENABLE_QUICKSWITCH_DESKTOP_SPLIT_BUGFIX,DesktopModeFlags.ENABLE_START_LAUNCH_TRANSITION_FROM_TASKBAR_BUGFIX,DesktopModeFlags.ENABLE_TASKBAR_RECENTS_LAYOUT_TRANSITION,DesktopExperienceFlags.ENABLE_DESKTOP_APP_LAUNCH_BUGFIX,DesktopExperienceFlags.ENABLE_DESKTOP_FIRST_FULLSCREEN_REFOCUS_BUGFIX,DesktopExperienceFlags.ENABLE_DESKTOP_WINDOWING_PIP,DesktopExperienceFlags.ENABLE_FREEFORM_DISPLAY_LAUNCH_PARAMS,DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND,DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_FRONTEND,DesktopExperienceFlags.ENABLE_PINNING_APP_WITH_CONTEXT_MENU,DesktopExperienceFlags.ENABLE_REJECT_HOME_TRANSITION,DesktopExperienceFlags.ENABLE_TASKBAR_RECENT_TASKS_THROTTLE_BUGFIX
Vigilante: sin verificar todavía.
Los cambios solo se aplican al reiniciar.
--- /data/adb/nexus_quickstep:
watchdog.log
--- watchdog.log:
2026-10-20 11:34:52 cmd overlay list no devolvió el overlay: cmd: Can't find service: overlay 
2026-10-20 11:34:55 cmd overlay list no devolvió el overlay:  
2026-10-20 11:34:56 post-fs-data: overlay copiado a /data/local/tmp/nexusmod/system/product/overlay/NexusRecentsOverlay.apk
2026-10-20 11:35:37 arranque con el overlay pedido: vigilando (espera 150s, vigilancia 60s)
2026-10-20 11:36:40 cmd overlay list no devolvió el overlay:  
2026-10-20 11:36:44 cmd overlay list no devolvió el overlay:  
2026-10-20 11:38:04 SystemUI enlazado al servicio de Nexus tras 145s
2026-10-20 11:40:52 cmd overlay list no devolvió el overlay: cmd: Failure calling service overlay: Broken pipe (32) 
2026-10-20 11:40:55 cmd overlay list no devolvió el overlay:  
--- caídas:
10-20 11:40:51.018  5491  5549 E AndroidRuntime: FATAL EXCEPTION: UiThreadHelper
10-20 11:40:51.018  5491  5549 E AndroidRuntime: Process: com.google.android.apps.nexuslauncher, PID: 5491

## Fase 7: recuperación automática (Nexus no consigue ofrecer el servicio de Quickstep)
- auto-recovery: exit 0
reinicio por el vigilante: exit 0
## after-guard-reboot
config_recentsComponentName: app.nexus.launcher/com.android.quickstep.RecentsActivity
[after-guard-reboot] permisos permitidos y no concedidos: 0 ()
--- nexus-quickstep bound:
SystemUI enlazado al servicio de Quickstep de Nexus: NO
(overlay efectivo: sí; estado: missing)
--- nexus-quickstep status:
Pedido (enabled): no. El módulo no aplica el overlay (estado de serie).
Overlay app.nexus.launcher.overlay.recents en este arranque: missing
  APK en /product/overlay: sí
config_recentsComponentName ahora: app.nexus.launcher/com.android.quickstep.RecentsActivity
Overlay efectivo: sí (Nexus es el proveedor de recientes)
SystemUI enlazado al servicio de Nexus: NO
Comprobación de Nexus: sdk=37 compatible=true
  Ausente pero tolerado por Nexus: DesktopModeFlags.ENABLE_DESKTOP_APP_LAUNCH_ALTTAB_TRANSITIONS_BUGFIX,DesktopModeFlags.ENABLE_DESKTOP_APP_LAUNCH_TRANSITIONS_BUGFIX,DesktopModeFlags.ENABLE_DESKTOP_TRAMPOLINE_CLOSE_ANIMATION_BUGFIX,DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_EXIT_BY_MINIMIZE_TRANSITION_BUGFIX,DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_QUICK_SWITCH,DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_TASKBAR_RUNNING_APPS,DesktopModeFlags.ENABLE_QUICKSWITCH_DESKTOP_SPLIT_BUGFIX,DesktopModeFlags.ENABLE_START_LAUNCH_TRANSITION_FROM_TASKBAR_BUGFIX,DesktopModeFlags.ENABLE_TASKBAR_RECENTS_LAYOUT_TRANSITION,DesktopExperienceFlags.ENABLE_DESKTOP_APP_LAUNCH_BUGFIX,DesktopExperienceFlags.ENABLE_DESKTOP_FIRST_FULLSCREEN_REFOCUS_BUGFIX,DesktopExperienceFlags.ENABLE_DESKTOP_WINDOWING_PIP,DesktopExperienceFlags.ENABLE_FREEFORM_DISPLAY_LAUNCH_PARAMS,DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND,DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_FRONTEND,DesktopExperienceFlags.ENABLE_PINNING_APP_WITH_CONTEXT_MENU,DesktopExperienceFlags.ENABLE_REJECT_HOME_TRANSITION,DesktopExperienceFlags.ENABLE_TASKBAR_RECENT_TASKS_THROTTLE_BUGFIX
Vigilante: desactivó el overlay -> 2026-10-20 11:42:39 SystemUI no se enlazó al servicio de Nexus en 60 s
Los cambios solo se aplican al reiniciar.
--- /data/adb/nexus_quickstep:
tripped
tripped.meta
watchdog.log
tripped: 2026-10-20 11:42:39 SystemUI no se enlazó al servicio de Nexus en 60 s
--- watchdog.log:
2026-10-20 11:35:37 arranque con el overlay pedido: vigilando (espera 150s, vigilancia 60s)
2026-10-20 11:36:40 cmd overlay list no devolvió el overlay:  
2026-10-20 11:36:44 cmd overlay list no devolvió el overlay:  
2026-10-20 11:38:04 SystemUI enlazado al servicio de Nexus tras 145s
2026-10-20 11:40:52 cmd overlay list no devolvió el overlay: cmd: Failure calling service overlay: Broken pipe (32) 
2026-10-20 11:40:55 cmd overlay list no devolvió el overlay:  
2026-10-20 11:40:57 post-fs-data: overlay copiado a /data/local/tmp/nexusmod/system/product/overlay/NexusRecentsOverlay.apk
2026-10-20 11:41:38 arranque con el overlay pedido: vigilando (espera 60s, vigilancia 30s)
2026-10-20 11:42:39 DESACTIVO el overlay: SystemUI no se enlazó al servicio de Nexus en 60 s
2026-10-20 11:42:39 reiniciando para arrancar sin overlay
2026-10-20 11:43:21 cmd overlay list no devolvió el overlay:  
2026-10-20 11:43:24 cmd overlay list no devolvió el overlay:  
--- caídas:
10-20 11:43:10.152  1053  1053 E AndroidRuntime: FATAL EXCEPTION: main
10-20 11:43:10.152  1053  1053 E AndroidRuntime: Process: com.google.android.apps.nexuslauncher, PID: 1053
10-20 11:43:14.068  1670  1670 E AndroidRuntime: FATAL EXCEPTION: main
10-20 11:43:14.068  1670  1670 E AndroidRuntime: Process: com.google.android.apps.nexuslauncher, PID: 1670
rc=0
total 0
tripped
tripped.meta
watchdog.log
2026-10-20 11:42:39 DESACTIVO el overlay: SystemUI no se enlazó al servicio de Nexus en 60 s
2026-10-20 11:42:39 reiniciando para arrancar sin overlay
2026-10-20 11:43:21 cmd overlay list no devolvió el overlay:  
2026-10-20 11:43:24 cmd overlay list no devolvió el overlay:  
montado: no
## after-trip
config_recentsComponentName: com.google.android.apps.nexuslauncher/com.android.quickstep.RecentsActivity
[after-trip] permisos permitidos y no concedidos: 11 (ACCESS_SHORTCUTS INTERNAL_SYSTEM_WINDOW MANAGE_ACCESSIBILITY MANAGE_ACTIVITY_TASKS MANAGE_KEY_GESTURES MONITOR_INPUT READ_FRAME_BUFFER REMOVE_TASKS SET_ORIENTATION STATUS_BAR_SERVICE SYSTEM_APPLICATION_OVERLAY)
--- nexus-quickstep bound:
SystemUI enlazado al servicio de Quickstep de Nexus: NO
(overlay efectivo: no; estado: missing)
--- nexus-quickstep status:
Pedido (enabled): no. El módulo no aplica el overlay (estado de serie).
Overlay app.nexus.launcher.overlay.recents en este arranque: missing
  APK en /product/overlay: no
config_recentsComponentName ahora: com.google.android.apps.nexuslauncher/com.android.quickstep.RecentsActivity
Overlay efectivo: no
Comprobación de Nexus: sdk=37 compatible=true
  Ausente pero tolerado por Nexus: DesktopModeFlags.ENABLE_DESKTOP_APP_LAUNCH_ALTTAB_TRANSITIONS_BUGFIX,DesktopModeFlags.ENABLE_DESKTOP_APP_LAUNCH_TRANSITIONS_BUGFIX,DesktopModeFlags.ENABLE_DESKTOP_TRAMPOLINE_CLOSE_ANIMATION_BUGFIX,DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_EXIT_BY_MINIMIZE_TRANSITION_BUGFIX,DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_QUICK_SWITCH,DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_TASKBAR_RUNNING_APPS,DesktopModeFlags.ENABLE_QUICKSWITCH_DESKTOP_SPLIT_BUGFIX,DesktopModeFlags.ENABLE_START_LAUNCH_TRANSITION_FROM_TASKBAR_BUGFIX,DesktopModeFlags.ENABLE_TASKBAR_RECENTS_LAYOUT_TRANSITION,DesktopExperienceFlags.ENABLE_DESKTOP_APP_LAUNCH_BUGFIX,DesktopExperienceFlags.ENABLE_DESKTOP_FIRST_FULLSCREEN_REFOCUS_BUGFIX,DesktopExperienceFlags.ENABLE_DESKTOP_WINDOWING_PIP,DesktopExperienceFlags.ENABLE_FREEFORM_DISPLAY_LAUNCH_PARAMS,DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND,DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_FRONTEND,DesktopExperienceFlags.ENABLE_PINNING_APP_WITH_CONTEXT_MENU,DesktopExperienceFlags.ENABLE_REJECT_HOME_TRANSITION,DesktopExperienceFlags.ENABLE_TASKBAR_RECENT_TASKS_THROTTLE_BUGFIX
Vigilante: desactivó el overlay -> 2026-10-20 11:42:39 SystemUI no se enlazó al servicio de Nexus en 60 s
Los cambios solo se aplican al reiniciar.
--- /data/adb/nexus_quickstep:
tripped
tripped.meta
watchdog.log
tripped: 2026-10-20 11:42:39 SystemUI no se enlazó al servicio de Nexus en 60 s
--- watchdog.log:
2026-10-20 11:36:44 cmd overlay list no devolvió el overlay:  
2026-10-20 11:38:04 SystemUI enlazado al servicio de Nexus tras 145s
2026-10-20 11:40:52 cmd overlay list no devolvió el overlay: cmd: Failure calling service overlay: Broken pipe (32) 
2026-10-20 11:40:55 cmd overlay list no devolvió el overlay:  
2026-10-20 11:40:57 post-fs-data: overlay copiado a /data/local/tmp/nexusmod/system/product/overlay/NexusRecentsOverlay.apk
2026-10-20 11:41:38 arranque con el overlay pedido: vigilando (espera 60s, vigilancia 30s)
2026-10-20 11:42:39 DESACTIVO el overlay: SystemUI no se enlazó al servicio de Nexus en 60 s
2026-10-20 11:42:39 reiniciando para arrancar sin overlay
2026-10-20 11:43:21 cmd overlay list no devolvió el overlay:  
2026-10-20 11:43:24 cmd overlay list no devolvió el overlay:  
2026-10-20 11:45:12 cmd overlay list no devolvió el overlay:  
2026-10-20 11:45:15 cmd overlay list no devolvió el overlay:  
--- caídas:
10-20 11:44:12.779  1049  1088 E AndroidRuntime: FATAL EXCEPTION: UiThreadHelper
10-20 11:44:12.779  1049  1088 E AndroidRuntime: Process: com.google.android.apps.nexuslauncher, PID: 1049
10-20 11:45:11.652  5287  5359 E AndroidRuntime: FATAL EXCEPTION: UiThreadHelper
10-20 11:45:11.652  5287  5359 E AndroidRuntime: Process: com.google.android.apps.nexuslauncher, PID: 5287
--- enable tras el fallo:
nexus-quickstep: el overlay se desactivó solo (2026-10-20 11:42:39 SystemUI no se enlazó al servicio de Nexus en 60 s). Tras un fallo de arranque o con esta versión de Nexus (1601009900) hace falta --force; si es solo la versión, actualizar Nexus también vale.
exit: 1
--- enable --force:
Overlay pedido. REINICIA para que el sistema lo aplique en el arranque.
Tras reiniciar: nexus-quickstep status  y  nexus-quickstep bound
Si SystemUI no llega a enlazarse con Nexus, el vigilante lo desactiva y reinicia solo.
Para deshacerlo: nexus-quickstep disable --reboot
exit: 0

## Fase 8: protección contra bucle de arranque (marcador booting)
- bootloop: exit 0
rc=0
tripped
tripped.meta
watchdog.log
2026-10-20 11:45:16 el arranque anterior con el overlay no terminó
nexus-quickstep: el overlay se desactivó solo (2026-10-20 11:45:16 el arranque anterior con el overlay no terminó). Tras un fallo de arranque o con esta versión de Nexus (1601009900) hace falta --force; si es solo la versión, actualizar Nexus también vale.
exit: 1

## Resumen de comprobaciones (emulador Android ro.build.version.release=17)
OK    enable pide el overlay (archivo enabled)
OK    antes de reiniciar el overlay aún no es efectivo
OK    tras reiniciar config_recentsComponentName apunta a Nexus
OK    tras reiniciar Nexus tiene los permisos de allow.txt (0 sin conceder)
FALLA SystemUI enlazado al servicio de Nexus
FALLA el vigilante dejó el arranque verificado (healthy)
FALLA el marcador booting se borró
OK    disable --reboot: post-fs-data.sh no monta el APK
OK    tras disable el sistema vuelve a los recientes de serie
OK    tras disable SystemUI ya no se enlaza a Nexus
OK    el vigilante reinició el dispositivo
OK    tras el fallo no queda archivo enabled
OK    el vigilante dejó constancia (tripped)
OK    tras el fallo los recientes vuelven a los de serie
OK    enable se niega tras el fallo con la misma versión
OK    enable --force lo permite
OK    bucle de arranque: post-fs-data.sh quita enabled
OK    bucle de arranque: no copia el APK
OK    bucle de arranque: enable exige --force

(fin)
