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
android.jar de compilación: /usr/local/lib/android/sdk/platforms/android-37.2/android.jar
   android.jar de compilación: 15042 recursos
== RECURSOS DEL FRAMEWORK: 866 referencias, 14793 recursos en el sistema
  todas existen
== IDs: 16 recursos distintos (17 referencias) entre el android.jar de compilación y el sistema
  ID DISTINTO android:color/system_error_dim_dark compilado=0x010600e3 sistema=0x01960005  <- /home/runner/work/Nexus-Launcher/Nexus-Launcher/.github/scripts/../../res/values-v34/colors.xml:95
  ID DISTINTO android:color/system_error_dim_light compilado=0x010600de sistema=0x01960000  <- /home/runner/work/Nexus-Launcher/Nexus-Launcher/.github/scripts/../../res/values-v34/colors.xml:40
  ID DISTINTO android:color/system_palette_key_color_error_dark compilado=0x010600e4 sistema=0x01960006  <- /home/runner/work/Nexus-Launcher/Nexus-Launcher/.github/scripts/../../res/values-v34/colors.xml:112
  ID DISTINTO android:color/system_palette_key_color_error_light compilado=0x010600df sistema=0x01960001  <- /home/runner/work/Nexus-Launcher/Nexus-Launcher/.github/scripts/../../res/values-v34/colors.xml:57
  ID DISTINTO android:color/system_primary_dim_dark compilado=0x010600e5 sistema=0x01960007  <- /home/runner/work/Nexus-Launcher/Nexus-Launcher/.github/scripts/../../res/values-v34/colors.xml:120
  ID DISTINTO android:color/system_primary_dim_light compilado=0x010600e0 sistema=0x01960002  <- /home/runner/work/Nexus-Launcher/Nexus-Launcher/.github/scripts/../../res/values-v34/colors.xml:65
  ID DISTINTO android:color/system_secondary_dim_dark compilado=0x010600e6 sistema=0x01960008  <- /home/runner/work/Nexus-Launcher/Nexus-Launcher/.github/scripts/../../res/values-v34/colors.xml:124
  ID DISTINTO android:color/system_secondary_dim_light compilado=0x010600e1 sistema=0x01960003  <- /home/runner/work/Nexus-Launcher/Nexus-Launcher/.github/scripts/../../res/values-v34/colors.xml:69
  ID DISTINTO android:color/system_tertiary_dim_dark compilado=0x010600e7 sistema=0x01960009  <- /home/runner/work/Nexus-Launcher/Nexus-Launcher/.github/scripts/../../res/values-v34/colors.xml:138
  ID DISTINTO android:color/system_tertiary_dim_light compilado=0x010600e2 sistema=0x01960004  <- /home/runner/work/Nexus-Launcher/Nexus-Launcher/.github/scripts/../../res/values-v34/colors.xml:83
  ID DISTINTO android:dimen/config_wallpaperMaxScale compilado=0x01050151 sistema=0x0105014c  <- /home/runner/work/Nexus-Launcher/Nexus-Launcher/.github/scripts/../../quickstep/res/values/config.xml:56
  ID DISTINTO android:dimen/navigation_bar_frame_height compilado=0x01050283 sistema=0x0105027b  <- /home/runner/work/Nexus-Launcher/Nexus-Launcher/.github/scripts/../../res/values/dimens.xml:437
  ID DISTINTO android:dimen/rounded_corner_content_padding compilado=0x01050341 sistema=0x0105033d  <- /home/runner/work/Nexus-Launcher/Nexus-Launcher/.github/scripts/../../res/values/dimens.xml:444
  ID DISTINTO android:dimen/taskbar_frame_height compilado=0x0105037b sistema=0x01050377  <- /home/runner/work/Nexus-Launcher/Nexus-Launcher/.github/scripts/../../quickstep/res/values/dimens.xml:363
  ID DISTINTO android:string/config_bodyFontFamily compilado=0x01040264 sistema=0x01040248  <- /home/runner/work/Nexus-Launcher/Nexus-Launcher/.github/scripts/../../quickstep/res/layout/bubblebar_flyout.xml:47
  ID DISTINTO android:string/config_bodyFontFamilyMedium compilado=0x01040265 sistema=0x01040249  <- /home/runner/work/Nexus-Launcher/Nexus-Launcher/.github/scripts/../../quickstep/res/layout/taskbar_divider_popup_menu.xml:60 (+1)

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
estado tras enable: enabled
lookup antes de reiniciar: com.google.android.apps.nexuslauncher/com.android.quickstep.RecentsActivity

## enabled
config_recentsComponentName: app.nexus.launcher/com.android.quickstep.RecentsActivity
[enabled] permisos permitidos y no concedidos: 0 ()
--- nexus-quickstep bound:
SystemUI enlazado al servicio de Quickstep de Nexus: sí
      * Client AppBindRecord{336d94 ProcessRecord{4f6de16 5342:com.android.systemui/u0a201}}
    binding=AppBindRecord{336d94 app.nexus.launcher/com.android.quickstep.TouchInteractionService:com.android.systemui}
--- nexus-quickstep status:
Pedido (enabled): sí. El módulo aplica el overlay en cada arranque.
Overlay app.nexus.launcher.overlay.recents en este arranque: aplicado (efectivo)
  APK en /product/overlay: sí
