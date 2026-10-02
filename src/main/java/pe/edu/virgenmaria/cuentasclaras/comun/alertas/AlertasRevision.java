package pe.edu.virgenmaria.cuentasclaras.comun.alertas;

import java.util.List;

/**
 * Puerto: alertas para la tarjeta «Para revisar» de Promotoría. Cada módulo aporta las suyas (cobranza: matrículas sin
 * cronograma; caja: faltantes, cajas sin cerrar, pagos sin verificar...) sin que el inicio dependa de él. Se llaman con
 * un usuario de Promotoría en sesión y deben ser consultas de solo lectura y baratas (no hay tareas programadas: todo
 * se calcula al consultar). El inicio las ordena por gravedad: las críticas primero.
 */
public interface AlertasRevision {

	List<AlertaRevision> alertas();
}
