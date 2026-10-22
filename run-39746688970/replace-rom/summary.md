# Experimento A: Nexus con el paquete del launcher de la ROM (com.google.android.apps.nexuslauncher)
ro.build.version.release=17
ro.build.version.sdk=37
ro.build.fingerprint=google/sdk_gphone64_x86_64/emu64xa:17/CE2A.260420.051.A3/16571990:userdebug/dev-keys
config_recentsComponentName de la ROM: com.google.android.apps.nexuslauncher/com.android.quickstep.RecentsActivity
APK del launcher de la ROM: /system_ext/priv-app/NexusLauncherRelease/NexusLauncherRelease.apk
Partición del launcher de la ROM: /system_ext

## Resultado

| paso | permisos clave | faltan | config_recentsComponentName | SystemUI enlazado | launcher de Inicio | ABI |
|---|---|---|---|---|---|---|
| 0-rom-original | 6/6 | ninguno | com.google.android.apps.nexuslauncher/com.android.quickstep.RecentsActivity | no | Launcher: ComponentInfo{com.google.android.apps.nexuslauncher/com.google.android.apps.nexuslauncher.NexusLauncherActivity} | primaryCpuAbi=null |
| 1-sustituido-reinicio-1 | 6/6 | ninguno | com.google.android.apps.nexuslauncher/com.android.quickstep.RecentsActivity | no | Launcher: ComponentInfo{com.google.android.apps.nexuslauncher/app.lawnchair.LawnchairLauncher} | primaryCpuAbi=null |
| 2-tras-inicio | 6/6 | ninguno | com.google.android.apps.nexuslauncher/com.android.quickstep.RecentsActivity | no | Launcher: ComponentInfo{com.google.android.apps.nexuslauncher/app.lawnchair.LawnchairLauncher} | primaryCpuAbi=null |
| 2b-tras-pm-clear | 6/6 | ninguno | com.google.android.apps.nexuslauncher/com.android.quickstep.RecentsActivity | no | Launcher: ComponentInfo{com.google.android.apps.nexuslauncher/app.lawnchair.LawnchairLauncher} | primaryCpuAbi=null |
| 2c-actualizado-por-pm-install | 6/6 | ninguno | com.google.android.apps.nexuslauncher/com.android.quickstep.RecentsActivity | no | ? | primaryCpuAbi=x86_64 |
| 3-reinicio-2 | 6/6 | ninguno | com.google.android.apps.nexuslauncher/com.android.quickstep.RecentsActivity | sí | Launcher: ComponentInfo{com.google.android.apps.nexuslauncher/app.lawnchair.LawnchairLauncher} | primaryCpuAbi=x86_64 |
