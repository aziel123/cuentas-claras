# Cuentas Claras · contenedor de HERRAMIENTAS (sprint 8, tanda 3; docs/operacion/despliegue-gcp.md).
# En la VM: corre respaldar.sh (sprint 7), 01, 02 y 03 contra Cloud SQL y el «paso 0» (el job mysql del CI contra una
# instancia de prueba). En la máquina del responsable técnico: lo mismo, más los jobs del CI reproducidos en local.
# Solo paquetes de Ubuntu (sin binarios de otras fuentes): cliente mysql, mysqldump, age, rclone, el JDK 21 (./mvnw y
# java -jar … migrar o verificar-respaldo), socat (reenvía 127.0.0.1:3306 a la base del paso 0) y python3 con yaml.
# La imagen base va fijada por digest (Dependabot la actualiza).
FROM ubuntu:24.04@sha256:68ee22004a9109b44973313b02ff7b1de250f1de0f5ecb628d73f579aaff33ac

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
       mysql-client age rclone openjdk-21-jdk-headless socat curl ca-certificates gzip jq python3 python3-yaml sudo \
    && rm -rf /var/lib/apt/lists/*

# Sin credenciales en la imagen: las claves llegan en el momento (variables o entrada estándar) y no se guardan.
WORKDIR /repo
CMD ["/bin/bash"]
