package pe.edu.virgenmaria.cuentasclaras;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import pe.edu.virgenmaria.cuentasclaras.comun.config.VerificadorConfiguracion;
import pe.edu.virgenmaria.cuentasclaras.comun.migracion.MigradorBaseDatos;

@SpringBootApplication
public class CuentasClarasApplication {

	/**
	 * {@code java -jar cuentas-claras.jar migrar}: solo aplica las migraciones (usuario cc_migrador) y termina.
	 * Sin argumentos: levanta la aplicación (indica el perfil con SPRING_PROFILES_ACTIVE).
	 */
	public static void main(String[] args) {
		if (args.length > 0 && MigradorBaseDatos.ARGUMENTO.equals(args[0])) {
			System.exit(MigradorBaseDatos.ejecutarDesdeEntorno(System.getenv()));
		}
		SpringApplication aplicacion = new SpringApplication(CuentasClarasApplication.class);
		aplicacion.addListeners(new VerificadorConfiguracion.AlPrepararEntorno());
		aplicacion.run(args);
	}
}
