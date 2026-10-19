#!/usr/bin/env python3
"""Auditoría estática de compatibilidad entre un APK y los binarios de un Android real.

Lee los DEX de la app y los de los archivos del sistema (framework, apex, SystemUI...) y comprueba:

  --link   Cada clase, método y campo del framework que usa la app existe en el sistema
           (buscando también en superclases e interfaces). Lo que falta es lo que acabaría en
           NoSuchMethodError / NoSuchFieldError / NoClassDefFoundError.
  --aidl   Para cada interfaz AIDL (clase ...$Stub) que lleva la app y que también existe en el
           sistema: mismos códigos de transacción (TRANSACTION_*) y mismas firmas. Una diferencia
           aquí no falla al cargar la clase: hace que una llamada Binder vaya a otro método.
  --dump   Imprime los campos y métodos de una clase del sistema (p. ej. las constantes de
           android.window.DesktopModeFlags).

No usa herramientas externas. Solo lee; no ejecuta nada de lo que analiza.

Uso:
  dex_audit.py --app Nexus.apk --device DIR_O_ARCHIVOS... --link --aidl \\
               --dump Landroid/view/IWindowManager; ...
"""

import argparse
import collections
import os
import struct
import sys
import zipfile

OBJECT = "Ljava/lang/Object;"
NO_INDEX = 0xFFFFFFFF


def read_uleb(buf, off):
    result = 0
    shift = 0
    while True:
        byte = buf[off]
        off += 1
        result |= (byte & 0x7F) << shift
        if not byte & 0x80:
            return result, off
        shift += 7


def _build_width_table():
    w = [1] * 256
    two = [0x02, 0x05, 0x08, 0x13, 0x15, 0x16, 0x19, 0x1A, 0x1C, 0x1F, 0x20, 0x22, 0x23, 0x29, 0xFE, 0xFF]
    three = [0x03, 0x06, 0x09, 0x14, 0x17, 0x1B, 0x24, 0x25, 0x26, 0x2A, 0x2B, 0x2C, 0xFC, 0xFD]
    for o in two:
        w[o] = 2
    for o in three:
        w[o] = 3
    w[0x18] = 5
    for o in list(range(0x2D, 0x3E)) + list(range(0x44, 0x6E)) + list(range(0x90, 0xB0)) + list(range(0xD0, 0xE3)):
        w[o] = 2
    for o in list(range(0x6E, 0x73)) + list(range(0x74, 0x79)):
        w[o] = 3
    w[0xFA] = w[0xFB] = 4
    return w


_WIDTH = _build_width_table()
SCAN_STATS = [0, 0]  # métodos recorridos, métodos cuyo recorrido no acabó justo al final


