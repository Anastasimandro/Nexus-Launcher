/*
 * Copyright (C) 2026 Nexus Launcher
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.shared.system;

import android.util.Log;
import android.window.TaskSnapshot;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Instantáneas de tarea cuando IActivityTaskManager ya no las expone (API 37).
 *
 * El sistema pasó getTaskSnapshot/takeTaskSnapshot a android.window.TaskSnapshotManager (que
 * delega en ITaskSnapshotManager, con firmas que cambian entre versiones). Se usa la clase del
 * propio framework por reflexión, eligiendo el método por nombre y tipos de parámetros; si ninguna
 * firma conocida existe se devuelve null (miniatura vacía), nunca una excepción.
 */
final class SnapshotManagerCompat {

    private static final String TAG = "SnapshotManagerCompat";
    private static final String CLASS = "android.window.TaskSnapshotManager";
    /** android.window.TaskSnapshotManager.RESOLUTION_*. */
    private static final int RESOLUTION_HIGH = 1;
    private static final int RESOLUTION_LOW = 2;

    private static boolean sLogged;

    private SnapshotManagerCompat() { }

    static TaskSnapshot getTaskSnapshot(int taskId, boolean isLowResolution) {
        return invoke("getTaskSnapshot",
                new Class<?>[] {int.class, int.class}, taskId,
                isLowResolution ? RESOLUTION_LOW : RESOLUTION_HIGH);
    }

    static TaskSnapshot takeTaskSnapshot(int taskId, boolean updateCache) {
        return invoke("takeTaskSnapshot",
                new Class<?>[] {int.class, boolean.class}, taskId, updateCache);
    }

    private static TaskSnapshot invoke(String name, Class<?>[] types, Object... args) {
        try {
            Class<?> cls = Class.forName(CLASS);
            Object instance = cls.getMethod("getInstance").invoke(null);
            Method m = cls.getMethod(name, types);
            return (TaskSnapshot) m.invoke(instance, args);
        } catch (InvocationTargetException e) {
            Log.w(TAG, name + " failed", e.getCause());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            if (!sLogged) {
                sLogged = true;
                Log.w(TAG, "No hay una firma conocida de " + CLASS + "." + name
                        + "; sin miniaturas de tareas: " + e);
            }
        }
        return null;
    }
}
