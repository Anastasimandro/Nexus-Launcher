# Experimento A: Nexus con el paquete del launcher de la ROM (com.google.android.apps.nexuslauncher)
ro.build.version.release=17
ro.build.version.sdk=37
ro.build.fingerprint=google/sdk_gphone64_x86_64/emu64xa:17/CE2A.260420.051.A3/16571990:userdebug/dev-keys
config_recentsComponentName de la ROM: com.google.android.apps.nexuslauncher/com.android.quickstep.RecentsActivity
APK del launcher de la ROM: /system_ext/priv-app/NexusLauncherRelease/NexusLauncherRelease.apk
!! el arranque no terminó en 10 min
!! el arranque no terminó en 10 min

## Resultado

| paso | permisos clave | faltan | config_recentsComponentName | SystemUI enlazado | launcher de Inicio |
|---|---|---|---|---|---|
| 0-rom-original | 0/6 |  MANAGE_ACTIVITY_TASKS MONITOR_INPUT READ_FRAME_BUFFER REMOVE_TASKS STATUS_BAR_SERVICE START_TASKS_FROM_RECENTS | cmd: Failure calling service overlay: Broken pipe (32) | no | cmd: Failure calling service shortcut: Broken pipe (32) |
| 1-sustituido-reinicio-1 | 0/6 |  MANAGE_ACTIVITY_TASKS MONITOR_INPUT READ_FRAME_BUFFER REMOVE_TASKS STATUS_BAR_SERVICE START_TASKS_FROM_RECENTS | cmd: Can't find service: overlay | no | cmd: Can't find service: shortcut |
| 2-tras-inicio | 0/6 |  MANAGE_ACTIVITY_TASKS MONITOR_INPUT READ_FRAME_BUFFER REMOVE_TASKS STATUS_BAR_SERVICE START_TASKS_FROM_RECENTS | cmd: Can't find service: overlay | no | cmd: Can't find service: shortcut |
| 3-reinicio-2 | 0/6 |  MANAGE_ACTIVITY_TASKS MONITOR_INPUT READ_FRAME_BUFFER REMOVE_TASKS STATUS_BAR_SERVICE START_TASKS_FROM_RECENTS | cmd: Can't find service: overlay | no | cmd: Can't find service: shortcut |
