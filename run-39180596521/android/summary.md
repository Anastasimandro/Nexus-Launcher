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
wm_size=cmd: Failure calling service window: Broken pipe (32)

## Fase 1: auditoría estática contra los binarios del sistema (sin módulo)
- static-audit-run: exit 0
app: 3 DEX, 11591 clases | sistema: 56 DEX, 63054 clases (con extra: 63054)
== BUSCAR 'ScreenCapture': 44 clases
== BUSCAR 'IRecentTasks': 0 clases
== BUSCAR 'ILauncherProxy': 0 clases
== BUSCAR 'IOverviewProxy': 0 clases
== BUSCAR 'ISystemUiProxy': 0 clases
== BUSCAR 'IRecentsAnimation': 0 clases
== BUSCAR 'IShellTransitions': 0 clases
== ESCANEO de bytecode: 69718 métodos recorridos, 0 desalineados (debe ser 0)
== ENLACE: clases que la app usa y el sistema no tiene (66)
== ENLACE: campos que faltan (2 clases)
== ENLACE: métodos que faltan (6 clases)
== ENLACE: total 92 (clases referenciadas: 13042, campos: 46261, métodos: 81067)
== AIDL: interfaces comparadas: 3; con diferencias: contadas arriba; solo en la app (no están en los binarios dados): 3
== AIDL: total de diferencias 0
  AUSENTE  Lcom/android/wm/shell/shared/IFocusTransitionListener2 (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lcom/android/wm/shell/shared/IHomeTransitionListener2 (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lcom/android/wm/shell/shared/IShellTransitions2 (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lcom/android/wm/shell/back/IBackAnimation (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lcom/android/wm/shell/bubbles/IBubbles (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lcom/android/wm/shell/bubbles/IBubblesListener (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lcom/android/wm/shell/common/pip/IPip (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lcom/android/wm/shell/common/pip/IPipAnimationListener (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lcom/android/wm/shell/desktopmode/IDesktopMode (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lcom/android/wm/shell/desktopmode/IDesktopTaskListener (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lcom/android/wm/shell/desktopmode/IMoveToDesktopCallback (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lcom/android/wm/shell/draganddrop/IDragAndDrop (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lcom/android/wm/shell/onehanded/IOneHanded (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lcom/android/wm/shell/recents/IRecentTasks (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lcom/android/wm/shell/recents/IRecentTasksListener (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lcom/android/wm/shell/recents/IRecentsAnimationController (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lcom/android/wm/shell/recents/IRecentsAnimationRunner (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lcom/android/wm/shell/splitscreen/ISplitScreen (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lcom/android/wm/shell/splitscreen/ISplitScreenListener (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lcom/android/wm/shell/splitscreen/ISplitSelectListener (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lcom/android/wm/shell/startingsurface/IStartingWindow (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lcom/android/wm/shell/startingsurface/IStartingWindowListener (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lcom/android/systemui/animation/shared/IOriginTransitions (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lcom/android/systemui/shared/recents/ILauncherProxy (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lcom/android/systemui/shared/recents/IOverviewProxy (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lcom/android/systemui/shared/recents/ISystemUiProxy (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lcom/android/systemui/shared/system/smartspace/ILauncherUnlockAnimationController (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lcom/android/systemui/shared/system/smartspace/ISysuiUnlockAnimationController (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lcom/android/systemui/unfold/progress/IUnfoldAnimation (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lcom/android/systemui/unfold/progress/IUnfoldTransitionListener (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lamirz/aidlbridge/IBridge (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lamirz/aidlbridge/IBridgeCallback (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lapp/lawnchair/root/IRootHelper (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lcom/google/android/libraries/launcherclient/ILauncherOverlay (la interfaz no está en el sistema con ese nombre)
  AUSENTE  Lcom/google/android/libraries/launcherclient/ILauncherOverlayCallback (la interfaz no está en el sistema con ese nombre)
== AIDL-FUENTES: 4 interfaces comparadas, 35 ausentes en el sistema, 0 diferencias

## Fase 2: instalar el módulo como lo hace Magisk (remount)
- install-module: exit 0

## Fase 3: el módulo en Android real (app privilegiada y overlay)
- module-evidence: exit 0
overlay (nuestro):
cmd: Failure calling service overlay: Broken pipe (32)
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

## Fase 4: activar el overlay y comprobar que SystemUI se enlaza a Nexus
- enable-overlay: exit 0
nexus-quickstep: Nexus aún no ha comprobado la compatibilidad: ábrelo una vez (o usa --force).
exit del primer enable (sin --force): 1
---
app.nexus.launcher/com.android.quickstep.RecentsActivity
---
SystemUI enlazado al servicio de Quickstep de Nexus: sí
      * Client AppBindRecord{79625 ProcessRecord{c37bba1 889:com.android.systemui/u0a201}}
    binding=AppBindRecord{79625 app.nexus.launcher/com.android.quickstep.TouchInteractionService:com.android.systemui}
--- caídas:
10-19 00:36:22.240  1429  1429 E AndroidRuntime: FATAL EXCEPTION: main
10-19 00:36:22.240  1429  1429 E AndroidRuntime: Process: app.nexus.launcher, PID: 1429
10-19 00:36:29.104  2043  2043 E AndroidRuntime: FATAL EXCEPTION: main
10-19 00:36:29.104  2043  2043 E AndroidRuntime: Process: app.nexus.launcher, PID: 2043

## Fase 5: gestos con el overlay activo (inyectados con 'input')
- gestures: exit 0
pantalla 1080x2400
=== A. Inicio con gesto rápido
antes:     topResumedActivity=ActivityRecord{129907379 u0 com.android.settings/.homepage.SettingsHomepageActivity t7}
después:     topResumedActivity=ActivityRecord{129907379 u0 com.android.settings/.homepage.SettingsHomepageActivity t7}
=== B. Inicio con gesto lento: captura a mitad del gesto (la ventana debe seguir el dedo)
después:     topResumedActivity=ActivityRecord{129907379 u0 com.android.settings/.homepage.SettingsHomepageActivity t7}
=== C. Cancelación: subir y volver al punto de partida (debe quedarse en la app)
después:     topResumedActivity=ActivityRecord{129907379 u0 com.android.settings/.homepage.SettingsHomepageActivity t7}
=== D. Cancelación con ACTION_CANCEL a mitad del gesto
después:     topResumedActivity=ActivityRecord{129907379 u0 com.android.settings/.homepage.SettingsHomepageActivity t7}
=== E. Atrás
después: 
=== F. Recientes: subir y mantener
después:       topResumedActivity=ActivityRecord{63673960 u0 com.google.android.apps.nexuslauncher/.NexusLauncherActivity t2}
=== G. Rotación (horizontal) y gesto de Inicio
después (apaisado):     topResumedActivity=ActivityRecord{260703883 u0 com.android.settings/.homepage.SettingsHomepageActivity t8}
=== H. Bloqueo y desbloqueo, y gesto de Inicio
después:     topResumedActivity=ActivityRecord{255304157 u0 com.android.settings/.SubSettings t9}

## Fase 6: recuperación manual (nexus-quickstep disable)
- disable-overlay: exit 0
nexus-quickstep: el overlay no está instalado.
app.nexus.launcher/com.android.quickstep.RecentsActivity
SystemUI enlazado al servicio de Quickstep de Nexus: sí
      * Client AppBindRecord{49e228c ProcessRecord{c677c08 10362:com.android.systemui/u0a201}}
    binding=AppBindRecord{49e228c app.nexus.launcher/com.android.quickstep.TouchInteractionService:com.android.systemui}
tras el gesto, con el overlay desactivado:     topResumedActivity=ActivityRecord{209651578 u0 com.android.settings/.SubSettings t10}

## Fase 7: recuperación automática (Nexus no consigue iniciar Quickstep)
- failure-recovery: exit 1
Overlay app.nexus.launcher.overlay.recents: enabled
config_recentsComponentName ahora: app.nexus.launcher/com.android.quickstep.RecentsActivity
SystemUI enlazado al servicio de Nexus: sí
Comprobación de Nexus: sdk=37 compatible=false
  Falta en este Android: DesktopModeFlags.ENABLE_DESKTOP_APP_LAUNCH_ALTTAB_TRANSITIONS_BUGFIX,DesktopModeFlags.ENABLE_DESKTOP_APP_LAUNCH_TRANSITIONS_BUGFIX,DesktopModeFlags.ENABLE_DESKTOP_TRAMPOLINE_CLOSE_ANIMATION_BUGFIX,DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_EXIT_BY_MINIMIZE_TRANSITION_BUGFIX,DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_QUICK_SWITCH,DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_TASKBAR_RUNNING_APPS,DesktopModeFlags.ENABLE_QUICKSWITCH_DESKTOP_SPLIT_BUGFIX,DesktopModeFlags.ENABLE_START_LAUNCH_TRANSITION_FROM_TASKBAR_BUGFIX,DesktopModeFlags.ENABLE_TASKBAR_RECENTS_LAYOUT_TRANSITION,DesktopExperienceFlags.ENABLE_DESKTOP_APP_LAUNCH_BUGFIX,DesktopExperienceFlags.ENABLE_DESKTOP_FIRST_FULLSCREEN_REFOCUS_BUGFIX,DesktopExperienceFlags.ENABLE_DESKTOP_WINDOWING_PIP,DesktopExperienceFlags.ENABLE_FREEFORM_DISPLAY_LAUNCH_PARAMS,DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND,DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_FRONTEND,DesktopExperienceFlags.ENABLE_PINNING_APP_WITH_CONTEXT_MENU,DesktopExperienceFlags.ENABLE_REJECT_HOME_TRANSITION,DesktopExperienceFlags.ENABLE_TASKBAR_RECENT_TASKS_THROTTLE_BUGFIX
Vigilante: el último arranque con el overlay activo quedó verificado.
Los cambios del overlay solo se aplican al reiniciar.
app.nexus.launcher/com.android.quickstep.RecentsActivity
2026-10-19 00:36:32 arranque con el overlay activo: vigilando (espera 150s, vigilancia 300s)
2026-10-19 00:36:32 SystemUI enlazado al servicio de Nexus tras 0s
2026-10-19 00:38:54 el overlay se desactivó durante la vigilancia
2026-10-19 00:40:42 arranque con el overlay activo: vigilando (espera 60s, vigilancia 30s)
2026-10-19 00:40:42 SystemUI enlazado al servicio de Nexus tras 0s
2026-10-19 00:41:13 vigilancia terminada: enlace estable
tras el gesto, con el vigilante ya disparado:     topResumedActivity=ActivityRecord{131229025 u0 com.android.settings/.homepage.SettingsHomepageActivity t11}
nexus-quickstep: Quickstep no es compatible con este Android (sdk=37; falta: DesktopModeFlags.ENABLE_DESKTOP_APP_LAUNCH_ALTTAB_TRANSITIONS_BUGFIX,DesktopModeFlags.ENABLE_DESKTOP_APP_LAUNCH_TRANSITIONS_BUGFIX,DesktopModeFlags.ENABLE_DESKTOP_TRAMPOLINE_CLOSE_ANIMATION_BUGFIX,DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_EXIT_BY_MINIMIZE_TRANSITION_BUGFIX,DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_QUICK_SWITCH,DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_TASKBAR_RUNNING_APPS,DesktopModeFlags.ENABLE_QUICKSWITCH_DESKTOP_SPLIT_BUGFIX,DesktopModeFlags.ENABLE_START_LAUNCH_TRANSITION_FROM_TASKBAR_BUGFIX,DesktopModeFlags.ENABLE_TASKBAR_RECENTS_LAYOUT_TRANSITION,DesktopExperienceFlags.ENABLE_DESKTOP_APP_LAUNCH_BUGFIX,DesktopExperienceFlags.ENABLE_DESKTOP_FIRST_FULLSCREEN_REFOCUS_BUGFIX,DesktopExperienceFlags.ENABLE_DESKTOP_WINDOWING_PIP,DesktopExperienceFlags.ENABLE_FREEFORM_DISPLAY_LAUNCH_PARAMS,DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND,DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_FRONTEND,DesktopExperienceFlags.ENABLE_PINNING_APP_WITH_CONTEXT_MENU,DesktopExperienceFlags.ENABLE_REJECT_HOME_TRANSITION,DesktopExperienceFlags.ENABLE_TASKBAR_RECENT_TASKS_THROTTLE_BUGFIX). Con --force se activa igualmente, pero los gestos pueden dejar de funcionar.

(fin)
