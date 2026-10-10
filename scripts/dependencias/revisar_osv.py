"""Regla de dependencias del proyecto (sprint 7, tanda 3; decisión 102) sobre la salida JSON de OSV-Scanner.

Uso: python3 -I revisar_osv.py salida-osv.json

Bloquea (código 1) una vulnerabilidad CRÍTICA o ALTA (CVSS 7.0 o más; si el aviso no trae puntaje, la gravedad de su base
de datos) que tenga una versión corregida publicada para ese paquete. Lista las demás como aviso. Código 2 si el archivo
no es una salida de OSV-Scanner.
"""
import json
import sys

GRAVEDADES_TEXTO = {"CRITICAL": 9.0, "HIGH": 7.0, "MODERATE": 5.0, "MEDIUM": 5.0, "LOW": 2.0}


def puntaje(grupo, aviso):
    """Puntaje CVSS del grupo (OSV-Scanner lo calcula como max_severity) o, si falta, la gravedad en texto del aviso."""
    texto = str(grupo.get("max_severity") or "").strip()
    try:
        return float(texto)
    except ValueError:
        pass
    gravedad = str((aviso.get("database_specific") or {}).get("severity") or "").upper()
    return GRAVEDADES_TEXTO.get(gravedad)


def mismo_paquete(nombre_aviso, nombre):
    return nombre_aviso == nombre or nombre_aviso.endswith(":" + nombre) or nombre.endswith(":" + nombre_aviso)


def corregidas(aviso, nombre):
    versiones = []
    for afectado in aviso.get("affected") or []:
        if not mismo_paquete((afectado.get("package") or {}).get("name", ""), nombre):
            continue
        for rango in afectado.get("ranges") or []:
            for evento in rango.get("events") or []:
                if "fixed" in evento:
                    versiones.append(evento["fixed"])
    return versiones


def main(ruta):
    try:
        with open(ruta, encoding="utf-8") as archivo:
            datos = json.load(archivo)
    except (OSError, ValueError) as error:
        print("No se pudo leer la salida de OSV-Scanner: %s" % error)
        return 2
    if not isinstance(datos, dict) or "results" not in datos:
        print("El archivo no es una salida JSON de OSV-Scanner.")
        return 2
    bloqueantes, avisos, paquetes = [], [], 0
    for resultado in datos.get("results") or []:
        for item in resultado.get("packages") or []:
            paquetes += 1
            paquete = item.get("package") or {}
            nombre, version = paquete.get("name", "?"), paquete.get("version", "?")
            por_id = {v.get("id"): v for v in item.get("vulnerabilities") or []}
            for grupo in item.get("groups") or []:
                ids = grupo.get("ids") or []
                aviso = next((por_id[i] for i in ids if i in por_id), {})
                cvss = puntaje(grupo, aviso)
                arreglo = corregidas(aviso, nombre)
                fila = "%s %s · %s · CVSS %s · %s" % (nombre, version, ", ".join(ids), "?" if cvss is None else cvss,
                                                     "corregida en " + ", ".join(sorted(set(arreglo))) if arreglo
                                                     else "sin versión corregida")
                if cvss is not None and cvss >= 7.0 and arreglo:
                    bloqueantes.append(fila)
                else:
                    avisos.append(fila)
    print("OSV-Scanner revisó las dependencias: %d paquetes con alguna vulnerabilidad conocida." % paquetes)
    for fila in avisos:
        print("AVISO (no bloquea): " + fila)
    for fila in bloqueantes:
        print("BLOQUEA (CRÍTICA o ALTA con arreglo): " + fila)
        print("::error::Dependencia vulnerable con arreglo: " + fila)
    if bloqueantes:
        print("Actualiza esas dependencias (en el pom.xml, la versión corregida) antes de unir el cambio.")
        return 1
    print("OK: ninguna vulnerabilidad CRÍTICA o ALTA con arreglo disponible.")
    return 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(2)
    sys.exit(main(sys.argv[1]))
