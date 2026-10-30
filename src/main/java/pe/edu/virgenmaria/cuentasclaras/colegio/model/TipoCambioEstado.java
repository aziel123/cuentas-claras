package pe.edu.virgenmaria.cuentasclaras.colegio.model;

import java.util.Set;

/**
 * Sprint 11, tanda 1 (secciones 3.2 a 3.5 y AP-2): los cambios que deciden dos operadores. Cada tipo tiene su origen, su
 * destino, su ámbito (un bloqueo de la facultad no espera a que se resuelva una suspensión) y sus motivos tipificados: lo
 * mismo que exige {@code ck_cambio_estado_colegio_tipo} (V39). CIERRE llega en la tanda 2.
 */
public enum TipoCambioEstado {

	BAJA("Dar de baja", EstadoColegio.PREPARACION, EstadoColegio.BAJA, AmbitoCambioEstado.ESTADO,
			Set.of(MotivoCambioEstado.PRUEBA, MotivoCambioEstado.NO_ARRANCO, MotivoCambioEstado.PEDIDO_COLEGIO,
					MotivoCambioEstado.DUPLICADO, MotivoCambioEstado.OTRO)),
	SUSPENSION("Suspender", EstadoColegio.PRODUCCION, EstadoColegio.SUSPENDIDO, AmbitoCambioEstado.ESTADO,
			Set.of(MotivoCambioEstado.FALTA_PAGO, MotivoCambioEstado.FIN_CONTRATO, MotivoCambioEstado.PEDIDO_COLEGIO,
					MotivoCambioEstado.OTRO)),
	REACTIVACION("Reactivar", EstadoColegio.SUSPENDIDO, EstadoColegio.PRODUCCION, AmbitoCambioEstado.ESTADO,
			Set.of(MotivoCambioEstado.PAGO_RECIBIDO, MotivoCambioEstado.ACUERDO, MotivoCambioEstado.OTRO)),
	/** Sprint 11, tanda 2 (sección 3.10): de SUSPENDIDO a CERRADO, con la exportación del colegio. */
	CIERRE("Cerrar", EstadoColegio.SUSPENDIDO, EstadoColegio.CERRADO, AmbitoCambioEstado.ESTADO,
			Set.of(MotivoCambioEstado.FIN_CONTRATO, MotivoCambioEstado.FALTA_PAGO, MotivoCambioEstado.PEDIDO_COLEGIO,
					MotivoCambioEstado.OTRO)),
	BLOQUEO_FACULTAD("Bloquear la facultad del titular", null, null, AmbitoCambioEstado.FACULTAD,
			Set.of(MotivoCambioEstado.SEGURIDAD, MotivoCambioEstado.PEDIDO_COLEGIO, MotivoCambioEstado.OTRO)),
	DESBLOQUEO_FACULTAD("Desbloquear la facultad del titular", null, null, AmbitoCambioEstado.FACULTAD,
			Set.of(MotivoCambioEstado.SEGURIDAD, MotivoCambioEstado.PEDIDO_COLEGIO, MotivoCambioEstado.OTRO));

	private final String etiqueta;

	private final EstadoColegio origen;

	private final EstadoColegio destino;

	private final AmbitoCambioEstado ambito;

	private final Set<MotivoCambioEstado> motivos;

	TipoCambioEstado(String etiqueta, EstadoColegio origen, EstadoColegio destino, AmbitoCambioEstado ambito,
			Set<MotivoCambioEstado> motivos) {
		this.etiqueta = etiqueta;
		this.origen = origen;
		this.destino = destino;
		this.ambito = ambito;
		this.motivos = motivos;
	}

	public String etiqueta() {
		return etiqueta;
	}

	/** El estado desde el que se propone ({@code null} en la facultad: el estado actual, preparación o producción). */
	public EstadoColegio origen() {
		return origen;
	}

	/** El estado al que lleva ({@code null} en la facultad: el mismo estado). */
	public EstadoColegio destino() {
		return destino;
	}

	public AmbitoCambioEstado ambito() {
		return ambito;
	}

	public Set<MotivoCambioEstado> motivos() {
		return motivos;
	}

	/** El titular puede objetar una baja o una suspensión (AP-10). */
	public boolean objetable() {
		return this == BAJA || this == SUSPENSION;
	}

	/** Se aplica en la misma transacción de la aprobación (todo menos la suspensión y la baja a 7 días). */
	public boolean deFacultad() {
		return ambito == AmbitoCambioEstado.FACULTAD;
	}
}
