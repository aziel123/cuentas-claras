package pe.edu.virgenmaria.cuentasclaras.fraude;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.proceso.HuellaDiaria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AlertasHuella;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * S5-M4 (G13): antes, la huella diaria solo protegía los días ya «huellados» y solo contra las filas que el atacante no
 * borró. Con las correcciones (V20): huella por hora en horario de caja, la secuencia nunca retrocede por debajo de una
 * huella guardada o ENVIADA, alerta si faltan días y el mensaje de hoy repite la huella anterior. Las aserciones «HUECO»
 * se invirtieron («CORREGIDO»).
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AtaquesHuellaSprint5Test {

	private static final Instant LUNES = Instant.parse("2026-10-05T15:00:00Z");

	@Autowired private HuellaDiaria huella;
	@Autowired private AuditoriaService auditoria;
	@Autowired private AlertasHuella alertas;
	@Autowired private UsuarioRepository usuarios;
	@Autowired private PasswordEncoder codificador;
	@Autowired private PlatformTransactionManager transacciones;
	@Autowired private RelojAjustable reloj;
	@Autowired private JdbcTemplate jdbc;

	private Usuario promotora;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		reloj.fijar(LUNES);
		promotora = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "promotora", UsuariosDePrueba.CLAVE, false,
				Rol.PROMOTOR);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private void eventos(int cantidad) {
		ContextoColegio.en(1L, () -> new TransactionTemplate(transacciones).executeWithoutResult(t -> {
			for (int i = 0; i < cantidad; i++) {
				auditoria.registrar(auditoria.actorPara(1L, promotora.getId(), "promotora", "PROMOTOR"),
						AccionAuditoria.INGRESO_EXITOSO, "usuario", promotora.getId().toString(), null, null, "Ingreso " + i);
			}
		}));
	}

	private long ultima() {
		return jdbc.queryForObject("SELECT MAX(secuencia) FROM evento_auditoria", Long.class);
	}

	/** El DBA borra la cola y retrocede el eslabón al evento conservado. */
	private void recortarHasta(long conservado) {
		String hash = jdbc.queryForObject("SELECT hash FROM evento_auditoria WHERE secuencia = ?", String.class,
				conservado);
		jdbc.update("DELETE FROM evento_auditoria WHERE secuencia > ?", conservado);
		jdbc.update("UPDATE auditoria_cadena SET ultima_secuencia = ?, ultimo_hash = ? WHERE id = 1", conservado, hash);
	}

	private void a0600DelDiaSiguiente() {
		LocalDate siguiente = LocalDate.now(reloj).plusDays(1);
		reloj.fijar(siguiente.atTime(6, 0).atZone(reloj.getZone()).toInstant());
	}

	/**
	 * (a) CORREGIDO: fraude y recorte el MISMO día, antes de las 06:00 del siguiente. La huella de la hora (horario de caja)
	 * ya guardó el último evento: al día siguiente no coincide, la huella diaria no puede retroceder por debajo de ella y
	 * Promotoría recibe «Bitácora verificada: NO». (Un recorte dentro de la MISMA hora sigue siendo un riesgo residual.)
	 */
	@Test
	void s5M4a_recorteDelMismoDiaNoSeDetecta() {
		eventos(2);
		long conservado = ultima();
		eventos(3); // la anulación y el cierre que el DBA quiere esconder
		huella.horaEnColegio(1L, LocalDateTime.now(reloj).plusMinutes(30)); // la huella de las 10:30
		recortarHasta(conservado);
		a0600DelDiaSiguiente();

		huella.enColegio(1L, LocalDate.now(reloj));

		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'HUELLA_NO_COINCIDE'")).as("CORREGIDO").isPositive();
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'HUELLA_RETROCEDIO'")).as("CORREGIDO").isPositive();
		assertThat(contar(jdbc, "huella_bitacora")).as("CORREGIDO: la huella recortada no se guarda").isZero();
		assertThat(jdbc.queryForObject("SELECT parametros FROM mensaje WHERE tipo = 'HUELLA_BITACORA'", String.class))
				.as("CORREGIDO: Promotoría recibe «Bitácora verificada: NO»").endsWith("NO, avise al contador");
	}

	/**
	 * (b) CORREGIDO: el DBA borra también la huella guardada. La secuencia no puede quedar por debajo de la que ya salió por
	 * mensaje a Promotoría, y faltan días de huella: dos alertas CRÍTICAS y el mensaje de hoy dice cuál fue la anterior.
	 */
	@Test
	void s5M4b_borrarTambienLaHuellaGuardadaNoSeDetecta() {
		eventos(1);
		long conservado = ultima();
		eventos(3);
		a0600DelDiaSiguiente();
		huella.enColegio(1L, LocalDate.now(reloj));
		long enviadaAyer = jdbc.queryForObject("SELECT secuencia FROM huella_bitacora", Long.class);

		recortarHasta(conservado);
		jdbc.update("DELETE FROM huella_bitacora");
		reloj.avanzar(Duration.ofDays(1));
		huella.enColegio(1L, LocalDate.now(reloj));

		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'HUELLA_RETROCEDIO'")).as("CORREGIDO").isPositive();
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'HUELLA_FALTAN_DIAS'")).as("CORREGIDO").isPositive();
		UsuariosDePrueba.iniciarSesion(promotora);
		assertThat(alertas.alertas()).as("CORREGIDO: alertas CRÍTICAS").isNotEmpty();
		assertThat(jdbc.queryForObject("SELECT COALESCE(MAX(secuencia), 0) FROM huella_bitacora", Long.class))
				.as("CORREGIDO: la secuencia no retrocede").satisfiesAnyOf(v -> assertThat(v).isZero(),
						v -> assertThat(v).isGreaterThanOrEqualTo(enviadaAyer));
		assertThat(jdbc.queryForList("SELECT parametros FROM mensaje WHERE tipo = 'HUELLA_BITACORA' ORDER BY id",
				String.class)).last().asString().contains("evento " + enviadaAyer).endsWith("NO, avise al contador");
	}
}
