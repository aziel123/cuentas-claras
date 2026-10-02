package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

/**
 * Resultado de verificar la cadena de auditoría.
 *
 * @param integra            {@code true} si la cadena está completa y ningún hash fue alterado
 * @param eventosRevisados   cuántos eventos se revisaron
 * @param secuenciaConProblema primera secuencia donde se encontró el problema, o {@code null}
 * @param detalle            descripción en español del resultado
 */
public record ResultadoVerificacion(boolean integra, long eventosRevisados, Long secuenciaConProblema,
		String detalle) {

	static ResultadoVerificacion integra(long eventosRevisados) {
		return new ResultadoVerificacion(true, eventosRevisados, null,
				"La bitácora está íntegra: " + eventosRevisados + " eventos verificados.");
	}

	static ResultadoVerificacion alterada(long eventosRevisados, Long secuencia, String detalle) {
		return new ResultadoVerificacion(false, eventosRevisados, secuencia, detalle);
	}
}