class Dex:
    """Un archivo .dex. Todo se lee bajo demanda: los de framework tienen cientos de miles de ids."""

    def __init__(self, data, label):
        if data[:4] != b"dex\n":
            raise ValueError("%s no es un DEX" % label)
        self.data = data
        self.label = label
        (self.n_str, self.off_str, self.n_type, self.off_type, self.n_proto, self.off_proto,
         self.n_field, self.off_field, self.n_method, self.off_method, self.n_class,
         self.off_class) = struct.unpack_from("<12I", data, 0x38)
        self._str = {}
        self._type = {}
        self._proto = {}

    def string(self, idx):
        cached = self._str.get(idx)
        if cached is not None:
            return cached
        (off,) = struct.unpack_from("<I", self.data, self.off_str + 4 * idx)
        _, off = read_uleb(self.data, off)
        end = self.data.index(b"\0", off)
        text = self.data[off:end].decode("utf-8", "replace")
        self._str[idx] = text
        return text

    def type(self, idx):
        cached = self._type.get(idx)
        if cached is not None:
            return cached
        (sidx,) = struct.unpack_from("<I", self.data, self.off_type + 4 * idx)
        text = self.string(sidx)
        self._type[idx] = text
        return text

    def type_list(self, off):
        if not off:
            return ()
        (size,) = struct.unpack_from("<I", self.data, off)
        return tuple(self.type(t) for t in struct.unpack_from("<%dH" % size, self.data, off + 4))

    def proto(self, idx):
        cached = self._proto.get(idx)
        if cached is not None:
            return cached
        _, ret, params_off = struct.unpack_from("<3I", self.data, self.off_proto + 12 * idx)
        text = "(" + "".join(self.type_list(params_off)) + ")" + self.type(ret)
        self._proto[idx] = text
        return text

    def field_ref(self, idx):
        cls, typ, name = struct.unpack_from("<HHI", self.data, self.off_field + 8 * idx)
        return self.type(cls), self.string(name), self.type(typ)

    def method_ref(self, idx):
        cls, proto, name = struct.unpack_from("<HHI", self.data, self.off_method + 8 * idx)
        return self.type(cls), self.string(name), self.proto(proto)

    def class_defs(self):
        for i in range(self.n_class):
            cidx, access, sup, ifaces, _, _, cdata, svals = struct.unpack_from(
                "<8I", self.data, self.off_class + 32 * i)
            yield {
                "name": self.type(cidx),
                "access": access,
                "super": self.type(sup) if sup != NO_INDEX else None,
                "ifaces": self.type_list(ifaces),
                "class_data": cdata,
                "static_values": svals,
                "dex": self,
            }

    def members(self, cdef):
        """(campos, métodos, constantes int estáticas) de una clase."""
        fields = set()
        methods = set()
        consts = {}
        off = cdef["class_data"]
        if not off:
            return fields, methods, consts
        buf = self.data
        n_sf, off = read_uleb(buf, off)
        n_if, off = read_uleb(buf, off)
        n_dm, off = read_uleb(buf, off)
        n_vm, off = read_uleb(buf, off)
        static_names = []
        for count, is_static in ((n_sf, True), (n_if, False)):
            idx = 0
            for _ in range(count):
                diff, off = read_uleb(buf, off)
                _, off = read_uleb(buf, off)
                idx += diff
                _, name, typ = self.field_ref(idx)
                fields.add((name, typ))
                if is_static:
                    static_names.append(name)
        for count in (n_dm, n_vm):
            idx = 0
            for _ in range(count):
                diff, off = read_uleb(buf, off)
                _, off = read_uleb(buf, off)
                _, off = read_uleb(buf, off)
                idx += diff
                _, name, sig = self.method_ref(idx)
                methods.add((name, sig))
        if cdef["static_values"] and static_names:
            for name, value in zip(static_names, self._static_values(cdef["static_values"])):
                if isinstance(value, int):
                    consts[name] = value
        return fields, methods, consts

    def code_refs(self):
        """(clase que referencia, tipo, referencia) leyendo el bytecode de cada método.

        tipo: "field", "method" o "class". Se usa para saber QUÉ código de la app toca un símbolo que
        el sistema ya no tiene (y no solo que la app lo menciona).
        """
        buf = self.data
        for cdef in self.class_defs():
            off = cdef["class_data"]
            if not off:
                continue
            n_sf, off = read_uleb(buf, off)
            n_if, off = read_uleb(buf, off)
            n_dm, off = read_uleb(buf, off)
            n_vm, off = read_uleb(buf, off)
            for count in (n_sf, n_if):
                for _ in range(count):
                    _, off = read_uleb(buf, off)
                    _, off = read_uleb(buf, off)
            for count in (n_dm, n_vm):
                idx = 0
                for _ in range(count):
                    diff, off = read_uleb(buf, off)
                    _, off = read_uleb(buf, off)
                    code_off, off = read_uleb(buf, off)
                    idx += diff
                    if not code_off:
                        continue
                    mname = self.method_ref(idx)[1]
                    for kind, ref in self._scan_code(code_off):
                        yield cdef["name"], mname, kind, ref

    def _scan_code(self, code_off):
        buf = self.data
        (insns_size,) = struct.unpack_from("<I", buf, code_off + 12)
        base = code_off + 16
        units = struct.unpack_from("<%dH" % insns_size, buf, base)
        i = 0
        while i < insns_size:
            u = units[i]
            op = u & 0xFF
            if op == 0x00 and u != 0:  # cargas útiles de switch / arrays
                if u == 0x0100:
                    i += 4 + units[i + 1] * 2
                elif u == 0x0200:
                    i += 2 + units[i + 1] * 4
                else:
                    width = units[i + 1]
                    size = units[i + 2] | (units[i + 3] << 16)
                    i += 4 + (size * width + 1) // 2
                continue
            width = _WIDTH[op]
            if i + 1 < insns_size:
                ref = units[i + 1]
                if 0x52 <= op <= 0x6D:
                    yield "field", self.field_ref(ref)
                elif op in (0x6E, 0x6F, 0x70, 0x71, 0x72, 0x74, 0x75, 0x76, 0x77, 0x78):
                    yield "method", self.method_ref(ref)
                elif op in (0x1C, 0x1F, 0x20, 0x22, 0x23, 0x24, 0x25):
                    yield "class", self.type(ref)
            i += width
        SCAN_STATS[0] += 1
        if i != insns_size:
            SCAN_STATS[1] += 1

    def _static_values(self, off):
        size, off = read_uleb(self.data, off)
        values = []
        for _ in range(size):
            value, off = self._encoded_value(off)
            values.append(value)
        return values

    def _encoded_value(self, off):
        """Lee un encoded_value y devuelve (valor o None, nuevo offset)."""
        header = self.data[off]
        off += 1
        vtype = header & 0x1F
        arg = header >> 5
        if vtype in (0x00, 0x02, 0x04, 0x06):  # byte, short, int, long: con signo
            raw = self.data[off:off + arg + 1]
            return int.from_bytes(raw, "little", signed=True), off + arg + 1
        if vtype == 0x03:  # char: sin signo
            raw = self.data[off:off + arg + 1]
            return int.from_bytes(raw, "little"), off + arg + 1
        if vtype in (0x10, 0x11, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B):
            return None, off + arg + 1
        if vtype == 0x1C:  # array
            size, off = read_uleb(self.data, off)
            for _ in range(size):
                _, off = self._encoded_value(off)
            return None, off
        if vtype == 0x1D:  # annotation
            _, off = read_uleb(self.data, off)
            size, off = read_uleb(self.data, off)
            for _ in range(size):
                _, off = read_uleb(self.data, off)
                _, off = self._encoded_value(off)
            return None, off
        if vtype in (0x1E, 0x1F):  # null, boolean
            return None, off
        raise ValueError("tipo de encoded_value desconocido: 0x%02x" % vtype)


