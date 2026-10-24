-- Sprint 11 · tanda 1c (docs/arquitectura/sprint-11-ciclo-colegio.md, secciones 3.16 y 3.16b, que prevalece). La Promotoría
-- (el dueño) aplica directo lo suyo: una «solicitud directa» la pide y la resuelve ella misma en la misma transacción, con
-- su firma, su código por WhatsApp si es sensible y avisos; los conectores que mueven dinero esperan 24 horas (con freno y
-- el socio que aprueba al instante), y dar Promotoría en producción, quitarla o desactivar a otra Promotoría también
-- esperan. El código queda atado a la acción (huella) y lo que escribió la persona se guarda como SHA-256. Nada se borra.
-- Compatible con MySQL 8 y con H2 en modo MySQL (comprobado en los dos: paso 1 de la tanda).

-- 1. solicitud_cambio: la directa, su código, la espera y el freno. aplica_desde la pone SOLO la base (A1c-6: ningún
--    GRANT sobre la columna; el trigger, cuyo definidor es cc_migrador, la fija y rechaza cualquier cambio después).
ALTER TABLE solicitud_cambio ADD COLUMN directa BOOLEAN;                  -- TRUE solo si la resolvió quien la pidió
ALTER TABLE solicitud_cambio ADD COLUMN codigo_mensaje_id BIGINT;         -- el CODIGO_PROMOTORIA o el CODIGO_CONTACTO
ALTER TABLE solicitud_cambio ADD COLUMN codigo_escrito_sha256 CHAR(64);   -- SHA-256 de lo que escribió la persona
ALTER TABLE solicitud_cambio ADD COLUMN aplica_desde DATETIME(6);         -- fin de la espera (la pone la base)
ALTER TABLE solicitud_cambio ADD COLUMN frenado_por VARCHAR(60);
ALTER TABLE solicitud_cambio ADD COLUMN frenado_en DATETIME(6);           -- lo pone la base
ALTER TABLE solicitud_cambio ADD COLUMN motivo_freno VARCHAR(500);
ALTER TABLE solicitud_cambio ADD CONSTRAINT uk_solicitud_cambio_codigo UNIQUE (codigo_mensaje_id);
ALTER TABLE solicitud_cambio ADD CONSTRAINT fk_solicitud_cambio_codigo FOREIGN KEY (codigo_mensaje_id, colegio_id)
    REFERENCES mensaje (id, colegio_id);
-- Se reemplaza el CHECK de V8 (copiando su lista): la resolución por quien la pidió vale solo con directa.
ALTER TABLE solicitud_cambio DROP CONSTRAINT ck_solicitud_cambio_resolucion;
ALTER TABLE solicitud_cambio ADD CONSTRAINT ck_solicitud_cambio_resolucion CHECK (
    (estado = 'PENDIENTE' AND pendiente IS NOT NULL AND pendiente = TRUE AND resuelto_por IS NULL AND resuelto_en IS NULL)
    OR (estado IN ('APROBADA', 'RECHAZADA') AND pendiente IS NULL AND resuelto_por IS NOT NULL AND resuelto_en IS NOT NULL
        AND (resuelto_por <> solicitado_por OR (directa IS NOT NULL AND directa AND estado = 'APROBADA'))));
ALTER TABLE solicitud_cambio ADD CONSTRAINT ck_solicitud_cambio_directa CHECK (directa IS NULL
    OR (directa AND estado = 'APROBADA' AND resuelto_por IS NOT NULL AND resuelto_por = solicitado_por));
ALTER TABLE solicitud_cambio ADD CONSTRAINT ck_solicitud_cambio_codigo CHECK (
    (codigo_mensaje_id IS NULL AND codigo_escrito_sha256 IS NULL)
    OR (codigo_mensaje_id IS NOT NULL AND codigo_escrito_sha256 IS NOT NULL AND CHAR_LENGTH(codigo_escrito_sha256) = 64));
ALTER TABLE solicitud_cambio ADD CONSTRAINT ck_solicitud_cambio_espera CHECK (aplica_desde IS NULL
    OR (directa IS NOT NULL AND directa));
