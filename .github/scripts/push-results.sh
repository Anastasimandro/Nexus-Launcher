#!/usr/bin/env bash
# Sube una carpeta de resultados a la rama quickstep-validation-results, en run-<id>/<nombre>/.
# Los artefactos de Actions no se pueden bajar desde fuera de GitHub; una rama sí.
#
# Uso: push-results.sh CARPETA NOMBRE        (necesita GITHUB_TOKEN, GITHUB_REPOSITORY, GITHUB_RUN_ID)
set -uo pipefail

src="${1:?falta la carpeta de resultados}"
name="${2:?falta el nombre}"
branch="quickstep-validation-results"
work="$(mktemp -d)"
url="https://x-access-token:${GITHUB_TOKEN}@github.com/${GITHUB_REPOSITORY}.git"

[[ -d $src ]] || { echo "No existe $src"; exit 0; }
cd "$work" || exit 1
git init -q .
git config user.name "github-actions"
git config user.email "github-actions@users.noreply.github.com"
git remote add origin "$url"

for attempt in 1 2 3 4 5; do
  if git fetch -q --depth=1 origin "$branch" 2>/dev/null; then
    git checkout -q -B results FETCH_HEAD
  else
    git checkout -q --orphan results
    git rm -rq --cached . 2>/dev/null || true
  fi
  dest="run-${GITHUB_RUN_ID}/${name}"
  rm -rf "$dest"
  mkdir -p "$dest"
  cp -R "$src"/. "$dest"/
  git add -A
  git commit -q -m "Resultados ${name} (run ${GITHUB_RUN_ID})" || true
  if err="$(git push -q origin "HEAD:${branch}" 2>&1)"; then
    echo "Resultados subidos a ${branch}:${dest}"
    exit 0
  fi
  last_err="$err"
  echo "Intento $attempt: no se pudo subir: $err"
  sleep $((attempt * 3))
done

# Último recurso: que al menos el resumen quede en una anotación legible desde la API.
msg="No se pudieron subir los resultados de ${name}: ${last_err:-?}"
echo "::warning title=push-results::$(printf '%s' "$msg" | head -c 2000 | sed ':a;N;$!ba;s/\n/%0A/g')"
if [[ -f $src/summary.md ]]; then
  body="$(head -c 55000 "$src/summary.md" | sed ':a;N;$!ba;s/%/%25/g;s/\r/%0D/g;s/\n/%0A/g')"
  echo "::notice title=resumen-${name}::${body}"
elif [[ -f $src/summary.txt ]]; then
  body="$(head -c 55000 "$src/summary.txt" | sed ':a;N;$!ba;s/%/%25/g;s/\r/%0D/g;s/\n/%0A/g')"
  echo "::notice title=resumen-${name}::${body}"
fi
exit 0
