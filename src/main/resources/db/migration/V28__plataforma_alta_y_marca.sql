-- Sprint 8 · tanda 1 (docs/arquitectura/sprint-8-lista-para-vender.md, sección 5). Colegio de plataforma (operadores),
-- estados del colegio, ficha y marca del colegio, primera Promotoría una sola vez por colegio, logo y correo externo de
-- control registrado por el operador. Compatible con MySQL 8 y con H2 en modo MySQL. Nada se borra.

-- H8: la ficha del colegio. tipo: COLEGIO (cliente) o PLATAFORMA (donde viven los operadores; una sola fila). estado:
-- PREPARACION (la base no deja registrar pagos: trg_pago_registro) o PRODUCCION (sin vuelta atrás: trg_colegio_cambio).
ALTER TABLE colegio ADD COLUMN tipo VARCHAR(12) NOT NULL DEFAULT 'COLEGIO';
ALTER TABLE colegio ADD COLUMN plataforma_unica BOOLEAN;                 -- TRUE solo en la fila PLATAFORMA; si no, NULL
ALTER TABLE colegio ADD COLUMN estado VARCHAR(12) NOT NULL DEFAULT 'PREPARACION';
ALTER TABLE colegio ADD COLUMN razon_social VARCHAR(150);
ALTER TABLE colegio ADD COLUMN direccion VARCHAR(200);
ALTER TABLE colegio ADD COLUMN correo_institucional VARCHAR(150);
ALTER TABLE colegio ADD COLUMN niveles VARCHAR(40) NOT NULL DEFAULT 'INICIAL,PRIMARIA,SECUNDARIA';
ALTER TABLE colegio ADD COLUMN titular_nombre VARCHAR(150);
ALTER TABLE colegio ADD COLUMN titular_documento VARCHAR(12);
ALTER TABLE colegio ADD COLUMN contrato_codigo VARCHAR(30);
ALTER TABLE colegio ADD COLUMN aviso_privacidad_version VARCHAR(20);
ALTER TABLE colegio ADD COLUMN aviso_privacidad_aprobado_por VARCHAR(60);
ALTER TABLE colegio ADD COLUMN aviso_privacidad_aprobado_en DATETIME(6);
ALTER TABLE colegio ADD COLUMN produccion_desde DATETIME(6);
ALTER TABLE colegio ADD COLUMN produccion_por VARCHAR(60);
ALTER TABLE colegio ADD COLUMN creado_por VARCHAR(60) NOT NULL DEFAULT 'migracion.v1';
ALTER TABLE colegio ADD COLUMN actualizado_en DATETIME(6);
ALTER TABLE colegio ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE colegio ADD CONSTRAINT ck_colegio_tipo CHECK (tipo IN ('COLEGIO', 'PLATAFORMA'));
ALTER TABLE colegio ADD CONSTRAINT ck_colegio_plataforma_unica CHECK (
    (tipo = 'PLATAFORMA' AND plataforma_unica) OR (tipo = 'COLEGIO' AND plataforma_unica IS NULL));
ALTER TABLE colegio ADD CONSTRAINT uk_colegio_plataforma UNIQUE (plataforma_unica);
ALTER TABLE colegio ADD CONSTRAINT ck_colegio_estado CHECK (estado IN ('PREPARACION', 'PRODUCCION')
    AND (tipo = 'COLEGIO' OR estado = 'PRODUCCION'));
ALTER TABLE colegio ADD CONSTRAINT ck_colegio_produccion CHECK ((estado = 'PREPARACION' AND produccion_desde IS NULL)
    OR (estado = 'PRODUCCION' AND produccion_desde IS NOT NULL AND produccion_por IS NOT NULL));
-- El RUC: 11 dígitos que empiezan con 10 o 20 (el dígito verificador lo valida la aplicación, ValidadorRuc).
ALTER TABLE colegio ADD CONSTRAINT ck_colegio_ruc CHECK (ruc IS NULL OR REGEXP_LIKE(ruc, '^(10|20)[0-9]{9}$', 'c'));
ALTER TABLE colegio ADD CONSTRAINT ck_colegio_niveles CHECK (REGEXP_LIKE(niveles,
    '^(INICIAL(,PRIMARIA)?(,SECUNDARIA)?|PRIMARIA(,SECUNDARIA)?|SECUNDARIA)$', 'c'));
ALTER TABLE colegio ADD CONSTRAINT ck_colegio_titular CHECK (titular_documento IS NULL
    OR REGEXP_LIKE(titular_documento, '^[0-9]{8}$|^[A-Z0-9]{9,12}$', 'c'));

-- Los colegios que ya operan (tienen cuentas) quedan en producción; la fila de V1 en una instalación nueva, en preparación.
UPDATE colegio SET estado = 'PRODUCCION', produccion_desde = CURRENT_TIMESTAMP(6), produccion_por = 'migracion.v28'
 WHERE EXISTS (SELECT 1 FROM usuario u WHERE u.colegio_id = colegio.id);