ALTER TABLE solicitud_cambio ADD CONSTRAINT ck_solicitud_cambio_freno CHECK (
    (frenado_por IS NULL AND frenado_en IS NULL AND motivo_freno IS NULL)
    OR (frenado_por IS NOT NULL AND frenado_en IS NOT NULL AND aplica_desde IS NOT NULL AND motivo_freno IS NOT NULL
        AND CHAR_LENGTH(TRIM(motivo_freno)) >= 20));

-- 2. conector_colegio: la aprobación directa de la Promotoría, la espera de 24 horas, el freno y el código del socio que
--    aprueba al instante. Se reemplazan tres CHECK de V29 copiando sus listas.
ALTER TABLE conector_colegio ADD COLUMN directa BOOLEAN;                  -- lo aprobó directo una Promotoría
ALTER TABLE conector_colegio ADD COLUMN aplica_desde DATETIME(6);         -- fin de la espera (la pone la base)
ALTER TABLE conector_colegio ADD COLUMN frenado_por VARCHAR(60);
ALTER TABLE conector_colegio ADD COLUMN frenado_en DATETIME(6);           -- lo pone la base
ALTER TABLE conector_colegio ADD COLUMN motivo_freno VARCHAR(500);
ALTER TABLE conector_colegio ADD COLUMN codigo_mensaje_id BIGINT;         -- el código del socio que aprueba al instante
ALTER TABLE conector_colegio ADD COLUMN codigo_escrito_sha256 CHAR(64);
ALTER TABLE conector_colegio ADD CONSTRAINT uk_conector_colegio_codigo UNIQUE (codigo_mensaje_id);
ALTER TABLE conector_colegio ADD CONSTRAINT fk_conector_colegio_codigo FOREIGN KEY (codigo_mensaje_id, colegio_id)
    REFERENCES mensaje (id, colegio_id);
ALTER TABLE conector_colegio DROP CONSTRAINT ck_conector_colegio_estado;
ALTER TABLE conector_colegio ADD CONSTRAINT ck_conector_colegio_estado CHECK (estado IN ('PROPUESTO', 'VIGENTE', 'RECHAZADO',
    'REEMPLAZADO', 'EN_ESPERA', 'FRENADO')
    AND ((estado = 'VIGENTE' AND vigente) OR (estado <> 'VIGENTE' AND vigente IS NULL)));
ALTER TABLE conector_colegio DROP CONSTRAINT ck_conector_colegio_resolucion;
ALTER TABLE conector_colegio ADD CONSTRAINT ck_conector_colegio_resolucion CHECK ((estado = 'PROPUESTO' AND resuelto_por IS NULL)
    OR (estado = 'REEMPLAZADO') OR (estado IN ('VIGENTE', 'RECHAZADO') AND resuelto_por IS NOT NULL
        AND resuelto_en IS NOT NULL)
    OR (estado = 'EN_ESPERA' AND aplica_desde IS NOT NULL AND resuelto_por IS NOT NULL AND resuelto_en IS NOT NULL)
    OR (estado = 'FRENADO' AND aplica_desde IS NOT NULL AND frenado_por IS NOT NULL AND frenado_en IS NOT NULL
        AND motivo_freno IS NOT NULL AND CHAR_LENGTH(TRIM(motivo_freno)) >= 20));
ALTER TABLE conector_colegio DROP CONSTRAINT ck_conector_colegio_actor;
ALTER TABLE conector_colegio ADD CONSTRAINT ck_conector_colegio_actor CHECK (creado_por = propuesto_por
    AND creado_por NOT LIKE 'sistema%'
    AND (resuelto_por IS NULL OR estado NOT IN ('VIGENTE', 'EN_ESPERA') OR resuelto_por <> propuesto_por
        OR (directa IS NOT NULL AND directa)));
ALTER TABLE conector_colegio ADD CONSTRAINT ck_conector_colegio_directa CHECK ((directa IS NULL OR directa)
    AND (aplica_desde IS NULL OR (directa IS NOT NULL AND directa)));
ALTER TABLE conector_colegio ADD CONSTRAINT ck_conector_colegio_freno CHECK (
    (frenado_por IS NULL AND frenado_en IS NULL AND motivo_freno IS NULL)
    OR (estado = 'FRENADO' AND frenado_por IS NOT NULL));