def load_dex_files(path):
    """Todos los DEX de un archivo suelto (.dex), un zip (.apk/.jar) o una carpeta."""
    found = []
    if os.path.isdir(path):
        for root, _, names in sorted(os.walk(path)):
            for name in sorted(names):
                found += load_dex_files(os.path.join(root, name))
        return found
    if not os.path.isfile(path):
        return found
    with open(path, "rb") as handle:
        head = handle.read(4)
    if head == b"dex\n":
        with open(path, "rb") as handle:
            found.append(Dex(handle.read(), path))
    elif head[:2] == b"PK":
        try:
            with zipfile.ZipFile(path) as archive:
                for name in sorted(archive.namelist()):
                    if name.endswith(".dex"):
                        found.append(Dex(archive.read(name), "%s!%s" % (path, name)))
        except zipfile.BadZipFile:
            pass
    return found


class ClassIndex:
    """Clases de un conjunto de DEX. Los miembros se leen solo cuando se piden."""

    def __init__(self, dex_files):
        self.cdefs = {}
        for dex in dex_files:
            for cdef in dex.class_defs():
                self.cdefs.setdefault(cdef["name"], cdef)
        self._members = {}

    def has(self, name):
        return name in self.cdefs

    def members(self, name):
        cached = self._members.get(name)
        if cached is None:
            cdef = self.cdefs[name]
            cached = cdef["dex"].members(cdef)
            self._members[name] = cached
        return cached

    def ancestors(self, name):
        seen = []
        stack = [name]
        while stack:
            current = stack.pop()
            if current in seen:
                continue
            seen.append(current)
            cdef = self.cdefs.get(current)
            if cdef is None:
                continue
            if cdef["super"]:
                stack.append(cdef["super"])
            stack.extend(cdef["ifaces"])
        if OBJECT not in seen:
            seen.append(OBJECT)
        return seen


