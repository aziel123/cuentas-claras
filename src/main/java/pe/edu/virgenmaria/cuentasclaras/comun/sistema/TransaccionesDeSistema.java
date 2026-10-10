package pe.edu.virgenmaria.cuentasclaras.comun.sistema;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Registra el manejador de transacciones con el que {@link EjecucionComoSistema} abre la transacción PROPIA de un actor
 * de sistema cuando lo llaman con una transacción activa (sprint 7, tanda 2; H3). Siempre activo (también sin tareas
 * programadas, como en las pruebas).
 */
@Component
class TransaccionesDeSistema implements InitializingBean {

	private final PlatformTransactionManager transacciones;

	TransaccionesDeSistema(PlatformTransactionManager transacciones) {
		this.transacciones = transacciones;
	}

	@Override
	public void afterPropertiesSet() {
		EjecucionComoSistema.usarTransacciones(transacciones);
	}
}
