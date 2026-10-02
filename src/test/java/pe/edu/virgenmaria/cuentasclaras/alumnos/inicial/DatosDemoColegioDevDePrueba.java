package pe.edu.virgenmaria.cuentasclaras.alumnos.inicial;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.PlatformTransactionManager;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.RegistroAlumnos;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;

import java.time.Clock;

/**
 * Los datos de demostración del colegio (perfil dev) armados a mano para otras pruebas, por ejemplo las de las
 * pensiones de demostración, que se crean encima.
 */
@TestComponent
public class DatosDemoColegioDevDePrueba {

	private final DatosDemoColegioDev demo;

	public DatosDemoColegioDevDePrueba(ColegioRepository colegios, AnioEscolarRepository anios,
			SeccionRepository secciones, RegistroAlumnos registro, AuditoriaService auditoria,
			PlatformTransactionManager transacciones, Clock reloj) {
		this.demo = new DatosDemoColegioDev(colegios, anios, secciones, registro, auditoria, transacciones, reloj,
				"jdbc:h2:mem:demo", true);
	}

	public boolean crear() {
		return demo.crearSiCorresponde();
	}
}
