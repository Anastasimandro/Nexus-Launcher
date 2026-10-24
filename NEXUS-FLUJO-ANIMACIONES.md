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

## Interrupciones (abrir, cerrar a medias, abrir otra)

**Error encontrado en el código previo.** `mergeAnimation` daba por absorbida cualquier transición que llegaba durante la apertura, descartaba su transacción y nunca llamaba al callback de fin de la transición original. Según el contrato del Shell (`Transitions`/`RemoteTransitionHandler`), una transición remota que no termina bloquea las siguientes, que quedan encoladas: eso se vería como apps que se abren sin animación o con retraso tras interrumpir. No está medido en el móvil; es el diagnóstico a partir del código y del contrato.

**Cómo funciona ahora.** Una `TransitionSession` por transición, con una o varias capas (`WindowLayer`, cada una con su resorte):

| Escenario | Decisión (`MergePlanner`) | Qué se ve |
|---|---|---|
| Abro A y voy a Inicio antes de que termine | `Reverse` | La ventana da la vuelta hacia su icono con el resorte de cierre, conservando la velocidad que llevaba; el icono se repliega a la vez. |
| Abro A, cierro y vuelvo a abrir A | `Reopen` | La misma capa cambia de rumbo sin reiniciarse. |
| Abro A, cierro, abro B mientras A aún vuelve | `AdoptOpening` | A termina de volver mientras B nace de su icono; las dos capas conviven y la transición original termina cuando se asientan ambas. |
| Cualquier otra combinación | `Reject` | No se hace nada y el sistema la encola, que es lo que manda el contrato. |

Garantías: la transición original termina exactamente una vez (`SessionCore`); hay un temporizador de seguridad de 2 s (× escala de animación del sistema) que fuerza el final; cualquier excepción termina la sesión y deja las superficies en estado neutro. Los zooms de icono pueden coexistir (máximo 3) y el recorte de los contenedores padre se cuenta por referencias para que un zoom no quite el recorte a otro. Un `onResume` tardío del launcher ya no repliega el zoom de la app que se está abriendo.

**Supuestos sin comprobar en dispositivo:** que el `TransitionInfo` entrante traiga el cambio del launcher (`HOME_OPENING`) al volver a Inicio, que el `leash` de la capa coincida con el de la ventana, y que `baseIntent`/`topActivity` den el paquete para emparejar el lanzamiento pendiente. Si alguno falla, el resultado es `Reject`: sin animación nueva, pero sin bloqueo.

**Límite de Android:** con el APK sin módulo root, si la app ya terminó de abrirse y se cierra, la animación de cierre la pone el sistema; Nexus solo controla el regreso del icono.

## Auditoría contra el brief (punto por punto)

| Requisito del brief | Estado | Nota |
|---|---|---|
| Cierre interactivo real (ventana sigue el dedo, hacia el icono) | Existe en Quickstep del repo | Solo con el módulo root como proveedor de Recientes; no demostrado en el Redmi |
| Reversible sin reinicios | Quickstep (`RectFSpringAnim`, progreso ligado al dedo) | No se ha tocado; sin medir en dispositivo |
| Soltar para ir a Inicio: continuidad de velocidad | Sí: resorte con la velocidad del dedo | Código de Quickstep existente |
| **Cancelar y volver a la app: continuidad de velocidad** | **Corregido en esta fase** | Antes: curva de frenada que arrancaba a su propia velocidad. Ahora: resorte de cierre ajustado a la duración de Quickstep y con la velocidad real al soltar como velocidad inicial (solo `LAST_TASK`) |
| Apertura desde el icono, inversa del cierre | Sí | `IconWindowGeometry` + resorte; con APK normal la controla Nexus vía Shizuku; sin privilegios, reveal del sistema |
| Sin curvas Bézier arbitrarias | Sí en apertura y cancelación | Quedan curvas de AOSP en Recientes/cuadrícula y en la ruta de nueva tarea (no se tocan) |
| Interrupciones (abrir, cerrar a medias, abrir otra) | Implementado | Ver sección anterior; sin probar en el móvil |
| Ciclo de vida: sin superficies huérfanas ni callbacks obsoletos | Implementado | La transición original termina una vez; temporizador de seguridad; ref-counting del recorte |
| Constantes no arbitrarias | Parcial | Los resortes salen del perfil; quedan constantes con nombre y comentario (temporizador 2 s, desvanecido al volver `REVERSE_FADE_UNTIL`=0,25, máx. 3 zooms). Son decisiones de diseño sin referencia oficial: se ajustan viéndolo en el móvil |
| Fidelidad visual a ColorOS 17 / OxygenOS 16 | **No afirmada** | Sin dispositivo ni grabaciones de referencia |

## Verificado

- 37 pruebas JVM (`SpringCurveTest`, `IconWindowGeometryTest`, `LaunchSessionCoreTest`; esta última cubre las decisiones de `MergePlanner` y que la transición original termina una sola vez): el resorte coincide con una integración numérica independiente (posición y velocidad); el rebote coincide con `exp(-πζ/√(1-ζ²))`; la geometría empieza exactamente en el icono y acaba exactamente en la ventana, cubre siempre el rectángulo sin deformar, es continua y la escala no decrece, en 10 escenarios (vertical, horizontal, tableta, plegable, iconos de 48 a 400 px, ventana desplazada).
- Compilación y `spotlessCheck` en GitHub Actions sobre el código de interrupciones (commit `3844216`: estilo y las tres variantes en verde).

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
- **Cancelar a Recientes o a otra tarea** (`RECENTS`, `NEW_TASK`): siguen con las curvas de AOSP. Solo se cambió volver a la app (`LAST_TASK`). El `ValueAnimator` y su duración son los mismos; únicamente cambia la curva.

## Protocolo de interrupciones en el móvil

Con `adb logcat -s NexusAppLaunch NexusLaunchAnimator` (las líneas "Interrupción: …" dicen qué decisión se tomó):

1. Abrir A y pulsar Inicio a los ~150 ms. Esperado: A vuelve a su icono sin salto.
2. Abrir A, Inicio, y tocar B enseguida. Esperado: A termina de volver mientras B se abre.
3. Tocar A, Inicio, A otra vez en rápida sucesión.
4. Repetir 20 veces seguidas y comprobar que la siguiente apertura normal sigue animándose (si no, la cola del Shell se bloqueó).
5. Mandar el logcat si algo salta, se queda a medias o aparece una superficie huérfana.

## Protocolo para comparar en el móvil

1. Abrir y cerrar la misma app 10 veces seguidas, a distintas velocidades.
2. Abrir un icono del dock, uno de la esquina superior y uno dentro de una carpeta.
3. Girar a horizontal y repetir.
4. Con el módulo root activo: arrastrar la ventana hacia arriba, parar, invertir, soltar rápido y soltar despacio.
5. Grabar la pantalla y compararla fotograma a fotograma con una grabación del sistema de referencia.
