package pe.edu.virgenmaria.cuentasclaras.comun.alertas;

import java.util.List;

/**
 * Puerto: alertas para la tarjeta «Para revisar» de Promotoría. Cada módulo aporta las suyas (cobranza: matrículas sin
 * cronograma; caja: faltantes, cajas sin cerrar, pagos sin verificar...) sin que el inicio dependa de él. Deben ser
 * consultas de solo lectura y baratas: se calculan al consultar. El inicio las ordena por gravedad: las críticas primero.
 * <p>
 * Sprint 6, tanda 2: las llaman Promotoría en sesión y el actor {@code sistema.panel} (resumen diario y avisos al
 * celular), así que su {@code @PreAuthorize} es {@code hasAnyRole('PROMOTOR','SISTEMA_PANEL')} (lo exige
 * {@code ReglasArquitecturaTest}). Una implementación que dependa de la persona en sesión debe devolver
 * {@code difundible() == false}: queda fuera del resumen y del aviso al celular.
 */
public interface AlertasRevision {

	List<AlertaRevision> alertas();

	/** Si sus alertas pueden calcularse sin una persona en sesión (como {@code sistema.panel}). */
	default boolean difundible() {
		return true;
	}
}
