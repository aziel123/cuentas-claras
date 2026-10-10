-- Sprint 7 · tanda 3 (docs/arquitectura/sprint-7-endurecimiento.md, secciones 5 y 8). Ley 29733 de Protección de Datos
-- Personales: quién ve los datos personales (de solo inserción; fuera de la cadena HMAC de la bitácora por volumen, lo
-- protege el GRANT: cc_app no lo edita ni lo borra) y los pedidos sobre datos personales (acceso, rectificación,
-- cancelación u oposición) por el mismo canal de «¿Algo no cuadra?». Nada se borra.

-- Una fila por pantalla con datos personales que vio una persona del personal (las familias no se registran cuando ven
-- los datos de su propia familia). Sin el texto buscado ni los datos mostrados: solo quién, qué pantalla, de qué familia
-- o alumno (o cuántas filas, en búsquedas y listas), desde qué sesión y cuándo.
CREATE TABLE acceso_dato_personal (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id      BIGINT        NOT NULL,
    usuario_id      BIGINT        NOT NULL,
    sesion_id       BIGINT,
    tipo            VARCHAR(30)   NOT NULL,
    familia_id      BIGINT,
    alumno_id       BIGINT,
    cantidad        INT           NOT NULL DEFAULT 1,
    ip              VARCHAR(45),
    creado_en       DATETIME(6)   NOT NULL,
    creado_por      VARCHAR(60)   NOT NULL,
    actualizado_en  DATETIME(6)   NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT fk_acceso_dato_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_acceso_dato_usuario FOREIGN KEY (usuario_id, colegio_id) REFERENCES usuario (id, colegio_id),
    CONSTRAINT fk_acceso_dato_sesion FOREIGN KEY (sesion_id, colegio_id) REFERENCES sesion_usuario (id, colegio_id),
    CONSTRAINT fk_acceso_dato_familia FOREIGN KEY (familia_id, colegio_id) REFERENCES familia (id, colegio_id),
    CONSTRAINT fk_acceso_dato_alumno FOREIGN KEY (alumno_id, colegio_id) REFERENCES alumno (id, colegio_id),
    CONSTRAINT ck_acceso_dato_tipo CHECK (tipo IN ('FICHA_FAMILIA', 'FICHA_ALUMNO', 'BUSQUEDA', 'MOROSOS',
        'LLAMADA_CONTROL', 'IMPORTACION', 'APROBACION_CONTACTO')),
    CONSTRAINT ck_acceso_dato_cantidad CHECK (cantidad >= 0),
    -- Las fichas nombran a su familia o alumno; las búsquedas y listas, solo cuántas filas mostraron.
    CONSTRAINT ck_acceso_dato_objeto CHECK (
        (tipo IN ('FICHA_FAMILIA', 'APROBACION_CONTACTO', 'LLAMADA_CONTROL') AND familia_id IS NOT NULL)
        OR (tipo = 'FICHA_ALUMNO' AND alumno_id IS NOT NULL)
        OR tipo IN ('BUSQUEDA', 'MOROSOS', 'IMPORTACION')),
    CONSTRAINT ck_acceso_dato_actor CHECK (creado_por NOT LIKE 'sistema%')
);
CREATE INDEX ix_acceso_dato_familia ON acceso_dato_personal (colegio_id, familia_id, creado_en);
CREATE INDEX ix_acceso_dato_usuario ON acceso_dato_personal (colegio_id, usuario_id, creado_en);
CREATE INDEX ix_acceso_dato_fecha ON acceso_dato_personal (colegio_id, creado_en);

-- H14: pedidos sobre datos personales (derechos de acceso, rectificación, cancelación y oposición) por «¿Algo no cuadra?».
-- El derecho no cambia (no está en el GRANT de UPDATE: 1143) y solo lo lleva ese tipo de aviso.
ALTER TABLE aviso_familia DROP CONSTRAINT ck_aviso_familia_tipo;
ALTER TABLE aviso_familia ADD CONSTRAINT ck_aviso_familia_tipo CHECK (tipo IN ('PAGUE_Y_NO_APARECE',
    'NO_RECONOZCO_PAGO', 'NO_RECONOZCO_ANULACION_O_DESCUENTO', 'OTRO', 'DATOS_PERSONALES'));
ALTER TABLE aviso_familia ADD COLUMN derecho VARCHAR(20);
ALTER TABLE aviso_familia ADD CONSTRAINT ck_aviso_familia_derecho CHECK (
    (tipo = 'DATOS_PERSONALES' AND derecho IS NOT NULL
        AND derecho IN ('ACCESO', 'RECTIFICACION', 'CANCELACION', 'OPOSICION')
        AND pago_id IS NULL AND cuota_id IS NULL)
    OR (tipo <> 'DATOS_PERSONALES' AND derecho IS NULL));