def referenced_members(app_dex_files):
    """Todo lo que el código de la app toma de fuera: clases, campos y métodos referenciados."""
    classes = set()
    fields = set()
    methods = set()
    for dex in app_dex_files:
        for i in range(dex.n_type):
            desc = dex.type(i)
            if desc.startswith("L"):
                classes.add(desc)
        for i in range(dex.n_field):
            fields.add(dex.field_ref(i))
        for i in range(dex.n_method):
            methods.add(dex.method_ref(i))
    return classes, fields, methods


def referrers_of(app_dex_files, wanted_fields, wanted_methods, wanted_classes, mapping):
    """Para cada símbolo ausente, las clases (y métodos) de la app que lo usan en su código."""
    found = collections.defaultdict(set)
    for dex in app_dex_files:
        for cls, mname, kind, ref in dex.code_refs():
            if (kind == "field" and ref in wanted_fields) or (kind == "method" and ref in wanted_methods) \
                    or (kind == "class" and ref in wanted_classes):
                found[(kind, ref)].add("%s.%s" % (mapping.get(cls, cls), mname))
    return found


def link_check(app_index, app_dex_files, device_index, out, mapping=None):
    mapping = mapping or {}
    classes, fields, methods = referenced_members(app_dex_files)
    external = lambda c: not app_index.has(c)

    missing_classes = sorted(c for c in classes if external(c) and not device_index.has(c))
    missing_fields = collections.defaultdict(list)
    missing_methods = collections.defaultdict(list)

    for cls, name, typ in sorted(fields):
        if not external(cls) or not device_index.has(cls):
            continue
        if not any((name, typ) in device_index.members(a)[0]
                   for a in device_index.ancestors(cls) if device_index.has(a)):
            missing_fields[cls].append("%s : %s" % (name, typ))
    for cls, name, sig in sorted(methods):
        if not external(cls) or not device_index.has(cls):
            continue
        if not any((name, sig) in device_index.members(a)[1]
                   for a in device_index.ancestors(cls) if device_index.has(a)):
            missing_methods[cls].append("%s%s" % (name, sig))

    wf = {(c, n.split(" : ")[0], n.split(" : ")[1]) for c, items in missing_fields.items() for n in items}
    wm = set()
    for c, items in missing_methods.items():
        for n in items:
            k = n.index("(")
            wm.add((c, n[:k], n[k:]))
    refs = referrers_of(app_dex_files, wf, wm, set(missing_classes), mapping)

    def show(kind, ref):
        who = sorted(refs.get((kind, ref), ()))
        if not who:
            print("      (sin referencias en el bytecode: solo declarado)", file=out)
        for item in who[:8]:
            print("      usado en %s" % item, file=out)
        if len(who) > 8:
            print("      ... y %d más" % (len(who) - 8), file=out)

    print("== ESCANEO de bytecode: %d métodos recorridos, %d desalineados (debe ser 0)"
          % (SCAN_STATS[0], SCAN_STATS[1]), file=out)
    print("== ENLACE: clases que la app usa y el sistema no tiene (%d)" % len(missing_classes), file=out)
    for cls in missing_classes:
        print("  CLASE   %s" % mapping.get(cls, cls), file=out)
        show("class", cls)
    print("== ENLACE: campos que faltan (%d clases)" % len(missing_fields), file=out)
    for cls in sorted(missing_fields):
        for item in missing_fields[cls]:
            print("  CAMPO   %s.%s" % (cls, item), file=out)
            name, typ = item.split(" : ")
            show("field", (cls, name, typ))
    print("== ENLACE: métodos que faltan (%d clases)" % len(missing_methods), file=out)
    for cls in sorted(missing_methods):
        for item in missing_methods[cls]:
            print("  METODO  %s.%s" % (cls, item), file=out)
            k = item.index("(")
            show("method", (cls, item[:k], item[k:]))
    total = (len(missing_classes) + sum(map(len, missing_fields.values()))
             + sum(map(len, missing_methods.values())))
    print("== ENLACE: total %d (clases referenciadas: %d, campos: %d, métodos: %d)"
          % (total, len(classes), len(fields), len(methods)), file=out)
    return total


def transactions(index, name):
    return {k: v for k, v in index.members(name)[2].items() if k.startswith("TRANSACTION_")}


