#!/usr/bin/env bash
# Genera el módulo Magisk/KernelSU de Nexus con el APK recién compilado.
#
# Uso: build-magisk-module.sh APK VERSION VERSION_CODE SALIDA.zip
#   Opcionales: AAPT2 (ruta de aapt2; por defecto el build-tools más reciente del SDK)
#               MODULE_DIR (plantilla; por defecto magisk-module)
#
# Estructura del zip:
#   module.prop                    version y versionCode iguales a los del APK
#   customize.sh, META-INF/        instalador
#   system/product/priv-app/Nexus/Nexus.apk
#   system/product/etc/permissions/privapp-permissions-nexus.xml
set -euo pipefail
# sort y comm tienen que ordenar igual.
export LC_ALL=C

apk="${1:?falta el APK}"
version="${2:?falta la versión}"
version_code="${3:?falta el versionCode}"
out_zip="${4:?falta el zip de salida}"

module_dir="${MODULE_DIR:-magisk-module}"
pkg="app.nexus.launcher"
allow_file="$module_dir/permissions/allow.txt"
normal_file="$module_dir/permissions/not-privileged.txt"
# Sin estos dos el módulo no sirve de nada: son los que dejan a Nexus pedir sus animaciones.
essential=(
  android.permission.START_TASKS_FROM_RECENTS
  android.permission.CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS
)

fail() {
  echo "::error title=Módulo Magisk::$1" >&2
  exit 1
}

[[ -f $apk ]] || fail "No existe el APK '$apk'."
[[ -f $allow_file ]] || fail "Falta $allow_file."
[[ -f $normal_file ]] || fail "Falta $normal_file."
[[ -f $module_dir/module.prop && -f $module_dir/customize.sh ]] || fail "Faltan archivos en $module_dir."
[[ $version =~ ^[0-9]+\.[0-9]+(\.[0-9]+)?$ ]] || fail "Versión no válida: '$version'."
[[ $version_code =~ ^[0-9]+$ ]] || fail "versionCode no válido: '$version_code'."
# Magisk guarda versionCode como int con signo.
(( version_code <= 2147483647 )) || fail "versionCode $version_code no cabe en un int."

if [[ -z "${AAPT2:-}" ]]; then
  sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  [[ -n $sdk && -d $sdk/build-tools ]] || fail "No encuentro el SDK de Android (build-tools)."
  AAPT2="$sdk/build-tools/$(ls "$sdk/build-tools" | sort -V | tail -n 1)/aapt2"
fi

# --- El APK que se mete en el módulo tiene que ser el de esta versión ---
badging=$("$AAPT2" dump badging "$apk" | sed -n '1p')
got_pkg=$(sed -n "s/.*package: name='\([^']*\)'.*/\1/p" <<<"$badging")
got_code=$(sed -n "s/.*versionCode='\([^']*\)'.*/\1/p" <<<"$badging")
got_name=$(sed -n "s/.*versionName='\([^']*\)'.*/\1/p" <<<"$badging")
[[ $got_pkg == "$pkg" ]] || fail "El APK es del paquete '$got_pkg' y el módulo es para '$pkg'."
[[ $got_name == "$version" ]] || fail "versionName del APK '$got_name' no coincide con '$version'."
[[ $got_code == "$version_code" ]] || fail "versionCode del APK '$got_code' no coincide con '$version_code'."

# --- Permisos: los que pide el APK y los que se conceden ---
requested=$(
  "$AAPT2" dump permissions "$apk" |
    sed -n "s/^uses-permission\(-sdk-23\)\{0,1\}: name='\([^']*\)'.*/\2/p" |
    sort -u
)
[[ -n $requested ]] || fail "aapt2 no listó ningún permiso del APK."

# Lee un archivo de permisos: sin comentarios ni líneas vacías, ordenado y sin repetidos.
read_list() {
  sed -e 's/[[:space:]]*#.*$//' -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' "$1" | grep -v '^$' | sort -u || true
}
allowed=$(read_list "$allow_file")
normal=$(read_list "$normal_file")
[[ -n $allowed ]] || fail "$allow_file no contiene ningún permiso."

for perm in "${essential[@]}"; do
  grep -qxF "$perm" <<<"$allowed" || fail "$perm es imprescindible y no está en $allow_file."
  grep -qxF "$perm" <<<"$requested" || fail "El APK no pide $perm, así que el módulo no podría concederlo."
done

bad=$(grep -vE '^[A-Za-z0-9_.]+$' <<<"$requested" || true)
[[ -z $bad ]] || fail "Nombres de permiso no válidos en el APK: $bad"
for f in "$allow_file" "$normal_file"; do
  bad=$(read_list "$f" | grep -vE '^[A-Za-z0-9_.]+$' || true)
  [[ -z $bad ]] || fail "Nombres de permiso no válidos en $f: $bad"
done
overlap=$(comm -12 <(printf '%s\n' "$allowed") <(printf '%s\n' "$normal") || true)
[[ -z $overlap ]] || fail "Están a la vez en allow.txt y not-privileged.txt: $(tr '\n' ' ' <<<"$overlap")"

