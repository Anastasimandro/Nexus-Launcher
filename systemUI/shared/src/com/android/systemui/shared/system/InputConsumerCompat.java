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

import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.IWindowManager;
import android.view.InputChannel;

import androidx.annotation.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Puente para {@code IWindowManager.createInputConsumer} / {@code destroyInputConsumer}.
 *
 * <p>Hasta Android 16 la firma es {@code void createInputConsumer(IBinder, String, int,
 * InputChannel)}: el canal se pasa vacío y el sistema lo rellena. En Android 17 esa forma ya no
 * existe y la llamada directa lanza {@link NoSuchMethodError} (esa excepción tumbaba el servicio
 * de Quickstep al desbloquear el usuario). La forma nueva devuelve el canal. Aquí se prueba la
 * forma antigua (el camino de siempre, sin coste extra) y, solo si no existe, se busca la nueva
 * por reflexión con los tipos exactos; si no hay ninguna conocida se devuelve {@code null} en
 * lugar de fallar, y {@link #describeSignatures()} deja a la vista qué hay en ese sistema.
 */
public final class InputConsumerCompat {

    private static final String TAG = "InputConsumerCompat";

    private InputConsumerCompat() {}

    /**
     * Crea el input consumer y devuelve su canal, o {@code null} si este Android no tiene una
     * forma conocida de {@code createInputConsumer}.
     */
    @Nullable
    public static InputChannel create(IWindowManager windowManager, IBinder token, String name,
            int displayId) throws RemoteException {
        try {
            InputChannel channel = new InputChannel();
            windowManager.createInputConsumer(token, name, displayId, channel);
            return channel;
        } catch (NoSuchMethodError e) {
            Log.i(TAG, "createInputConsumer sin canal de salida: se busca la forma que lo devuelve");
        }

        Method method = findReturningForm();
        if (method == null) {
            Log.e(TAG, "createInputConsumer no tiene una forma conocida. Firmas: "
                    + describeSignatures());
            return null;
        }
        try {
            return (InputChannel) method.invoke(windowManager, token, name, displayId);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RemoteException) {
                throw (RemoteException) cause;
            }
            Log.e(TAG, "createInputConsumer falló", cause != null ? cause : e);
        } catch (IllegalAccessException | RuntimeException e) {
            Log.e(TAG, "No se pudo llamar a createInputConsumer", e);
        }
        return null;
    }

    /** Destruye el input consumer. Devuelve {@code false} si no se pudo hacer. */
    public static boolean destroy(IWindowManager windowManager, IBinder token, int displayId)
            throws RemoteException {
        try {
            windowManager.destroyInputConsumer(token, displayId);
            return true;
        } catch (NoSuchMethodError e) {
            Log.e(TAG, "destroyInputConsumer no existe en este Android. Firmas: "
                    + describeSignatures());
            return false;
        }
    }

    /**
     * {@code true} si este Android tiene alguna forma de {@code createInputConsumer} que se sabe
     * llamar (la antigua con canal de salida o la nueva que lo devuelve), y {@code
     * destroyInputConsumer(IBinder, int)}. No crea nada.
     */
    public static boolean isSupported() {
        try {
            boolean hasCreate = hasOutParamForm() || findReturningForm() != null;
            // getMethod lanza NoSuchMethodException si no existe.
            IWindowManager.class.getMethod("destroyInputConsumer", IBinder.class, int.class);
            return hasCreate;
        } catch (NoSuchMethodException | RuntimeException | LinkageError e) {
            return false;
        }
    }

    /** Firmas de {@code *InputConsumer} que tiene este Android, para el informe de errores. */
    public static String describeSignatures() {
        List<String> found = new ArrayList<>();
        try {
            for (Method method : IWindowManager.class.getMethods()) {
                if (method.getName().endsWith("InputConsumer")) {
                    found.add(method.toGenericString());
                }
            }
        } catch (RuntimeException | LinkageError e) {
            return "no se pudo leer IWindowManager (" + e + ")";
        }
        return found.isEmpty() ? "ninguna" : String.join("; ", found);
    }

    private static boolean hasOutParamForm() {
        try {
            IWindowManager.class.getMethod("createInputConsumer",
                    IBinder.class, String.class, int.class, InputChannel.class);
            return true;
        } catch (NoSuchMethodException | RuntimeException | LinkageError e) {
            return false;
        }
    }

    /** {@code InputChannel createInputConsumer(IBinder, String, int)}, con esos tipos exactos. */
    @Nullable
    private static Method findReturningForm() {
        try {
            Method method = IWindowManager.class.getMethod("createInputConsumer",
                    IBinder.class, String.class, int.class);
            return method.getReturnType() == InputChannel.class ? method : null;
        } catch (NoSuchMethodException | RuntimeException | LinkageError e) {
            return null;
        }
    }
}
