package pe.edu.virgenmaria.cuentasclaras.conectores.service;

import pe.edu.virgenmaria.cuentasclaras.conectores.dto.ConectorVigente;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ProveedorConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.TipoConector;

/**
 * Sprint 8, tanda 2 (sección 3.6): puerto que implementa el módulo que USA el conector (comprobantes para el OSE, pasarela
 * para el pago en línea, recaudación para la recaudación; la cuenta bancaria la valida {@code conectores}). Así
 * {@code conectores} no depende de ellos.
 * <ul>
 *   <li>{@link #probar}: «Probar conexión» de un conector PROPUESTO, ya descifrado. Sin la prueba correcta no entra en
 *       vigencia (la base lo exige: {@code ck_conector_colegio_prueba}). El detalle no lleva secretos.</li>
 *   <li>{@link #validarAntesDeVigencia}: lo que el módulo exige justo antes de aprobar (por ejemplo, que las series de un
 *       OSE real no se hayan usado con el simulado en ese colegio). Lanza {@code ReglaNegocioException}.</li>
 * </ul>
 */
public interface AdaptadorConector {

	TipoConector tipo();

	/** Si este adaptador sabe probar ese proveedor (los reales sin adaptador no pasan la prueba). */
	boolean admite(ProveedorConector proveedor);

	ResultadoPrueba probar(ConectorVigente candidato);

	default void validarAntesDeVigencia(ConectorVigente candidato) {
		// nada que validar
	}

	/**
	 * Correcciones del sprint 8 (QA-S8-6): lo que el módulo exige antes de aprobar y necesita ver OTROS colegios (por
	 * ejemplo, las series de las otras sedes con el mismo RUC). Corre sin transacción abierta, antes de la de la aprobación.
	 */
	default void validarFueraDeTransaccion(ConectorVigente candidato) {
		// nada que validar
	}

	/** El resultado de una prueba de conexión: sin secretos ni datos personales en el detalle. */
	record ResultadoPrueba(boolean ok, String detalle) {

		public static ResultadoPrueba correcta(String detalle) {
			return new ResultadoPrueba(true, detalle);
		}

		public static ResultadoPrueba fallida(String detalle) {
			return new ResultadoPrueba(false, detalle);
		}
	}
}
