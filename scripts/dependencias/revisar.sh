#!/bin/sh
# Cuentas Claras · dependencias con vulnerabilidades conocidas (sprint 7, tanda 3; A06 de OWASP, decisión 102).
#
#   sh scripts/dependencias/revisar.sh [SBOM]
#
# 1. OSV-Scanner (la imagen OFICIAL de Google en ghcr.io, fijada por su digest: nada se descarga de otra fuente) lee el
#    SBOM CycloneDX que genera el build (por defecto target/classes/META-INF/sbom/application.cdx.json, solo lo que va en
#    el jar) y consulta la base pública osv.dev.
# 2. revisar_osv.py decide: falla (código 1) si alguna vulnerabilidad es CRÍTICA o ALTA (CVSS 7.0 o más, o esa gravedad
#    en la base de datos del aviso) y tiene una versión corregida publicada para ese paquete. Las demás se listan como
#    aviso: Dependabot propone la actualización cada semana (.github/dependabot.yml).
#
# Variables opcionales: OSV_SALIDA (dónde dejar el JSON de OSV-Scanner; por defecto, un archivo temporal).
# Código de salida: 0 sin bloqueantes, 1 con alguna CRÍTICA o ALTA con arreglo, 2 si no se pudo revisar.
set -eu

sbom="${1:-target/classes/META-INF/sbom/application.cdx.json}"
if [ ! -f "$sbom" ]; then
	echo "No existe el SBOM $sbom: corre antes ./mvnw -B -DskipTests package"
	exit 2
fi

# OSV-Scanner v2.6.0 (publicado el 14/09/2026). Actualizar el digest junto con la etiqueta.
imagen="ghcr.io/google/osv-scanner@sha256:afd838850ac1a0fcc15ff4a041dc9ba11123c3f0d2666217a5f0fcf9222b55fa"

dir=$(cd "$(dirname "$sbom")" && pwd)
nombre=$(basename "$sbom")
case "$nombre" in
	*.cdx.json) ;;
	*) echo "El SBOM debe llamarse *.cdx.json (así lo reconoce OSV-Scanner): $nombre"; exit 2 ;;
esac
# En Windows (Git Bash) Docker necesita la ruta de Windows.
if command -v cygpath > /dev/null 2>&1; then
	dir=$(cygpath -w "$dir")
fi
salida="${OSV_SALIDA:-$(mktemp)}"

# OSV-Scanner sale con 1 cuando encuentra cualquier vulnerabilidad; la regla del proyecto la aplica revisar_osv.py.
set +e
MSYS_NO_PATHCONV=1 docker run --rm -v "$dir:/sbom:ro" "$imagen" scan -L "/sbom/$nombre" --format json > "$salida"
codigo=$?
set -e
if [ "$codigo" -ne 0 ] && [ "$codigo" -ne 1 ]; then
	echo "OSV-Scanner no pudo revisar el SBOM (código $codigo)."
	exit 2
fi

python3 -I "$(dirname "$0")/revisar_osv.py" "$salida"