UPDATE colegio SET actualizado_en = CURRENT_TIMESTAMP(6);
ALTER TABLE colegio MODIFY actualizado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

-- El colegio de plataforma: ahí viven los operadores (rol OPERADOR_PLATAFORMA). Nunca tiene familias ni dinero.
INSERT INTO colegio (nombre, tipo, plataforma_unica, estado, activo, creado_por, actualizado_en, produccion_desde,
    produccion_por)
VALUES ('Plataforma', 'PLATAFORMA', TRUE, 'PRODUCCION', TRUE, 'migracion.v28', CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6), 'migracion.v28');

-- Rol nuevo (V2 creó el CHECK; nadie lo recreó después). Solo existe en el colegio de plataforma (trg_usuario_rol_alta).
ALTER TABLE usuario_rol DROP CONSTRAINT ck_usuario_rol_rol;
ALTER TABLE usuario_rol ADD CONSTRAINT ck_usuario_rol_rol CHECK (rol IN ('PROMOTOR', 'DIRECTOR', 'ADMINISTRACION', 'CAJA',
    'DOCENTE', 'APODERADO', 'OPERADOR_PLATAFORMA'));

-- H7. La primera Promotoría, una sola vez por colegio (patrón de primera_direccion, V27). La escribe SOLO cc_sistema, en
-- la misma transacción que la cuenta del titular; en MySQL, trg_primera_promotoria_nace exige un operador activo con su
-- firma y un colegio en preparación cuya única cuenta es la del titular, y trg_usuario_rol_alta exige esta fila para el
-- primer PROMOTOR. De solo inserción.
CREATE TABLE primera_promotoria (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id      BIGINT        NOT NULL,
    usuario_id      BIGINT        NOT NULL,   -- el titular
    operador_id     BIGINT,                   -- NULL solo en las filas de la migración
    plataforma_id   BIGINT,
    creado_en       DATETIME(6)   NOT NULL,
    creado_por      VARCHAR(60)   NOT NULL,   -- el operador
    actualizado_en  DATETIME(6)   NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_primera_promotoria_colegio UNIQUE (colegio_id),
    CONSTRAINT fk_primera_promotoria_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_primera_promotoria_usuario FOREIGN KEY (usuario_id, colegio_id) REFERENCES usuario (id, colegio_id),
    CONSTRAINT fk_primera_promotoria_operador FOREIGN KEY (operador_id, plataforma_id) REFERENCES usuario (id, colegio_id),
    CONSTRAINT ck_primera_promotoria_origen CHECK ((operador_id IS NOT NULL AND plataforma_id IS NOT NULL)
        OR creado_por = 'migracion.v28')
);
-- Los colegios que ya tienen Promotoría ya gastaron su primera.
INSERT INTO primera_promotoria (colegio_id, usuario_id, creado_en, creado_por, actualizado_en)
SELECT u.colegio_id, MIN(u.id), CURRENT_TIMESTAMP(6), 'migracion.v28', CURRENT_TIMESTAMP(6)
  FROM usuario u JOIN usuario_rol r ON r.usuario_id = u.id
 WHERE r.rol = 'PROMOTOR'
 GROUP BY u.colegio_id;

-- Logo del colegio: PNG reescrito por la aplicación (400 px y 200 KB como máximo). Uno vigente por colegio (UNIQUE con
-- vigente TRUE o NULL). El contenido no cambia (GRANT por columna: solo vigente).
CREATE TABLE colegio_logo (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id      BIGINT        NOT NULL,
    contenido       MEDIUMBLOB    NOT NULL,
    sha256          VARCHAR(64)   NOT NULL,
    bytes           INT           NOT NULL,
    ancho           INT           NOT NULL,
    alto            INT           NOT NULL,
    vigente         BOOLEAN,
    creado_en       DATETIME(6)   NOT NULL,
    creado_por      VARCHAR(60)   NOT NULL,
    actualizado_en  DATETIME(6)   NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_colegio_logo_vigente UNIQUE (colegio_id, vigente),
    CONSTRAINT fk_colegio_logo_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT ck_colegio_logo_vigente CHECK (vigente IS NULL OR vigente),
    CONSTRAINT ck_colegio_logo_tamano CHECK (bytes > 0 AND bytes <= 204800 AND ancho BETWEEN 1 AND 400
        AND alto BETWEEN 1 AND 400),
    CONSTRAINT ck_colegio_logo_sha CHECK (REGEXP_LIKE(sha256, '^[0-9a-f]{64}$', 'c'))
);

-- H10. El correo externo de control lo registra el operador (con cc_sistema y su firma: trg_configuracion_colegio_*), ya no
-- el DBA a mano. Las filas que ya existen quedan como «dba».
ALTER TABLE configuracion_colegio ADD COLUMN creado_por VARCHAR(60) NOT NULL DEFAULT 'dba';
ALTER TABLE configuracion_colegio ADD COLUMN actualizado_por VARCHAR(60);
ALTER TABLE configuracion_colegio ADD COLUMN actualizado_en DATETIME(6);
ALTER TABLE configuracion_colegio ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
