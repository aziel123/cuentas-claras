#!/usr/bin/env python3
"""Cuentas Claras · corre los pasos de UN job de .github/workflows/ci.yml fuera de GitHub (sprint 8, tanda 3).

Sirve para el «paso 0» (el job mysql contra una instancia de prueba de Cloud SQL: deploy/gcp/paso-0.sh) y para
reproducir los jobs mysql y respaldo del CI en local, en contenedores desechables. Corre dentro del contenedor de
herramientas (deploy/herramientas/Dockerfile: JDK 21, cliente mysql, age, rclone, python3-yaml), con el repositorio en el
directorio actual. Los servicios del job (los MySQL) NO los levanta: los pone quien lo llama, en 127.0.0.1:3306 (y
:3308 para la restauración del job respaldo), o los reenvía con socat a una base remota.

    python3 scripts/ci/correr-job.py mysql [--desde N] [--hasta N] [--env CLAVE=VALOR ...] [--quitar TEXTO ...]

- Cada paso «run» corre con bash -eo pipefail y el entorno del job (más el del paso, con ${{ env.X }} resuelto).
- $GITHUB_ENV funciona como en GitHub: lo que un paso agrega, lo ven los siguientes.
- --env cambia una variable del job (por ejemplo, la clave del administrador de la base del paso 0).
- --quitar borra las líneas de los scripts que contienen ese texto (por ejemplo «SET PERSIST log_bin_trust_function_creators»,
  que un MySQL administrado no permite: la marca va en la configuración de la instancia).
- Los pasos «uses» (checkout, setup-java) se saltan: el repositorio y el JDK ya están.
Al final imprime el resumen (paso, nombre, OK o FALLÓ) y sale con 1 si alguno falló. No guarda ninguna clave.
"""
import argparse
import os
import re
import subprocess
import sys
import tempfile

import yaml


def expandir(valor, entorno):
    return re.sub(r"\$\{\{\s*env\.(\w+)\s*\}\}", lambda m: entorno.get(m.group(1), ""), str(valor))


def leer_github_env(ruta, entorno):
    if not os.path.exists(ruta):
        return
    with open(ruta, encoding="utf-8") as f:
        for linea in f.read().splitlines():
            if "=" in linea:
                clave, valor = linea.split("=", 1)
                entorno[clave] = valor


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("job")
    p.add_argument("--ci", default=".github/workflows/ci.yml")
    p.add_argument("--desde", type=int, default=0, help="primer paso (índice en el job) que se corre")
    p.add_argument("--hasta", type=int, default=10_000, help="último paso que se corre")
    p.add_argument("--env", action="append", default=[], metavar="CLAVE=VALOR")
    p.add_argument("--quitar", action="append", default=[], metavar="TEXTO")
    p.add_argument("--omitir", action="append", default=[], metavar="TEXTO",
                   help="no corre los pasos cuyo nombre contiene ese texto")
    args = p.parse_args()

    with open(args.ci, encoding="utf-8") as f:
        ci = yaml.safe_load(f)
    definicion = ci["jobs"][args.job]
    entorno = dict(os.environ)
    entorno.update({k: str(v) for k, v in (definicion.get("env") or {}).items()})
    for par in args.env:
        clave, valor = par.split("=", 1)
        entorno[clave] = valor
    github_env = os.path.join(tempfile.gettempdir(), "cc-github-env-" + args.job)
    if args.desde == 0 and os.path.exists(github_env):
        os.remove(github_env)
    open(github_env, "a", encoding="utf-8").close()
    entorno["GITHUB_ENV"] = github_env

    resultados = []
    for i, paso in enumerate(definicion["steps"]):
        nombre = paso.get("name", paso.get("uses", "?"))
        if "run" not in paso or i < args.desde or i > args.hasta:
            continue
        if any(t in nombre for t in args.omitir):
            resultados.append((i, nombre, "OMITIDO"))
            continue
        leer_github_env(github_env, entorno)
        entorno_paso = dict(entorno)
        for clave, valor in (paso.get("env") or {}).items():
            entorno_paso[clave] = expandir(valor, entorno)
        script = paso["run"]
        if args.quitar:
            script = "\n".join(l for l in script.splitlines() if not any(t in l for t in args.quitar))
        print("\n=== [%d] %s" % (i, nombre), flush=True)
        r = subprocess.run(["bash", "-eo", "pipefail", "-c", script], env=entorno_paso)
        estado = "OK" if r.returncode == 0 else "FALLÓ (%d)" % r.returncode
        resultados.append((i, nombre, estado))
        if r.returncode != 0:
            break

    print("\n===== Resumen del job %s =====" % args.job)
    for i, nombre, estado in resultados:
        print("[%d] %s: %s" % (i, nombre, estado))
    sys.exit(0 if all(e in ("OK", "OMITIDO") for _, _, e in resultados) else 1)


if __name__ == "__main__":
    main()