config_recentsComponentName ahora: app.nexus.launcher/com.android.quickstep.RecentsActivity
Overlay efectivo: sí (Nexus es el proveedor de recientes)
SystemUI enlazado al servicio de Nexus: sí
Comprobación de Nexus: sdk=37 compatible=true
  Ausente pero tolerado por Nexus: DesktopModeFlags.ENABLE_DESKTOP_APP_LAUNCH_ALTTAB_TRANSITIONS_BUGFIX,DesktopModeFlags.ENABLE_DESKTOP_APP_LAUNCH_TRANSITIONS_BUGFIX,DesktopModeFlags.ENABLE_DESKTOP_TRAMPOLINE_CLOSE_ANIMATION_BUGFIX,DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_EXIT_BY_MINIMIZE_TRANSITION_BUGFIX,DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_QUICK_SWITCH,DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_TASKBAR_RUNNING_APPS,DesktopModeFlags.ENABLE_QUICKSWITCH_DESKTOP_SPLIT_BUGFIX,DesktopModeFlags.ENABLE_START_LAUNCH_TRANSITION_FROM_TASKBAR_BUGFIX,DesktopModeFlags.ENABLE_TASKBAR_RECENTS_LAYOUT_TRANSITION,DesktopExperienceFlags.ENABLE_DESKTOP_APP_LAUNCH_BUGFIX,DesktopExperienceFlags.ENABLE_DESKTOP_FIRST_FULLSCREEN_REFOCUS_BUGFIX,DesktopExperienceFlags.ENABLE_DESKTOP_WINDOWING_PIP,DesktopExperienceFlags.ENABLE_FREEFORM_DISPLAY_LAUNCH_PARAMS,DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND,DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_FRONTEND,DesktopExperienceFlags.ENABLE_PINNING_APP_WITH_CONTEXT_MENU,DesktopExperienceFlags.ENABLE_REJECT_HOME_TRANSITION,DesktopExperienceFlags.ENABLE_TASKBAR_RECENT_TASKS_THROTTLE_BUGFIX
Vigilante: el último arranque con el overlay aplicado quedó verificado.
Los cambios solo se aplican al reiniciar.
--- /data/adb/nexus_quickstep:
enabled
healthy
watchdog.log
enabled: 2026-10-20 21:48:44 version=1601009900
healthy: 2026-10-20 21:49:59
--- watchdog.log:
2026-10-20 21:48:44 post-fs-data: overlay copiado a /data/local/tmp/nexusmod/system/product/overlay/NexusRecentsOverlay.apk
2026-10-20 21:49:32 arranque con el overlay pedido: vigilando (espera 240s, vigilancia 60s)
2026-10-20 21:49:59 SystemUI enlazado al servicio de Nexus tras 25s
--- caídas:
10-20 21:49:23.072  1039  1039 E AndroidRuntime: FATAL EXCEPTION: main
10-20 21:49:23.072  1039  1039 E AndroidRuntime: Process: com.google.android.apps.nexuslauncher, PID: 1039
10-20 21:49:27.392  1661  1661 E AndroidRuntime: FATAL EXCEPTION: main
10-20 21:49:27.392  1661  1661 E AndroidRuntime: Process: com.google.android.apps.nexuslauncher, PID: 1661
10-20 21:49:55.314  3200  3200 E AndroidRuntime: FATAL EXCEPTION: main
10-20 21:49:55.314  3200  3200 E AndroidRuntime: Process: com.google.android.apps.nexuslauncher, PID: 3200
10-20 21:49:59.542  4033  4033 E AndroidRuntime: FATAL EXCEPTION: main
10-20 21:49:59.542  4033  4033 E AndroidRuntime: Process: com.google.android.apps.nexuslauncher, PID: 4033
10-20 21:50:25.863  5499  5499 E AndroidRuntime: FATAL EXCEPTION: main
10-20 21:50:25.863  5499  5499 E AndroidRuntime: Process: com.google.android.apps.nexuslauncher, PID: 5499
10-20 21:50:30.405  6306  6306 E AndroidRuntime: FATAL EXCEPTION: main
10-20 21:50:30.405  6306  6306 E AndroidRuntime: Process: com.google.android.apps.nexuslauncher, PID: 6306

## Fase 5: gestos con Nexus como proveedor de Quickstep
- gestures: exit 0
pantalla 1080x2400
=== A. Inicio con gesto rápido
antes:     topResumedActivity=ActivityRecord{119847752 u0 com.android.settings/.homepage.SettingsHomepageActivity t7}
después:     topResumedActivity=ActivityRecord{119847752 u0 com.android.settings/.homepage.SettingsHomepageActivity t7}
=== B. Inicio con gesto lento: captura a mitad del gesto (la ventana debe seguir el dedo)
después:       topResumedActivity=ActivityRecord{2179881 u0 com.google.android.apps.nexuslauncher/.NexusLauncherActivity t2}
=== C. Cancelación: subir y volver al punto de partida (debe quedarse en la app)
después:     topResumedActivity=ActivityRecord{260088495 u0 com.android.settings/.homepage.SettingsHomepageActivity t7}
=== D. Cancelación con ACTION_CANCEL a mitad del gesto
después: 
=== E. Atrás
después:       topResumedActivity=ActivityRecord{86748590 u0 com.google.android.apps.nexuslauncher/.NexusLauncherActivity t2}
=== F. Recientes: subir y mantener
después:     topResumedActivity=ActivityRecord{75596696 u0 com.android.settings/.homepage.SettingsHomepageActivity t7}
=== G. Rotación (horizontal) y gesto de Inicio
después (apaisado):       topResumedActivity=ActivityRecord{169976533 u0 com.google.android.apps.nexuslauncher/.NexusLauncherActivity t2}
=== H. Bloqueo y desbloqueo, y gesto de Inicio
después:       topResumedActivity=ActivityRecord{126945727 u0 com.google.android.apps.nexuslauncher/.NexusLauncherActivity t2}

## Fase 6: deshacer a mano ('nexus-quickstep disable --reboot')
