-- Sprint 7 · correcciones tras la auditoría y QA (docs/arquitectura/sprint-7-correcciones.md). No se editan V24 a V26:
-- todo cambio de esquema va aquí. Compatible con MySQL 8.0.19+ y con H2 en modo MySQL. Nada se borra.

-- QA-S7-1. La alerta «Faltan filas» de un respaldo se mantiene hasta que una PERSONA la resuelve con motivo: Promotoría,
-- con la firma de su sesión, nunca cc_respaldo (el operador) ni un actor de sistema. Resolver el último respaldo con
-- FALTAN_FILAS cierra el incidente (y los anteriores). Como respaldo, es técnica y de toda la base: guarda el colegio y
-- la persona que resolvió, pero no es «de» un colegio (la alerta es de la base). De solo inserción (GRANT y trigger
-- trg_resolucion_respaldo_registro en MySQL; queda además en la bitácora como RESPALDO_FALTAN_FILAS_RESUELTO).
CREATE TABLE resolucion_respaldo (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    respaldo_id     BIGINT        NOT NULL,
    colegio_id      BIGINT        NOT NULL,
    usuario_id      BIGINT        NOT NULL,
    motivo          VARCHAR(500)  NOT NULL,
    creado_en       DATETIME(6)   NOT NULL,
    creado_por      VARCHAR(60)   NOT NULL,
    CONSTRAINT uk_resolucion_respaldo UNIQUE (respaldo_id),
    CONSTRAINT fk_resolucion_respaldo_respaldo FOREIGN KEY (respaldo_id) REFERENCES respaldo (id),
    CONSTRAINT fk_resolucion_respaldo_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_resolucion_respaldo_usuario FOREIGN KEY (usuario_id, colegio_id) REFERENCES usuario (id, colegio_id),
    CONSTRAINT ck_resolucion_respaldo_motivo CHECK (CHAR_LENGTH(TRIM(motivo)) >= 10),
    CONSTRAINT ck_resolucion_respaldo_actor CHECK (creado_por NOT LIKE 'sistema%' AND creado_por <> 'cc_respaldo')
);

-- Observación de QA (roles). La excepción de la primera Dirección (dar DIRECTOR sin solicitud en un colegio con una sola
-- Promotoría y sin Dirección) se usa UNA vez por colegio, para la primera Dirección de su historia: esta fila la marca
-- (UNIQUE por colegio). La escribe solo cc_sistema (GRANT), en la misma transacción que el rol; en MySQL,
-- trg_usuario_rol_alta exige la fila de ESE colegio y de ESA cuenta, recién escrita. De solo inserción.
CREATE TABLE primera_direccion (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id      BIGINT        NOT NULL,
    usuario_id      BIGINT        NOT NULL,
    creado_en       DATETIME(6)   NOT NULL,
    creado_por      VARCHAR(60)   NOT NULL,
    actualizado_en  DATETIME(6)   NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_primera_direccion_colegio UNIQUE (colegio_id),
    CONSTRAINT fk_primera_direccion_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_primera_direccion_usuario FOREIGN KEY (usuario_id, colegio_id) REFERENCES usuario (id, colegio_id)
);
-- Los colegios que ya tuvieron Dirección (una cuenta con el rol, o un CAMBIO_ROLES aprobado que la dio) ya gastaron su
-- excepción.
INSERT INTO primera_direccion (colegio_id, usuario_id, creado_en, creado_por, actualizado_en)
SELECT x.colegio_id, MIN(x.usuario_id), CURRENT_TIMESTAMP(6), 'migracion.v27', CURRENT_TIMESTAMP(6)
  FROM (SELECT u.colegio_id AS colegio_id, u.id AS usuario_id FROM usuario u JOIN usuario_rol r ON r.usuario_id = u.id
         WHERE r.rol = 'DIRECTOR'
        UNION ALL
        SELECT s.colegio_id, s.entidad_id FROM solicitud_cambio s
         WHERE s.tipo = 'CAMBIO_ROLES' AND s.estado = 'APROBADA' AND s.datos LIKE '%DIRECTOR%') x
 GROUP BY x.colegio_id;

-- Observación de QA (roles). Desactivar o reactivar una cuenta con Promotoría o Dirección se hace con SU solicitud
-- ESTADO_CUENTA aprobada por otra persona (como roles_solicitud_id): enlazada aquí, usada una vez y solo hacia adelante.
-- En MySQL lo exige trg_usuario_identidad (con la firma de quien aprobó).
ALTER TABLE usuario ADD COLUMN estado_solicitud_id BIGINT;
ALTER TABLE usuario ADD CONSTRAINT uk_usuario_estado_solicitud UNIQUE (colegio_id, estado_solicitud_id);
ALTER TABLE usuario ADD CONSTRAINT fk_usuario_estado_solicitud FOREIGN KEY (estado_solicitud_id, colegio_id)
    REFERENCES solicitud_cambio (id, colegio_id);

-- S7-B1. La pantalla de cobro de caja (/caja/familias/{id}) queda en el registro de accesos con su propio tipo, COBRO:
-- nombra a la familia, pero NO cuenta para la alerta de más de 50 fichas en un día (una cajera abre decenas al día).
ALTER TABLE acceso_dato_personal DROP CONSTRAINT ck_acceso_dato_tipo;
ALTER TABLE acceso_dato_personal ADD CONSTRAINT ck_acceso_dato_tipo CHECK (tipo IN ('FICHA_FAMILIA', 'FICHA_ALUMNO',
    'BUSQUEDA', 'MOROSOS', 'LLAMADA_CONTROL', 'IMPORTACION', 'APROBACION_CONTACTO', 'COBRO'));
ALTER TABLE acceso_dato_personal DROP CONSTRAINT ck_acceso_dato_objeto;
ALTER TABLE acceso_dato_personal ADD CONSTRAINT ck_acceso_dato_objeto CHECK (
    (tipo IN ('FICHA_FAMILIA', 'APROBACION_CONTACTO', 'LLAMADA_CONTROL', 'COBRO') AND familia_id IS NOT NULL)
    OR (tipo = 'FICHA_ALUMNO' AND alumno_id IS NOT NULL)
    OR tipo IN ('BUSQUEDA', 'MOROSOS', 'IMPORTACION'));
