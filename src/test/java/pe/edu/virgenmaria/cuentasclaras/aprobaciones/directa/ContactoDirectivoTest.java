package pe.edu.virgenmaria.cuentasclaras.aprobaciones.directa;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.ContactoTitularRequest;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.AltaColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 11, tanda 1c (A1c-1): si la dueña de una cuenta directiva perdió su celular, su contacto lo cambian DOS
 * operadores de la plataforma (uno propone y otro aplica, cada uno con su firma), en un colegio que opera. Nadie del
 * colegio lo cambia solo.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ContactoDirectivoTest extends BaseDirecta {

	@Autowired
	private AltaColegio alta;

	private <T> T comoOperador(Usuario operador, Supplier<T> accion) {
		UsuariosDePrueba.iniciarSesion(operador);
		try {
			return accion.get();
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	@Test
	void dosOperadoresCambianElContactoDeLaPromotoria() {
		jdbc.update("UPDATE colegio SET correo_institucional = 'secretaria@colegio.pe' WHERE id = 1");
		Usuario uno = EscenarioPlataforma.operador(usuarios, codificador, colegios, "operador.uno");
		Usuario dos = EscenarioPlataforma.operador(usuarios, codificador, colegios, "operador.dos");
		Long cambio = comoOperador(uno, () -> alta.proponerContactoDirectivo(1L, "promotora.dir",
				new ContactoTitularRequest("987 650 777", null, "Perdió su celular: lo confirmé por videollamada con su DNI")));
		// Quien lo propuso no lo aplica.
		assertThatThrownBy(() -> comoOperador(uno, () -> alta.aplicarContactoTitular(1L, cambio)))
				.isInstanceOf(ReglaNegocioException.class);
		assertThat(jdbc.queryForObject("SELECT telefono_whatsapp FROM usuario WHERE id = ?", String.class,
				e.promotora().getId())).doesNotEndWith("987650777");

		comoOperador(dos, () -> alta.aplicarContactoTitular(1L, cambio));
		assertThat(jdbc.queryForObject("SELECT telefono_whatsapp FROM usuario WHERE id = ?", String.class,
				e.promotora().getId())).endsWith("987650777");
	}

	@Test
	void unaCuentaQueNoEsDirectivaNoVaPorEsteCamino() {
		Usuario uno = EscenarioPlataforma.operador(usuarios, codificador, colegios, "operador.uno");
		assertThatThrownBy(() -> comoOperador(uno, () -> alta.proponerContactoDirectivo(1L, "admin.dir",
				new ContactoTitularRequest("987 650 778", null, "Perdió su celular: lo confirmé por videollamada"))))
				.isInstanceOf(RuntimeException.class);
	}
}
