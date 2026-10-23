package pe.edu.virgenmaria.cuentasclaras.comprobantes.inicial;

import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.proceso.ReintentosComprobantes;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.TareasDelDiaDev;

/**
 * Rediseño (datos con historia, solo {@code dev}): las boletas de cada día simulado del historial van al OSE (simulado)
 * dentro del plazo legal, como lo haría la tarea programada. Una pasada envía hasta {@code EnvioComprobantes.LOTE} por
 * colegio; el historial cobra menos que eso por día.
 */
@Component
@Profile("dev")
@Order(1)
public class BoletasDelDiaDev implements TareasDelDiaDev {

	private final ReintentosComprobantes reintentos;

	public BoletasDelDiaDev(ReintentosComprobantes reintentos) {
		this.reintentos = reintentos;
	}

	@Override
	public void pasar(long colegioId) {
		reintentos.reintentar();
	}
}
