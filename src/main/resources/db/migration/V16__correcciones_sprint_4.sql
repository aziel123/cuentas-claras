-- Sprint 4 · correcciones tras la auditoría antifraude y QA (docs/arquitectura/sprint-4-correcciones.md).
-- No se editan V13–V15: todo cambio de esquema va aquí. Compatible con MySQL 8.0.19+ y con H2 en modo MySQL.

-- S4-C1. Una pareja que confirma una persona (SUGERIDA o MANUAL) es por el MISMO monto: solo la liquidación de la
-- pasarela admite su tolerancia configurable (la EXACTA ya exigía diferencia 0). Así nadie «tapa» un Yape inventado
-- con un abono cualquiera de otro monto. La MANUAL, además, pasa por la bandeja (trigger: solicitud PARTIDA_MANUAL
-- aprobada por otra persona antes de confirmarse).
ALTER TABLE partida_conciliacion ADD CONSTRAINT ck_partida_conciliacion_monto_exacto CHECK (
    diferencia = 0 OR objeto_tipo = 'LIQUIDACION');
