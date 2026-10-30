package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import java.util.Map;

/**
 * Sprint 11, tanda 2 (3.11 y AP2-8): las actas de supresión EJECUTADAS, para que {@link VerificadorIntegridadAuditoria}
 * acepte los eventos tachados de esos colegios. Un evento tachado no se puede recalcular: se toma su {@code hash} guardado
 * como eslabón y, al final, la huella encadenada de los eventos del colegio ({@code h = SHA-256(h|id|hash)}, desde 64
 * ceros, por secuencia) debe ser la que calculó la base al ejecutar el acta. Un tachado sin acta, o con otra huella, rompe
 * la verificación.
 */
public interface SupresionesDeLaBitacora {

	/** El acta ejecutada de un colegio: la huella de sus eventos y la última secuencia que cubre. */
	record ActaEjecutada(long colegioId, long actaId, String huellaEventos, long ultimaSecuencia) {
	}

	/** Por colegio. Se llama sin transacción abierta. */
	Map<Long, ActaEjecutada> actasEjecutadas();
}
