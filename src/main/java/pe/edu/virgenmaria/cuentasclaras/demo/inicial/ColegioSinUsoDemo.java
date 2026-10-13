package pe.edu.virgenmaria.cuentasclaras.demo.inicial;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;

/**
 * Sprint 8, tanda 3 (H12 del diseño): toda base nueva trae el colegio de la migración V1, con el nombre del colegio piloto.
 * En la demo ese nombre no debe aparecer en ningún lado (por ejemplo, en la lista del operador de ventas): al arrancar se
 * llama «Colegio sin uso de la demo». Ningún visitante lo usa; cada uno tiene el suyo. Solo en el perfil {@code demo}.
 */
@Component
@Profile("demo")
public class ColegioSinUsoDemo implements ApplicationRunner {

	private static final Logger LOG = LoggerFactory.getLogger(ColegioSinUsoDemo.class);

	static final String NOMBRE = "Colegio sin uso de la demo";

	private final ColegioRepository colegios;

	private final TransactionTemplate transaccion;

	public ColegioSinUsoDemo(ColegioRepository colegios, PlatformTransactionManager transacciones) {
		this.colegios = colegios;
		this.transaccion = new TransactionTemplate(transacciones);
	}

	@Override
	public void run(ApplicationArguments argumentos) {
		transaccion.executeWithoutResult(t -> colegios.findByTipoOrderByIdAsc(TipoColegio.COLEGIO).stream().findFirst()
				.filter(c -> !NOMBRE.equals(c.getNombre())).ifPresent(c -> {
					c.corregirFicha(NOMBRE, "Sin uso (demo)", "Sin dirección (demo)", "sin-uso@colegio-demo.example");
					colegios.save(c);
					LOG.info("Demo: el colegio {} de la migración inicial queda como «{}».", c.getId(), NOMBRE);
				}));
	}
}
