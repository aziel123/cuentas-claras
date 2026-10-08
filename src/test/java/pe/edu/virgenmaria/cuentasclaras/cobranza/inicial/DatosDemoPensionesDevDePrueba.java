package pe.edu.virgenmaria.cuentasclaras.cobranza.inicial;

import org.springframework.boot.test.context.TestComponent;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioSaldoInicial;

import java.time.Clock;

/** Las pensiones de demostración (perfil dev) armadas a mano para otras pruebas, por ejemplo la caja de demostración. */
@TestComponent
public class DatosDemoPensionesDevDePrueba {

	private final DatosDemoPensionesDev demo;

	public DatosDemoPensionesDevDePrueba(ServicioPlanesPension planes, ServicioSaldoInicial saldoInicial, Clock reloj) {
		this.demo = new DatosDemoPensionesDev(planes, saldoInicial, reloj, "jdbc:h2:mem:demo", true);
	}

	public boolean crear() {
		return demo.crearSiCorresponde();
	}
}
