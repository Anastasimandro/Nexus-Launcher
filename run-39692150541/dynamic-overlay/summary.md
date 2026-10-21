# Experimento: overlay normal (no estático) + recálculo de permisos

ro.build.version.release=17
ro.build.version.sdk=37
ro.build.fingerprint=google/sdk_gphone64_x86_64/emu64xa:17/CE2A.260420.019/15611780:userdebug/dev-keys

## Resultado

| paso | permisos clave | faltan | config_recentsComponentName | SystemUI enlazado | overlay |
|---|---|---|---|---|---|
| 0-base | 0/6 |  MANAGE_ACTIVITY_TASKS MONITOR_INPUT READ_FRAME_BUFFER REMOVE_TASKS STATUS_BAR_SERVICE START_TASKS_FROM_RECENTS | cmd: Can't find service: overlay | no | cmd: Can't find service: overlay  |
| 1-activado | 1/6 |  MANAGE_ACTIVITY_TASKS MONITOR_INPUT READ_FRAME_BUFFER REMOVE_TASKS STATUS_BAR_SERVICE | com.google.android.apps.nexuslauncher/com.android.quickstep.RecentsActivity | no | [ ] app.nexus.launcher.overlay.recents  |
| 2-systemui-reiniciado | 1/6 |  MANAGE_ACTIVITY_TASKS MONITOR_INPUT READ_FRAME_BUFFER REMOVE_TASKS STATUS_BAR_SERVICE | com.google.android.apps.nexuslauncher/com.android.quickstep.RecentsActivity | no | [ ] app.nexus.launcher.overlay.recents  |
| 3-reinstalado | 1/6 |  MANAGE_ACTIVITY_TASKS MONITOR_INPUT READ_FRAME_BUFFER REMOVE_TASKS STATUS_BAR_SERVICE | com.google.android.apps.nexuslauncher/com.android.quickstep.RecentsActivity | no | [ ] app.nexus.launcher.overlay.recents  |
| 4-install-existing | 1/6 |  MANAGE_ACTIVITY_TASKS MONITOR_INPUT READ_FRAME_BUFFER REMOVE_TASKS STATUS_BAR_SERVICE | com.google.android.apps.nexuslauncher/com.android.quickstep.RecentsActivity | no | [ ] app.nexus.launcher.overlay.recents  |
| 5-framework-reiniciado | 0/6 |  MANAGE_ACTIVITY_TASKS MONITOR_INPUT READ_FRAME_BUFFER REMOVE_TASKS STATUS_BAR_SERVICE START_TASKS_FROM_RECENTS | cmd: Can't find service: overlay | no | cmd: Can't find service: overlay  |
| 6-reinicio | 1/6 |  MANAGE_ACTIVITY_TASKS MONITOR_INPUT READ_FRAME_BUFFER REMOVE_TASKS STATUS_BAR_SERVICE | com.google.android.apps.nexuslauncher/com.android.quickstep.RecentsActivity | no | [ ] app.nexus.launcher.overlay.recents  |

Lectura: si algún paso muestra 6/6 permisos y SystemUI enlazado = sí con config_recentsComponentName apuntando a Nexus, el overlay normal sirve. Si no, el sistema solo concede los permisos de recientes al arrancar con el overlay estático.
