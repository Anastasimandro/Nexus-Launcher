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
== RECURSOS DEL FRAMEWORK: 860 referencias, 14793 recursos en el sistema
  todas existen
== IDs: 11 recursos distintos (11 referencias) entre el android.jar de compilación y el sistema
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
  ID DISTINTO android:dimen/navigation_bar_frame_height compilado=0x01050283 sistema=0x0105027b  <- /home/runner/work/Nexus-Launcher/Nexus-Launcher/.github/scripts/../../res/values/dimens.xml:437

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

## Fase 4: activar con 'nexus-quickstep enable', aplicar en el arranque y verificar el enlace