def aidl_check(app_index, device_index, out):
    """Compara cada ...$Stub de la app con el del sistema, si existe."""
    differences = 0
    compared = 0
    only_app = []
    for stub in sorted(c for c in app_index.cdefs if c.endswith("$Stub;")):
        app_tx = transactions(app_index, stub)
        if not app_tx:
            continue
        if not device_index.has(stub):
            only_app.append(stub)
            continue
        compared += 1
        dev_tx = transactions(device_index, stub)
        iface = stub[:-len("$Stub;")] + ";"
        problems = []
        for name in sorted(set(app_tx) | set(dev_tx)):
            a, d = app_tx.get(name), dev_tx.get(name)
            if a != d:
                problems.append("transacción %s: app=%s sistema=%s" % (name, a, d))
        if app_index.has(iface) and device_index.has(iface):
            app_methods = app_index.members(iface)[1]
            dev_methods = device_index.members(iface)[1]
            for name, sig in sorted(app_methods - dev_methods):
                problems.append("método solo en la app: %s%s" % (name, sig))
            for name, sig in sorted(dev_methods - app_methods):
                problems.append("método solo en el sistema: %s%s" % (name, sig))
        if problems:
            differences += len(problems)
            print("  DIFERENCIA %s" % stub[:-len("$Stub;")], file=out)
            for problem in problems:
                print("      %s" % problem, file=out)
    print("== AIDL: interfaces comparadas: %d; con diferencias: contadas arriba; "
          "solo en la app (no están en los binarios dados): %d" % (compared, len(only_app)), file=out)
    for stub in only_app:
        print("  SOLO-APP %s" % stub[:-len("$Stub;")], file=out)
    print("== AIDL: total de diferencias %d" % differences, file=out)
    return differences


def parse_aidl(path):
    """(descriptor de clase, [métodos en orden de declaración]) de un .aidl con una interfaz."""
    import re
    with open(path, encoding="utf-8", errors="replace") as handle:
        text = handle.read()
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    text = re.sub(r"//[^\n]*", " ", text)
    pkg = re.search(r"^\s*package\s+([\w.]+)\s*;", text, re.M)
    iface = re.search(r"\binterface\s+(\w+)\s*\{", text)
    if not pkg or not iface:
        return None
    start = iface.end()
    depth, end = 1, start
    while end < len(text) and depth:
        depth += {"{": 1, "}": -1}.get(text[end], 0)
        end += 1
    body = text[start:end - 1]
    methods = []
    for stmt in body.split(";"):
        stmt = re.sub(r"@\w+(\([^)]*\))?", " ", stmt).strip()
        if "(" not in stmt or re.match(r"^(const|parcelable)\b", stmt):
            continue
        head = stmt[:stmt.index("(")]
        if "=" in head:
            continue
        methods.append(head.split()[-1])
    return "L%s/%s;" % (pkg.group(1).replace(".", "/"), iface.group(1)), methods


def aidl_source_check(src_dirs, system_index, out):
    """Compara el orden de métodos de los .aidl del repositorio con los códigos de transacción del
    sistema. El código de un método AIDL es su posición (desde 1): si SystemUI tiene otro orden,
    una llamada de Nexus llega a otro método. Es independiente de R8, que borra las constantes
    TRANSACTION_* del APK."""
    compared = differences = absent = 0
    for src in src_dirs:
        for root, _, names in sorted(os.walk(src)):
            for name in sorted(names):
                if not name.endswith(".aidl"):
                    continue
                parsed = parse_aidl(os.path.join(root, name))
                if not parsed or not parsed[1]:
                    continue
                desc, methods = parsed
                stub = desc[:-1] + "$Stub;"
                if not system_index.has(stub):
                    absent += 1
                    print("  AUSENTE  %s (la interfaz no está en el sistema con ese nombre)" % desc[:-1], file=out)
                    continue
                compared += 1
                system = {k[len("TRANSACTION_"):]: v for k, v in transactions(system_index, stub).items()}
                problems = []
                for index, method in enumerate(methods, 1):
                    got = system.get(method)
                    if got is None:
                        problems.append("%s: el repositorio la tiene (código %d) y el sistema no" % (method, index))
                    elif got != index:
                        problems.append("%s: repositorio=%d sistema=%d" % (method, index, got))
                for method in sorted(set(system) - set(methods)):
                    problems.append("%s: el sistema la tiene (código %d) y el repositorio no" % (method, system[method]))
                if problems:
                    differences += len(problems)
                    print("  DIFERENCIA %s" % desc[:-1], file=out)
                    for item in problems:
                        print("      %s" % item, file=out)
    print("== AIDL-FUENTES: %d interfaces comparadas, %d ausentes en el sistema, %d diferencias"
          % (compared, absent, differences), file=out)
    return differences


