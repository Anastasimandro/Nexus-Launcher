# Nexus: animaciones de apertura y cierre (estado y límites)

Rama de trabajo: `flow-motion-dev` (no publica release; `16-dev` sí lo hace al hacer push).

## Qué controla quién

| Parte | Quién la controla | Estado |
|---|---|---|
| Cierre con el gesto de Inicio (la ventana sigue el dedo, se suelta y vuela al icono) | Quickstep real de este repo (`AbsSwipeUpHandler`, `SwipeUpAnimationLogic`, `RectFSpringAnim`, `FloatingIconView`) | **Ya existe y es interactivo de verdad**, pero solo actúa si Nexus es el proveedor de Recientes (módulo root). No demostrado en el Redmi. |
| Apertura desde el icono, con Quickstep activo | `QuickstepTransitionManager.getOpeningWindowAnimators` | Ahora usa el resorte del perfil de Nexus y su duración real |
| Apertura desde el icono, APK normal con Shizuku | `NexusAppLaunchTransition` (transición remota) + `ZoomSession` del icono | Ahora usa la geometría inversa del cierre |
| Apertura sin privilegios | Reveal del sistema (`makeClipRevealAnimation`) | Sin cambios; lo controla Android |

**Lo que Android no permite:** una transición remota de apertura no recibe el dedo; el progreso lo lleva el resorte, no el usuario. Solo el cierre con el gesto de Inicio es interactivo, y requiere ser el componente de Recientes.

## Cambios de esta fase

1. `IconWindowGeometry`: geometría icono ↔ ventana por escala uniforme + recorte. Es la misma relación que usa el cierre de Quickstep, así que abrir es el cierre al revés. Sustituye el recorte con escala fija 0,97, que no se parecía al cierre.
2. `NexusAppLaunchTransition` usa esa geometría. El radio de esquina se fija en espacio local (radio visible ÷ escala), porque el sistema lo escala junto con la superficie.
3. `SpringCurve` / `SpringInterpolator`: respuesta analítica del resorte del perfil para animaciones de duración fija. `NexusMotion.openCurve()` y `appLaunchDurationMs()` son la única fuente: duración del adaptador, retraso de la barra de estado, fundidos y animador del fondo salen de ahí.
4. `QuickstepTransitionManager`: se elimina la Bézier de 400 ms.

## Verificado

- 15 pruebas JVM (`SpringCurveTest`, `IconWindowGeometryTest`): el resorte coincide con una integración numérica independiente (posición y velocidad); el rebote coincide con `exp(-πζ/√(1-ζ²))`; la geometría empieza exactamente en el icono y acaba exactamente en la ventana, cubre siempre el rectángulo sin deformar, es continua y la escala no decrece, en 10 escenarios (vertical, horizontal, tableta, plegable, iconos de 48 a 400 px, ventana desplazada).
- Compilación de las tres variantes (`GithubDebug`, `PlayDebug`, `NightlyRelease`) en GitHub Actions.

## NO verificado

- Nada de esto se ha visto en un dispositivo. No hay comparación con grabaciones de ColorOS 17 ni de OxygenOS 16, así que **no se afirma fidelidad visual**.
- Las pruebas de la carpeta `tests/` no las ejecuta CI; se corrieron a mano en la JVM.
- El cierre interactivo no se ha medido en el Redmi (el módulo root aún no arranca con el overlay).

## Hallazgo: el rebote actual es imperceptible

| Amortiguación ζ | Rebote | Píxeles sobre 1000 px |
|---|---|---|
| 0,90 (apertura actual) | 0,15 % | 1,5 |
| 0,88 (cierre actual) | 0,30 % | 3 |
| 0,80 | 1,5 % | 15 |
| 0,75 | 2,8 % | 28 |
| 0,70 | 4,6 % | 46 |

Con ζ=0,90 el primer pico llega a los 552 ms, pero el resorte se da por asentado a los 445 ms: la apertura no rebota nada. Si quieres un rebote sutil visible, ζ entre 0,75 y 0,85. Es un ajuste para decidir viéndolo en el móvil.

## Sin cambios, a propósito

- **Mínimo de velocidad al soltar** (2000 px/s en Y): se dejó. Con rigidez 400 y 800 px de recorrido, el resorte acelera a más de 3000 px/s en 10 ms, así que el mínimo no cambia nada perceptible.
- **Cancelar el gesto** (volver a la app): sigue siendo el `ValueAnimator` de AOSP con curva de frenada, partiendo de la posición actual. La posición es continua; la velocidad solo se aproxima. Sustituirlo por un resorte toca los flujos de Recientes y no se hizo sin poder probarlo.

## Protocolo para comparar en el móvil

1. Abrir y cerrar la misma app 10 veces seguidas, a distintas velocidades.
2. Abrir un icono del dock, uno de la esquina superior y uno dentro de una carpeta.
3. Girar a horizontal y repetir.
4. Con el módulo root activo: arrastrar la ventana hacia arriba, parar, invertir, soltar rápido y soltar despacio.
5. Grabar la pantalla y compararla fotograma a fotograma con una grabación del sistema de referencia.
