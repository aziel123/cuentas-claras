-- Sprint 8 · tanda 2 (docs/arquitectura/sprint-8-lista-para-vender.md, secciones 3.5 y 3.6). Conectores de cada colegio:
-- OSE, pasarela, cuenta bancaria y recaudación. Cada cambio es una fila nueva (PROPUESTO) que entra en vigencia con su
-- CAMBIO_CONECTOR aprobada y firmada por otra persona; la vigente pasa a REEMPLAZADO en la misma transacción. El secreto
-- va cifrado (AES-256-GCM) por la aplicación: la base nunca lo ve en claro. Compatible con MySQL 8 y con H2 en modo MySQL.
-- Nada se borra.
CREATE TABLE conector_colegio (
    id               BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id       BIGINT         NOT NULL,
    tipo             VARCHAR(20)    NOT NULL,
    proveedor        VARCHAR(20)    NOT NULL,
    modo             VARCHAR(10)    NOT NULL,
    configuracion    VARCHAR(2000)  NOT NULL,   -- JSON sin secretos (lo valida la aplicación con un record por tipo)
    secreto_cifrado  VARBINARY(2048),           -- nonce (12) + texto cifrado + etiqueta (16)
    secreto_pista    VARCHAR(4),                -- los 4 últimos caracteres del secreto principal
    clave_version    INT,                       -- versión de la clave maestra (CC_CLAVE_CONECTORES_VERSION)
    clave_publica    VARCHAR(36)    NOT NULL,   -- UUID opaco: ruta del aviso de la pasarela y parte del AAD
    estado           VARCHAR(12)    NOT NULL,
    vigente          BOOLEAN,                   -- TRUE solo el VIGENTE; si no, NULL (uno vigente por colegio y tipo)
    solicitud_id     BIGINT,                    -- su CAMBIO_CONECTOR
    propuesto_por    VARCHAR(60)    NOT NULL,
    resuelto_por     VARCHAR(60),
    resuelto_en      DATETIME(6),
    prueba_ok        BOOLEAN,
    prueba_en        DATETIME(6),
    prueba_detalle   VARCHAR(200),              -- sin secretos ni datos personales
    creado_en        DATETIME(6)    NOT NULL,
    creado_por       VARCHAR(60)    NOT NULL,
    actualizado_en   DATETIME(6)    NOT NULL,
    version          BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_conector_colegio_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT uk_conector_colegio_vigente UNIQUE (colegio_id, tipo, vigente),
    CONSTRAINT uk_conector_colegio_clave_publica UNIQUE (clave_publica),
    CONSTRAINT fk_conector_colegio_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_conector_colegio_solicitud FOREIGN KEY (solicitud_id, colegio_id)
        REFERENCES solicitud_cambio (id, colegio_id),
    CONSTRAINT ck_conector_colegio_tipo CHECK (tipo IN ('OSE', 'PASARELA', 'CUENTA_BANCARIA', 'RECAUDACION')),
    CONSTRAINT ck_conector_colegio_proveedor CHECK (
        (tipo = 'OSE' AND proveedor IN ('SIMULADO', 'NUBEFACT'))
        OR (tipo = 'PASARELA' AND proveedor IN ('SIMULADO', 'CULQI', 'NIUBIZ', 'IZIPAY'))
        OR (tipo = 'CUENTA_BANCARIA' AND proveedor IN ('BCP', 'BBVA', 'INTERBANK', 'SCOTIABANK', 'OTRO'))
        OR (tipo = 'RECAUDACION' AND proveedor IN ('GENERICO', 'BCP', 'BBVA', 'INTERBANK', 'SCOTIABANK'))),
    -- Una cuenta bancaria o una recaudación son siempre REAL (el dinero entra de verdad); SIMULADO va solo con el
    -- proveedor SIMULADO.
    CONSTRAINT ck_conector_colegio_modo CHECK (modo IN ('SIMULADO', 'PRUEBAS', 'REAL')
        AND ((proveedor = 'SIMULADO') = (modo = 'SIMULADO'))
        AND (tipo IN ('OSE', 'PASARELA') OR modo = 'REAL')),
    CONSTRAINT ck_conector_colegio_estado CHECK (estado IN ('PROPUESTO', 'VIGENTE', 'RECHAZADO', 'REEMPLAZADO')
        AND ((estado = 'VIGENTE' AND vigente) OR (estado <> 'VIGENTE' AND vigente IS NULL))),
    -- Solo el OSE y la pasarela tienen secreto; si hay secreto, hay versión de la clave maestra.
    CONSTRAINT ck_conector_colegio_secreto CHECK ((secreto_cifrado IS NULL AND secreto_pista IS NULL
        AND clave_version IS NULL) OR (secreto_cifrado IS NOT NULL AND clave_version IS NOT NULL AND clave_version > 0
            AND tipo IN ('OSE', 'PASARELA'))),
    CONSTRAINT ck_conector_colegio_resolucion CHECK ((estado = 'PROPUESTO' AND resuelto_por IS NULL)
        OR (estado = 'REEMPLAZADO') OR (estado IN ('VIGENTE', 'RECHAZADO') AND resuelto_por IS NOT NULL
            AND resuelto_en IS NOT NULL)),
    -- Quien propone es una persona (nunca un proceso sistema.*) y no se aprueba a sí mismo.
    CONSTRAINT ck_conector_colegio_actor CHECK (creado_por = propuesto_por AND creado_por NOT LIKE 'sistema%'
        AND (resuelto_por IS NULL OR estado <> 'VIGENTE' OR resuelto_por <> propuesto_por)),
    -- Un VIGENTE pasó la prueba de conexión.
    CONSTRAINT ck_conector_colegio_prueba CHECK (estado <> 'VIGENTE' OR (prueba_ok IS NOT NULL AND prueba_ok)),
    CONSTRAINT ck_conector_colegio_clave_publica CHECK (REGEXP_LIKE(clave_publica,
        '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c'))
);
CREATE INDEX ix_conector_colegio_tipo ON conector_colegio (colegio_id, tipo, estado);

