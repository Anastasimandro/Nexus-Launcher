#!/usr/bin/env python3
"""Comprueba que los recursos del framework que Nexus referencia en sus XML existen en el sistema.

Uso: check-framework-resources.py VOLCADO_AAPT2 VOLCADO_BUILD RAIZ [RAIZ...]
  VOLCADO_AAPT2: salida de `aapt2 dump resources framework-res.apk` del dispositivo
  VOLCADO_BUILD: salida de `aapt2 dump resources android.jar` del SDK con el que se compila (o "-")
  RAIZ: carpetas con res/ del proyecto

Dos comprobaciones: (1) el nombre existe en el sistema; (2) el ID con el que aapt2 enlaza la
referencia al compilar (el del android.jar) es el mismo que tiene en el sistema. Los recursos
privados (`@*android:`) no tienen ID estable entre compilaciones del framework: si difieren, la
referencia queda apuntando a otro recurso o a ninguno (Resources$NotFoundException).

Una referencia `@*android:dimen/x` a un recurso que el sistema ya no tiene se compila bien (se
resuelve contra el android.jar del SDK) y falla en ejecución con Resources$NotFoundException.
"""
import re
import sys
from pathlib import Path

def ids(path):
    if path == "-":
        return {}
    text = Path(path).read_text(errors="replace")
    return {(t, n): i for i, t, n in re.findall(r"resource 0x([0-9a-f]+) (?:android:)?(\w+)/([\w.]+)", text)}

device = ids(sys.argv[1])
built = ids(sys.argv[2])
have = set(device)
dump = ""
if not have:
    print("!! el volcado no contiene recursos: no se puede comprobar")
    sys.exit(2)
if built:
    print(f"   android.jar de compilación: {len(built)} recursos")

ref = re.compile(r"[@?]\*?android:(?:(\w+)/)?([\w.]+)")
missing, checked, shifted = {}, 0, {}
for root in sys.argv[3:]:
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
                line = text.count("\n", 0, m.start()) + 1
                if (typ, name) not in have:
                    missing.setdefault((typ, name), []).append(f"{xml}:{line}")
                elif built and (typ, name) in built and built[(typ, name)] != device[(typ, name)]:
                    shifted.setdefault((typ, name), []).append(f"{xml}:{line}")
print(f"== RECURSOS DEL FRAMEWORK: {checked} referencias, {len(have)} recursos en el sistema")
for (typ, name), where in sorted(missing.items()):
    print(f"  AUSENTE android:{typ}/{name}  <- {where[0]}" + (f" (+{len(where)-1})" if len(where) > 1 else ""))
if not missing:
    print("  todas existen")
if built:
    private = sum(len(v) for v in shifted.values())
    print(f"== IDs: {len(shifted)} recursos distintos ({private} referencias) entre el android.jar de compilación y el sistema")
    for (typ, name), where in sorted(shifted.items()):
        print(f"  ID DISTINTO android:{typ}/{name} compilado=0x{built[(typ, name)]} sistema=0x{device[(typ, name)]}  <- {where[0]}" + (f" (+{len(where)-1})" if len(where) > 1 else ""))
    if not shifted:
        print("  todos los IDs coinciden")
else:
    print("== IDs: no se pudo comparar (no hay android.jar de compilación)")