def dump_class(device_index, name, out):
    if not device_index.has(name):
        print("== %s: NO EXISTE en el sistema" % name, file=out)
        return
    fields, methods, consts = device_index.members(name)
    print("== %s" % name, file=out)
    for fname, ftype in sorted(fields):
        extra = " = %d" % consts[fname] if fname in consts else ""
        print("  campo   %s : %s%s" % (fname, ftype, extra), file=out)
    for mname, sig in sorted(methods):
        print("  metodo  %s%s" % (mname, sig), file=out)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawTextHelpFormatter)
    parser.add_argument("--app", nargs="+", required=True, help="APK o DEX de la app")
    parser.add_argument("--device", nargs="+", required=True,
                        help="lo que la app ve en ejecución (classpath de arranque): para --link")
    parser.add_argument("--aidl-device", nargs="*", default=[],
                        help="binarios extra para --aidl y --dump (p. ej. SystemUI, que la app no ve)")
    parser.add_argument("--link", action="store_true")
    parser.add_argument("--aidl", action="store_true")
    parser.add_argument("--dump", nargs="*", default=[], help="descriptores, p. ej. Landroid/view/IWindowManager;")
    parser.add_argument("--aidl-src", nargs="*", default=[],
                        help="carpetas con .aidl del repositorio para comparar con el sistema")
    parser.add_argument("--mapping", help="mapping.txt de R8: muestra los nombres originales de las clases de la app")
    parser.add_argument("--find", nargs="*", default=[],
                        help="lista las clases del sistema (con SystemUI) cuyo nombre contiene estos textos")
    args = parser.parse_args()

    app_dex = [d for p in args.app for d in load_dex_files(p)]
    device_dex = [d for p in args.device for d in load_dex_files(p)]
    if not app_dex:
        sys.exit("No encuentro ningún DEX en --app")
    if not device_dex:
        sys.exit("No encuentro ningún DEX en --device")
    app_index = ClassIndex(app_dex)
    device_index = ClassIndex(device_dex)
    extra_dex = [d for p in args.aidl_device for d in load_dex_files(p)]
    # --aidl y --dump ven también lo extra; --link solo lo que la app ve de verdad.
    wide_index = ClassIndex(device_dex + extra_dex) if extra_dex else device_index
    print("app: %d DEX, %d clases | sistema: %d DEX, %d clases (con extra: %d)"
          % (len(app_dex), len(app_index.cdefs), len(device_dex), len(device_index.cdefs),
             len(wide_index.cdefs)))

    for text in args.find:
        hits = sorted(c for c in wide_index.cdefs if text in c)
        print("== BUSCAR %r: %d clases" % (text, len(hits)))
        for c in hits[:60]:
            print("  %s" % c)

    mapping = {}
    if args.mapping and os.path.isfile(args.mapping):
        with open(args.mapping, encoding="utf-8", errors="replace") as handle:
            for line in handle:
                if line.startswith("#") or line.startswith(" ") or " -> " not in line:
                    continue
                orig, obf = line.rstrip().rstrip(":").split(" -> ")
                mapping["L%s;" % obf.replace(".", "/")] = "L%s;" % orig.replace(".", "/")

    for name in args.dump:
        dump_class(wide_index, name if name.endswith(";") else name + ";", sys.stdout)
    if args.link:
        link_check(app_index, app_dex, device_index, sys.stdout, mapping)
    if args.aidl:
        aidl_check(app_index, wide_index, sys.stdout)
    if args.aidl_src:
        aidl_source_check(args.aidl_src, wide_index, sys.stdout)


if __name__ == "__main__":
    main()
