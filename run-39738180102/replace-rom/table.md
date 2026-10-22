| paso | permisos clave | faltan | config_recentsComponentName | SystemUI enlazado | launcher de Inicio |
|---|---|---|---|---|---|
| 0-rom-original | 0/6 |  MANAGE_ACTIVITY_TASKS MONITOR_INPUT READ_FRAME_BUFFER REMOVE_TASKS STATUS_BAR_SERVICE START_TASKS_FROM_RECENTS | cmd: Failure calling service overlay: Broken pipe (32) | no | cmd: Failure calling service shortcut: Broken pipe (32) |
| 1-sustituido-reinicio-1 | 0/6 |  MANAGE_ACTIVITY_TASKS MONITOR_INPUT READ_FRAME_BUFFER REMOVE_TASKS STATUS_BAR_SERVICE START_TASKS_FROM_RECENTS | cmd: Can't find service: overlay | no | cmd: Can't find service: shortcut |
| 2-tras-inicio | 0/6 |  MANAGE_ACTIVITY_TASKS MONITOR_INPUT READ_FRAME_BUFFER REMOVE_TASKS STATUS_BAR_SERVICE START_TASKS_FROM_RECENTS | cmd: Can't find service: overlay | no | cmd: Can't find service: shortcut |
| 3-reinicio-2 | 0/6 |  MANAGE_ACTIVITY_TASKS MONITOR_INPUT READ_FRAME_BUFFER REMOVE_TASKS STATUS_BAR_SERVICE START_TASKS_FROM_RECENTS | cmd: Can't find service: overlay | no | cmd: Can't find service: shortcut |
