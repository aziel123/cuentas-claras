package pe.edu.virgenmaria.cuentasclaras.seguridad.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * Sprint 7, tanda 3 (auditoría web, «Enumeración de usuarios»): un usuario que existe y uno que no responden lo mismo
 * (mismo mensaje y misma redirección) y tardan parecido, porque para el que no existe se compara la clave contra un hash
 * señuelo (BCrypt con el mismo costo). El margen es amplio: sin el señuelo, el inexistente tardaría 50 veces menos.
 */
@PruebaIntegracion
class EnumeracionUsuariosTest {

	private static final int INTENTOS = 7;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		UsuariosDePrueba.guardar(usuarios, codificador, 1L, "promotora", UsuariosDePrueba.CLAVE, false, Rol.PROMOTOR);
	}

	@AfterEach
	void limpiar() {
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void unUsuarioQueNoExisteRespondeIgualYTardaParecido() throws Exception {
		// Calentamiento (carga de clases y del codificador).
		intentar("promotora", 1);
		intentar("nadie.inventado", 1);
		long[] existe = new long[INTENTOS];
		long[] noExiste = new long[INTENTOS];
		for (int i = 0; i < INTENTOS; i++) {
			// Alternados, para que una pausa del equipo afecte a los dos por igual. La cuenta no llega al bloqueo (5):
			// el ingreso correcto reinicia el contador cada 4 intentos.
			existe[i] = intentar("promotora", 1);
			noExiste[i] = intentar("nadie.inventado." + i, 1);
			if (i % 3 == 2) {
				mvc.perform(post("/login").param("usuario", "promotora").param("clave", UsuariosDePrueba.CLAVE).with(csrf()))
						.andExpect(redirectedUrl("/inicio"));
			}
		}
		long medianaExiste = mediana(existe);
		long medianaNoExiste = mediana(noExiste);
		assertThat(medianaNoExiste).as("el usuario inexistente también pasa por BCrypt (señuelo): %d ns contra %d ns",
				medianaNoExiste, medianaExiste).isGreaterThan(medianaExiste / 4);
	}

	private long intentar(String usuario, int veces) throws Exception {
		long inicio = System.nanoTime();
		for (int i = 0; i < veces; i++) {
			mvc.perform(post("/login").param("usuario", usuario).param("clave", "no es la clave de nadie").with(csrf()))
					.andExpect(redirectedUrl("/login?error"));
		}
		return System.nanoTime() - inicio;
	}

	private static long mediana(long[] valores) {
		long[] ordenados = valores.clone();
		Arrays.sort(ordenados);
		return ordenados[ordenados.length / 2];
	}
}
