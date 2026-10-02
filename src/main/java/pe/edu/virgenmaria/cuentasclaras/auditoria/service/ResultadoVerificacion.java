package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import java.io.Serializable;

/**
 * Resultado de verificar la cadena de auditoría, tal como lo ve el colegio que lo pidió.
 *
 * @param integra              {@code true} si la cadena está completa, ningún hash fue alterado y la huella anotada sigue en ella
 * @param eventosRevisados     eventos del colegio revisados (la cadena es global; no se informa el total de la plataforma)
 * @param secuenciaConProblema primer evento con problema, solo si es del colegio que verifica; si no, {@code null}
 * @param detalle              descripción en español del resultado
 * @param huella               huella para anotar: el evento que registró esta verificación ({@code null} si no se pudo registrar)
 */
public record ResultadoVerificacion(boolean integra, long eventosRevisados, Long secuenciaConProblema, String detalle,
		HuellaBitacora huella) implements Serializable {

	static ResultadoVerificacion integra(long eventosRevisados) {
		return new ResultadoVerificacion(true, eventosRevisados, null,
				"La bitácora está íntegra: " + eventosRevisados + " eventos verificados.", null);
	}

	static ResultadoVerificacion alterada(long eventosRevisados, Long secuencia, String detalle) {
		return new ResultadoVerificacion(false, eventosRevisados, secuencia, detalle, null);
	}

	ResultadoVerificacion conHuella(HuellaBitacora nueva) {
		return new ResultadoVerificacion(integra, eventosRevisados, secuenciaConProblema, detalle, nueva);
	}
}
