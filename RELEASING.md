# Releases de Nexus

Cada push a la rama `16-dev` publica una release nueva con una versión numérica.
No hay que hacer nada más: GitHub Actions compila, firma, comprueba y publica el APK.
(Los cambios que solo tocan archivos `.md` no publican nada.)

## Versiones

La secuencia es `0.1` → `0.1.1` → `0.1.2` → `0.1.3` …

- La siguiente versión sale sola: la etiqueta `vX.Y[.Z]` más alta + 1 en el último número.
- La primera release es `0.1` (equivale a `0.1.0`).
- Para cambiar de línea (por ejemplo a `0.2` o `1.0`): pestaña **Actions** → **Nexus release** → **Run workflow** → escribe la versión. Después siguen `0.2.1`, `0.2.2`…
- Cada número va de 0 a 99. La versión nueva debe ser mayor que la última publicada.
- El APK se llama `Nexus-0.1.2.apk` y la etiqueta es `v0.1.2`.
- Las compilaciones locales o de CI que no son release llevan la versión `dev-<commit>` y nunca se publican.

El `versionCode` (el número que Android compara) se calcula a partir de la versión:

```
versionCode = 1601000000 + mayor*10000 + menor*100 + parche
0.1 = 1601000100   0.1.1 = 1601000101   0.2 = 1601000200   1.0 = 1601010000
```

Se calcula igual en `build.gradle` y en `.github/scripts/next-version.sh`, y el workflow
comprueba que el APK final lleva exactamente el valor esperado.

## Actualizar sin desinstalar

Android solo deja instalar un APK encima de otro si cumple las tres condiciones:

1. Mismo paquete: `app.nexus.launcher`.
2. Misma firma: el mismo certificado en todas las versiones.
3. `versionCode` mayor que el instalado.

Antes de publicar, el workflow lo verifica sobre el APK real (`.github/scripts/verify-apk.sh`) y **no publica nada si falla algo**.

## Firma

La clave se guarda en los secrets del repositorio (Settings → Secrets and variables → Actions):

| Secret | Contenido |
|---|---|
| `KEYSTORE` | el archivo `.jks` en base64 (`base64 -w0 key.jks`) |
| `KEYSTORE_PASSWORD` | contraseña del keystore |
| `KEY_ALIAS` | alias de la clave |
| `KEY_PASSWORD` | contraseña de la clave |

La huella SHA-256 del certificado aceptado está en `.github/nexus-signing-sha256.txt`; cada release la muestra en sus notas.

**Guarda una copia del `.jks` y de las contraseñas fuera de GitHub** (los secrets no se pueden volver a leer). Si se pierde la clave hay que crear otra, actualizar los secrets y esa huella, y entonces todo el mundo tendrá que desinstalar una sola vez.

## Si el workflow falla

| Mensaje | Qué pasa |
|---|---|
| Falta la clave de firma | Falta alguno de los cuatro secrets de arriba. |
| El APK está firmado con otra clave | Los secrets apuntan a una clave distinta de la de las versiones anteriores. Corrígelos; no cambies la huella salvo que cambies de clave a propósito. |
| La versión X no es mayor que la última publicada | La versión manual indicada es igual o menor que una ya existente. |
| versionCode / versionName no coinciden | `build.gradle` y `next-version.sh` calculan distinto: hay que revisarlos juntos. |

Si una release falla a medias, haz un nuevo push (o **Run workflow**): se publicará la siguiente versión libre.
Las releases antiguas `build-N` siguen en GitHub y se pueden borrar a mano; ya no se crean.
