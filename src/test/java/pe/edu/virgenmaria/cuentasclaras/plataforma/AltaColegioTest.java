package pe.edu.virgenmaria.cuentasclaras.plataforma;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.TokenDeSesionDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.ColegioCreado;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.ContactoTitularRequest;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.FichaColegioRequest;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.AltaColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.identidad.AltaTitular.DatosTitular;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 8, tanda 1 (sección 3.3; E3, E29 y auditoría): el operador da de alta un colegio sin tocar la base. Nace en
 * preparación, con su primera Promotoría (una sola vez), el enlace al titular, el año escolar inicial y la firma del
 * operador; todo queda en la bitácora de la plataforma y en la del colegio nuevo.
 */
@PruebaIntegracion
class AltaColegioTest {

	@Autowired
	private AltaColegio alta;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TokenDeSesionDePrueba tokens;

	private Usuario operador;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		tokens.olvidar();
		operador = EscenarioPlataforma.operador(usuarios, codificador, colegios, "operador.alta");
		UsuariosDePrueba.iniciarSesion(operador);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		tokens.olvidar();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void elAltaCreaElColegioEnPreparacionConSuTitularYSuEnlaceSinAnio() {
		ColegioCreado creado = alta.crear(EscenarioPlataforma.alta("Colegio San Martín", EscenarioPlataforma.RUC_A,
				"titular.sanmartin", "45678912", "987650001"), null, null);

		assertThat(creado.completo()).as(creado.pendiente()).isTrue();
		assertThat(creado.titularCanal()).isEqualTo("WhatsApp");
		assertThat(creado.titularDestino()).doesNotContain("987650001");
		Map<String, Object> colegio = jdbc.queryForMap("SELECT * FROM colegio WHERE id = ?", creado.colegioId());
		assertThat(colegio).containsEntry("tipo", "COLEGIO").containsEntry("estado", "PREPARACION")
				.containsEntry("ruc", EscenarioPlataforma.RUC_A).containsEntry("creado_por", "operador.alta")
				.containsEntry("titular_documento", "45678912").containsEntry("niveles", "PRIMARIA,SECUNDARIA");
		// La cuenta del titular: única en el colegio, PROMOTOR, con la clave por cambiar (solo entra con su enlace).
		List<Map<String, Object>> cuentas = jdbc.queryForList("SELECT u.nombre_usuario, u.debe_cambiar_clave, r.rol "
				+ "FROM usuario u JOIN usuario_rol r ON r.usuario_id = u.id WHERE u.colegio_id = ?", creado.colegioId());
		assertThat(cuentas).hasSize(1);
		assertThat(cuentas.getFirst()).containsEntry("nombre_usuario", "titular.sanmartin").containsEntry("rol", "PROMOTOR")
				.containsEntry("debe_cambiar_clave", true);
		// La marca de la primera Promotoría nombra al operador y su cuenta en la plataforma.
		assertThat(jdbc.queryForMap("SELECT operador_id, plataforma_id, creado_por FROM primera_promotoria WHERE colegio_id = ?",
				creado.colegioId())).containsEntry("operador_id", operador.getId())
				.containsEntry("plataforma_id", EscenarioPlataforma.plataformaId(colegios))
				.containsEntry("creado_por", "operador.alta");
		// E29: la firma es del operador, en el colegio de plataforma, con la clave de ESTE colegio.
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM firma_operacion WHERE colegio_id = ? AND usuario_id = ? AND clave = ?",
				Long.class, EscenarioPlataforma.plataformaId(colegios), operador.getId(),
				"primera_promotoria:" + creado.colegioId() + ":1")).isEqualTo(1L);
		// El enlace va en un mensaje del colegio nuevo, al celular del titular, con el nombre del colegio delante.
		Map<String, Object> mensaje = jdbc.queryForMap("SELECT tipo, destino, parametros, estado FROM mensaje WHERE colegio_id = ?",
				creado.colegioId());
		assertThat(mensaje).containsEntry("tipo", "ACTIVACION_CUENTA").containsEntry("estado", "PENDIENTE")
				.containsEntry("destino", "+51987650001");
		assertThat((String) mensaje.get("parametros")).startsWith("Colegio San Martín");
		// Sprint 11, tanda 1 (sección 3.7): el colegio nace SIN año escolar (lo crea su Promotoría o su Dirección).
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM anio_escolar WHERE colegio_id = ?", Long.class,
				creado.colegioId())).isZero();
		// Auditoría: en la bitácora de la plataforma (el alta) y en la del colegio (quién creó el colegio y la cuenta).
		assertThat(jdbc.queryForList("SELECT accion FROM evento_auditoria WHERE colegio_id = ?", String.class,
				EscenarioPlataforma.plataformaId(colegios))).contains("COLEGIO_CREADO");
		List<Map<String, Object>> delColegio = jdbc.queryForList("SELECT accion, nombre_usuario, valor_nuevo, detalle "
				+ "FROM evento_auditoria WHERE colegio_id = ? ORDER BY secuencia", creado.colegioId());
		assertThat(delColegio).extracting(e -> e.get("accion")).contains("PRIMERA_PROMOTORIA_CREADA", "COLEGIO_CREADO");
		assertThat(delColegio).allMatch(e -> "operador.alta".equals(e.get("nombre_usuario")));
		assertThat(delColegio).anyMatch(e -> String.valueOf(e.get("detalle")).contains("lo creó el operador operador.alta")
				|| String.valueOf(e.get("detalle")).contains("Tu colegio y tu cuenta los creó el operador operador.alta"));
	}

	@Test
	void noSePuedeRepetirLaPrimeraPromotoria() {
		ColegioCreado creado = alta.crear(EscenarioPlataforma.alta("Colegio Repetido", EscenarioPlataforma.RUC_B,
				"titular.repetido", "45678913", "987650002"), null, null);

		assertThatThrownBy(() -> alta.completarAlta(creado.colegioId(),
				new DatosTitular("otro.titular", "Otro Titular", "987650003", null)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("primera Promotoría");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE colegio_id = ?", Long.class, creado.colegioId()))
				.isEqualTo(1L);
	}

	@Test
	void unRucInvalidoNoCreaNada() {
		long antes = jdbc.queryForObject("SELECT COUNT(*) FROM colegio", Long.class);

		assertThatThrownBy(() -> alta.crear(EscenarioPlataforma.alta("Colegio Mal", "20600000014", "titular.mal",
				"45678914", "987650004"), null, null)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("no es válido");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM colegio", Long.class)).isEqualTo(antes);
	}

	@Test
	void laFichaYElContactoDelTitularSeCorrigenSoloEnPreparacion() {
		ColegioCreado creado = alta.crear(EscenarioPlataforma.alta("Colegio Ficha", EscenarioPlataforma.RUC_C,
				"titular.ficha", "45678915", "987650005"), null, null);

		alta.corregirFicha(creado.colegioId(), new FichaColegioRequest("Colegio Ficha Corregida", "Ficha S.A.C.",
				EscenarioPlataforma.RUC_D, "Jr. Nuevo 456", "nuevo@ficha.edu.pe",
				EnumSet.of(pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel.INICIAL), "CT-9", "Titular Ficha", "45678915"));
		assertThat(jdbc.queryForMap("SELECT nombre, ruc, niveles FROM colegio WHERE id = ?", creado.colegioId()))
				.containsEntry("nombre", "Colegio Ficha Corregida").containsEntry("ruc", EscenarioPlataforma.RUC_D)
				.containsEntry("niveles", "INICIAL");

		// S8-A1: el contacto lo propone un operador y lo aplica OTRO.
		Long cambio = alta.proponerContactoTitular(creado.colegioId(), new ContactoTitularRequest("987650006", null,
				"El titular corrigió su celular por teléfono"));
		UsuariosDePrueba.iniciarSesion(EscenarioPlataforma.operador(usuarios, codificador, colegios, "operador.alta2"));
		ColegioCreado reenviado = alta.aplicarContactoTitular(creado.colegioId(), cambio);
		UsuariosDePrueba.iniciarSesion(operador);
		assertThat(reenviado.titularDestino()).startsWith("WhatsApp");
		assertThat(jdbc.queryForObject("SELECT telefono_whatsapp FROM usuario WHERE colegio_id = ?", String.class,
				creado.colegioId())).isEqualTo("+51987650006");
		assertThat(jdbc.queryForList("SELECT accion FROM evento_auditoria WHERE colegio_id = ?", String.class,
				creado.colegioId())).contains("TITULAR_CONTACTO_CORREGIDO");

		// En producción, el operador ya no cambia la ficha.
		jdbc.update("UPDATE colegio SET estado = 'PRODUCCION', produccion_desde = CURRENT_TIMESTAMP, produccion_por = 'x' "
				+ "WHERE id = ?", creado.colegioId());
		assertThatThrownBy(() -> alta.corregirFicha(creado.colegioId(), new FichaColegioRequest("Otro", "Otro S.A.C.",
				EscenarioPlataforma.RUC_D, "Jr. 1", "a@b.pe",
				EnumSet.of(pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel.INICIAL), null, "Titular", "45678915")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("producción");
	}

	@Test
	void elCorreoDeControlLoRegistraElOperadorConSuFirma() {
		ColegioCreado creado = alta.crear(EscenarioPlataforma.alta("Colegio Control", EscenarioPlataforma.RUC_A,
				"titular.control", "45678916", "987650007"), null, null);

		alta.registrarCorreoControl(creado.colegioId(), "contador@estudio.pe");
		alta.registrarCorreoControl(creado.colegioId(), "contador2@estudio.pe");

		assertThat(jdbc.queryForList("SELECT valor FROM configuracion_colegio WHERE colegio_id = ?", String.class,
				creado.colegioId())).containsOnly("contador2@estudio.pe").hasSize(2);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM firma_operacion WHERE colegio_id = ? AND clave LIKE ?", Long.class,
				EscenarioPlataforma.plataformaId(colegios), "configuracion_colegio:" + creado.colegioId() + ":%"))
				.isEqualTo(4L);
		assertThat(jdbc.queryForList("SELECT valor_nuevo FROM evento_auditoria WHERE colegio_id = ? AND accion = ?",
				String.class, creado.colegioId(), "CORREO_CONTROL_REGISTRADO")).hasSize(2)
				.noneMatch(v -> v.contains("contador2@estudio.pe"));
	}

	@Test
	void soloElOperadorDaDeAltaColegios() {
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(Rol.PROMOTOR));

		assertThatThrownBy(() -> alta.crear(EscenarioPlataforma.alta("Colegio Intruso", EscenarioPlataforma.RUC_B,
				"titular.intruso", "45678917", "987650008"), null, null)).isInstanceOf(AccessDeniedException.class);
	}
}
