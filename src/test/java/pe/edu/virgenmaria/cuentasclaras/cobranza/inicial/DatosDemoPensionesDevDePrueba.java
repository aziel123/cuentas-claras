package pe.edu.virgenmaria.cuentasclaras.cobranza.inicial;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioSaldoInicial;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PersonasDemoDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.PersonaDemo;

import java.time.Clock;

/** Las pensiones de demostración (perfil dev) armadas a mano para otras pruebas, por ejemplo la caja de demostración. */
@TestComponent
public class DatosDemoPensionesDevDePrueba {

	private final DatosDemoPensionesDev demo;

	private final UsuarioRepository usuarios;

	private final PasswordEncoder codificador;

	private final JdbcTemplate jdbc;

	public DatosDemoPensionesDevDePrueba(ServicioPlanesPension planes, ServicioSaldoInicial saldoInicial, Clock reloj,
			PersonaDemo personaDemo, UsuarioRepository usuarios, PasswordEncoder codificador, JdbcTemplate jdbc) {
		this.demo = new DatosDemoPensionesDev(planes, saldoInicial, reloj, personaDemo, "jdbc:h2:mem:demo", true);
		this.usuarios = usuarios;
		this.codificador = codificador;
		this.jdbc = jdbc;
	}

	/** Crea las personas de la demo que aprueban los planes (si faltan) y las pensiones de demostración. */
	public boolean crear() {
		PersonasDemoDePrueba.asegurar(usuarios, codificador, jdbc, "administracion", "director");
		return demo.crearSiCorresponde();
	}
}