-- Mensajes nuevos: el aviso a toda la Promotoría (y al correo externo de control) cuando un conector entra en vigencia
-- (cambia adónde entra el dinero del colegio) y cuando el colegio pasa a producción.
ALTER TABLE mensaje DROP CONSTRAINT ck_mensaje_tipo;
ALTER TABLE mensaje ADD CONSTRAINT ck_mensaje_tipo CHECK (tipo IN ('PAGO_REGISTRADO', 'PAGO_ANULADO',
    'DESCUENTO_APROBADO', 'CONTACTO_CAMBIADO', 'ACTIVACION_CUENTA', 'HUELLA_BITACORA', 'RECORDATORIO_VENCIMIENTO',
    'CUOTA_VENCIDA', 'RENOVACION_MATRICULA', 'RENOVACION_REGISTRADA', 'AVISO_ATENDIDO', 'VERIFICACION_CONTACTO',
    'APODERADO_AGREGADO', 'CONTACTO_POR_VERIFICAR', 'FERIADO_PROPUESTO', 'RESUMEN_DIARIO', 'ALERTA_PROMOTORIA',
    'CONECTOR_VIGENTE', 'COLEGIO_EN_PRODUCCION'));
-- Esos dos avisos también van al correo externo de control (el del colegio en configuracion_colegio; trg_mensaje_nace lo
-- exige), como la huella y el resumen.
ALTER TABLE mensaje DROP CONSTRAINT ck_mensaje_destinatario;
ALTER TABLE mensaje ADD CONSTRAINT ck_mensaje_destinatario CHECK (
    (destinatario_tipo = 'APODERADO' AND apoderado_id IS NOT NULL AND familia_id IS NOT NULL AND usuario_id IS NULL)
    OR (destinatario_tipo = 'USUARIO' AND usuario_id IS NOT NULL AND apoderado_id IS NULL AND familia_id IS NULL)
    OR (destinatario_tipo = 'EXTERNO' AND usuario_id IS NULL AND apoderado_id IS NULL AND familia_id IS NULL
        AND tipo IN ('HUELLA_BITACORA', 'RESUMEN_DIARIO', 'CONECTOR_VIGENTE', 'COLEGIO_EN_PRODUCCION') AND canal = 'CORREO'));
