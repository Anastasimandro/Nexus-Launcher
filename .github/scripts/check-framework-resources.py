#!/usr/bin/env python3
"""Comprueba que los recursos del framework que Nexus referencia en sus XML existen en el sistema.

Uso: check-framework-resources.py VOLCADO_AAPT2 RAIZ [RAIZ...]
  VOLCADO_AAPT2: salida de `aapt2 dump resources framework-res.apk` del dispositivo
  RAIZ: carpetas con res/ del proyecto

Una referencia `@*android:dimen/x` a un recurso que el sistema ya no tiene se compila bien (se
resuelve contra el android.jar del SDK) y falla en ejecución con Resources$NotFoundException.
"""
import re
import sys
from pathlib import Path

dump = Path(sys.argv[1]).read_text(errors="replace")
have = set(re.findall(r"resource 0x[0-9a-f]+ (?:android:)?(\w+)/([\w.]+)", dump))
if not have:
    print("!! el volcado no contiene recursos: no se puede comprobar")
    sys.exit(2)

ref = re.compile(r"[@?]\*?android:(?:(\w+)/)?([\w.]+)")
missing, checked = {}, 0
for root in sys.argv[2:]:
    for xml in Path(root).rglob("*.xml"):
        if "/res/" not in str(xml).replace("\\", "/") and not str(xml).startswith("res/"):
            continue
        text = xml.read_text(errors="replace")
        for m in ref.finditer(text):
            typ, name = m.group(1), m.group(2)
            if typ is None:  # ?android:colorAccent -> attr
                typ = "attr"
            if typ in ("style", "attr", "color", "drawable", "dimen", "bool", "integer", "string", "layout", "id", "interpolator", "anim", "array", "fraction", "plurals") :
                checked += 1
                if (typ, name) not in have:
                    line = text.count("\n", 0, m.start()) + 1
                    missing.setdefault((typ, name), []).append(f"{xml}:{line}")
print(f"== RECURSOS DEL FRAMEWORK: {checked} referencias, {len(have)} recursos en el sistema")
for (typ, name), where in sorted(missing.items()):
    print(f"  AUSENTE android:{typ}/{name}  <- {where[0]}" + (f" (+{len(where)-1})" if len(where) > 1 else ""))
if not missing:
    print("  todas existen")