ALTER TABLE conector_colegio ADD CONSTRAINT ck_conector_colegio_codigo CHECK (
    (codigo_mensaje_id IS NULL AND codigo_escrito_sha256 IS NULL)
    OR (codigo_mensaje_id IS NOT NULL AND codigo_escrito_sha256 IS NOT NULL AND CHAR_LENGTH(codigo_escrito_sha256) = 64));

-- 3. mensaje: el código de la Promotoría (y el de la confirmación del contacto anterior, A1c-1) guarda solo la SHA-256 del
--    código y la huella de la acción que autoriza (A1c-5). CODIGO_FACULTAD queda como alias aceptado.
ALTER TABLE mensaje ADD COLUMN codigo_sha256 CHAR(64);
ALTER TABLE mensaje ADD COLUMN huella_accion CHAR(64);
ALTER TABLE mensaje DROP CONSTRAINT ck_mensaje_tipo;
ALTER TABLE mensaje ADD CONSTRAINT ck_mensaje_tipo CHECK (tipo IN ('PAGO_REGISTRADO', 'PAGO_ANULADO',
    'DESCUENTO_APROBADO', 'CONTACTO_CAMBIADO', 'ACTIVACION_CUENTA', 'HUELLA_BITACORA', 'RECORDATORIO_VENCIMIENTO',
    'CUOTA_VENCIDA', 'RENOVACION_MATRICULA', 'RENOVACION_REGISTRADA', 'AVISO_ATENDIDO', 'VERIFICACION_CONTACTO',
    'APODERADO_AGREGADO', 'CONTACTO_POR_VERIFICAR', 'FERIADO_PROPUESTO', 'RESUMEN_DIARIO', 'ALERTA_PROMOTORIA',
    'CONECTOR_VIGENTE', 'COLEGIO_EN_PRODUCCION', 'AVISO_ALTA', 'CORREO_CONTROL', 'ASISTENCIA_DIA',
    'ASISTENCIA_CORREGIDA', 'COMUNICADOS_RESUMEN', 'COMUNICADO_URGENTE', 'LIBRETA_DISPONIBLE', 'LIBRETA_CORREGIDA',
    'CODIGO_FACULTAD', 'CODIGO_PROMOTORIA', 'CODIGO_CONTACTO'));
ALTER TABLE mensaje ADD CONSTRAINT ck_mensaje_codigo CHECK (
    (tipo IN ('CODIGO_PROMOTORIA', 'CODIGO_CONTACTO') AND codigo_sha256 IS NOT NULL AND CHAR_LENGTH(codigo_sha256) = 64
        AND huella_accion IS NOT NULL AND CHAR_LENGTH(huella_accion) = 64)
    OR (tipo NOT IN ('CODIGO_PROMOTORIA', 'CODIGO_CONTACTO') AND codigo_sha256 IS NULL AND huella_accion IS NULL));

-- 4. usuario_rol.desde (A1c-2): desde cuándo la cuenta tiene el rol (la antigüedad para frenar y aprobar al instante). La
--    pone la base al dar el rol (trg_usuario_rol_alta, reloj real); las filas existentes, con usuario.creado_en.
ALTER TABLE usuario_rol ADD COLUMN desde DATETIME(6);
UPDATE usuario_rol SET desde = (SELECT u.creado_en FROM usuario u WHERE u.id = usuario_rol.usuario_id);
ALTER TABLE usuario_rol MODIFY desde DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

-- 5. uso_facultad_titular: lo que escribió el titular (desviación del diseño: así la base compara el código también en la
--    facultad, con la misma función que en la directa). Las filas anteriores a V40 quedan sin él.
ALTER TABLE uso_facultad_titular ADD COLUMN codigo_escrito_sha256 CHAR(64);

-- 6. configuracion_bd: la espera de las directas que mueven dinero o apartan a una Promotoría (24 horas). Solo la escribe
--    el DBA; el verificador de prod exige 24. Las pruebas de MySQL la bajan como DBA (nunca cambian aplica_desde).
INSERT INTO configuracion_bd (clave, valor, creado_en) VALUES ('espera_directa_horas', '24', CURRENT_TIMESTAMP(6));
