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

package com.android.wm.shell.shared.desktopmode;

import android.util.Log;

import java.util.function.BooleanSupplier;

/**
 * Lectura tolerante de las constantes de {@code android.window.DesktopModeFlags} y
 * {@code android.window.DesktopExperienceFlags}.
 *
 * <p>Android 17 (API 37) conserva ambas clases pero ha quitado constantes que esta versión de
 * Quickstep usa (la auditoría del DEX contra un Android 17 real lista 14). Leer una constante que
 * no existe lanza {@link NoSuchFieldError}, un {@link Error} que ningún {@code catch (Exception)}
 * recoge; en {@code TaskAnimationManager.maybeStartHomeAction}, que ejecuta el gesto de Inicio,
 * tumbaba el servicio de Quickstep. Aquí una constante ausente cuenta como desactivada, igual que
 * el resto de indicadores de escritorio de este fork.
 *
 * <p>También vale para las clases de indicadores generadas por aconfig que Android 17 no incluye
 * (p. ej. {@code android.companion.virtualdevice.flags.Flags}, que tumbaba el servicio de Quickstep
 * con NoClassDefFoundError en {@code KeyButtonRipple}): la clase ausente cuenta como indicador apagado.
 *
 * <p>Uso: {@code DesktopFlagsCompat.isTrue(() -> DesktopModeFlags.X.isTrue())}. La lectura va
 * dentro de la lambda para que el error de enlace se produzca (y se recoja) aquí.
 */
public final class DesktopFlagsCompat {
    private static final String TAG = "DesktopFlagsCompat";

    private DesktopFlagsCompat() {}

    public static boolean isTrue(BooleanSupplier flag) {
        try {
            return flag.getAsBoolean();
        } catch (LinkageError | RuntimeException e) {
            // Normalmente una constante que esta versión de Android ya no tiene.
            Log.w(TAG, "Indicador de escritorio no disponible, se toma como desactivado: " + e);
            return false;
        }
    }
}
