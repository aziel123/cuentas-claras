package pe.edu.virgenmaria.cuentasclaras.caja.service;

import java.util.Collection;
import java.util.List;

/**
 * Puerto (sprint 4): avisos de pagos en línea en curso para estas cuotas, que caja muestra antes de cobrar («Hay un pago
 * en línea iniciado hace 4 min para Pensión octubre»). Lo implementa el módulo de pagos en línea: caja no depende de él.
 * No bloquea el cobro (decisión 7): si la pasarela confirma después, la orden queda por revisar.
 */
public interface PagosEnCurso {

	List<String> avisos(Collection<Long> cuotaIds);
}