granted=$(comm -12 <(printf '%s\n' "$requested") <(printf '%s\n' "$allowed"))
skipped=$(comm -12 <(printf '%s\n' "$requested") <(printf '%s\n' "$normal"))
denied=$(comm -23 <(comm -23 <(printf '%s\n' "$requested") <(printf '%s\n' "$allowed")) <(printf '%s\n' "$normal"))
unused=$(comm -13 <(printf '%s\n' "$requested") <(printf '%s\n' "$allowed"))
if [[ -n $unused ]]; then
  echo "::notice title=Módulo Magisk::En allow.txt pero el APK no los pide (se ignoran): $(tr '\n' ' ' <<<"$unused")" >&2
fi
# Lo denegado queda a la vista en cada ejecución. Casi todo será privilegiado a propósito; si
# aparece uno normal que ha traído una librería, va a not-privileged.txt para no tocarlo.
denied_android=$(grep -E '^android\.permission\.' <<<"$denied" || true)
if [[ -n $denied_android ]]; then
  echo "::notice title=Módulo Magisk::android.permission.* denegados: $(tr '\n' ' ' <<<"$denied_android")(si alguno es normal, añádelo a magisk-module/permissions/not-privileged.txt)" >&2
fi

# --- Montaje del módulo ---
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
root="$work/module"
mkdir -p "$root/system/product/priv-app/Nexus" "$root/system/product/etc/permissions"

cp -R "$module_dir/META-INF" "$root/META-INF"
cp "$module_dir/customize.sh" "$root/customize.sh"
sed -e "s/@VERSION@/$version/" -e "s/@VERSION_CODE@/$version_code/" \
  "$module_dir/module.prop" > "$root/module.prop"
cp "$apk" "$root/system/product/priv-app/Nexus/Nexus.apk"

xml="$root/system/product/etc/permissions/privapp-permissions-nexus.xml"
{
  echo '<?xml version="1.0" encoding="utf-8"?>'
  echo '<!-- Generado por .github/scripts/build-magisk-module.sh. No editar a mano. -->'
  echo '<permissions>'
  echo "    <privapp-permissions package=\"$pkg\">"
  while IFS= read -r perm; do echo "        <permission name=\"$perm\"/>"; done <<<"$granted"
  echo '        <!-- Lo que el APK pide y no se concede queda denegado de forma explícita. -->'
  if [[ -n $denied ]]; then
    while IFS= read -r perm; do echo "        <deny-permission name=\"$perm\"/>"; done <<<"$denied"
  fi
  echo '    </privapp-permissions>'
  echo '</permissions>'
} > "$xml"

# --- Comprobaciones del resultado antes de empaquetar ---
grep -qxF "version=$version" "$root/module.prop" || fail "module.prop no quedó con version=$version."
grep -qxF "versionCode=$version_code" "$root/module.prop" || fail "module.prop no quedó con versionCode=$version_code."
grep -q '@[A-Z_]*@' "$root/module.prop" && fail "module.prop todavía tiene marcadores sin sustituir."
grep -qxF 'id=nexus_launcher_systemless' "$root/module.prop" || fail "module.prop no tiene el id esperado."
cmp -s "$apk" "$root/system/product/priv-app/Nexus/Nexus.apk" || fail "El APK del módulo no es idéntico al compilado."
# Cada permiso pedido por el APK está concedido, denegado o es uno no privilegiado: ninguno sin resolver.
n_xml=$(grep -cE '<(deny-)?permission name=' "$xml" || true)
n_req=$(grep -c . <<<"$requested" || true)
n_skipped=$(grep -c . <<<"$skipped" || true)
(( n_xml == n_req - n_skipped )) || fail "El XML tiene $n_xml permisos; el APK pide $n_req y $n_skipped no son privilegiados."
while IFS= read -r perm; do
  grep -qF "name=\"$perm\"" "$xml" || grep -qxF "$perm" <<<"$normal" || fail "$perm no quedó resuelto."
done <<<"$requested"
if command -v python3 >/dev/null 2>&1; then
  python3 -I -c 'import sys, xml.dom.minidom as m; m.parse(sys.argv[1])' "$xml" || fail "El XML de permisos no es válido."
fi

# --- Zip ---
chmod 0755 "$root/customize.sh" "$root/META-INF/com/google/android/update-binary"
mkdir -p "$(dirname "$out_zip")"
out_abs="$(cd "$(dirname "$out_zip")" && pwd)/$(basename "$out_zip")"
rm -f "$out_abs"
(cd "$root" && zip -q -r -X "$out_abs" module.prop customize.sh META-INF system)
unzip -tq "$out_abs" >/dev/null || fail "El zip generado está corrupto."
for entry in module.prop customize.sh META-INF/com/google/android/update-binary \
  META-INF/com/google/android/updater-script system/product/priv-app/Nexus/Nexus.apk \
  system/product/etc/permissions/privapp-permissions-nexus.xml; do
  unzip -l "$out_abs" "$entry" >/dev/null 2>&1 || fail "Falta $entry dentro del zip."
done

echo "Módulo $version (versionCode $version_code): $(grep -c . <<<"$granted" || true) permisos concedidos, $(grep -c . <<<"$denied" || true) denegados, $n_skipped sin tocar." >&2
echo "→ $out_abs" >&2
