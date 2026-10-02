package pe.edu.virgenmaria.cuentasclaras.comun.alertas;

import java.util.List;

/**
 * Puerto: alertas para la tarjeta «Para revisar» de Promotoría. Cada módulo aporta las suyas (por ejemplo, cobranza:
 * matrículas sin cronograma o retiradas sin cuotas) sin que el inicio dependa de él. Se llaman con un usuario de
 * Promotoría en sesión y deben ser consultas de solo lectura y baratas.
 */
public interface AlertasRevision {

	List<String> alertas();
}
