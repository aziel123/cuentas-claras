package pe.edu.virgenmaria.cuentasclaras.colegio.service;

import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.util.Map;
import java.util.Optional;

/**
 * Sprint 11, tanda 1b (sección 3.14, punto 3): se intentó algo que el colegio en PREPARACION no permite. La lanza
 * {@link ColegioService#exigirEnProduccion(Long, AccionBloqueada)} y se reconoce también en el mensaje de la base (los 1644
 * de preparación de {@code trg_pago_registro}, {@code trg_orden_pago_nace} y {@code trg_mensaje_nace}). La muestra
 * {@code PreparacionAdvice} (409, con el enlace a la Puesta en marcha para el personal; a las familias, sin enlace).
 */
public class ColegioEnPreparacionException extends ReglaNegocioException {

	/** Los textos de los triggers de preparación (scripts/mysql/03-triggers.sql), sin tildes como en la base. */
	static final Map<String, AccionBloqueada> MENSAJES_DE_LA_BASE = Map.of(
			"el colegio todavia no esta en produccion: no se registran pagos", AccionBloqueada.COBRAR,
			"el colegio todavia no esta en produccion: sin pagos en linea", AccionBloqueada.PAGO_EN_LINEA,
			"el colegio todavia no esta en produccion: sin recordatorios de cobro ni mensajes a las familias",
			AccionBloqueada.COMUNICADO);

	private final AccionBloqueada accion;

	public ColegioEnPreparacionException(AccionBloqueada accion) {
		super(accion.mensaje());
		this.accion = accion;
	}

	/** El mismo aviso con otro texto (por ejemplo, el del portal de las familias). */
	public ColegioEnPreparacionException(AccionBloqueada accion, String mensaje) {
		super(mensaje);
		this.accion = accion;
	}

	public AccionBloqueada accion() {
		return accion;
	}

	/** Si el error (o su causa) es un 1644 de preparación de la base, la excepción equivalente. */
	public static Optional<ColegioEnPreparacionException> desdeBase(Throwable error) {
		for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
			String mensaje = t.getMessage();
			if (mensaje == null) {
				continue;
			}
			for (Map.Entry<String, AccionBloqueada> e : MENSAJES_DE_LA_BASE.entrySet()) {
				if (mensaje.contains(e.getKey())) {
					return Optional.of(new ColegioEnPreparacionException(e.getValue()));
				}
			}
		}
		return Optional.empty();
	}
}
