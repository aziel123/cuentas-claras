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

import static org.assertj.core.api.Assertions.assertThat;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * S5-M4 (G13): la huella diaria solo protege los días ya «huellados» y solo contra las filas que el atacante no borró.
 * Quien tiene acceso de DBA recorta sin que el sistema lo note si (a) recorta el mismo día, antes de las 06:00, o (b)
 * borra también las filas de huella_bitacora de esos días.
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

	/** (a) Fraude y recorte el MISMO día, antes de las 06:00 del siguiente: la huella nace sobre la bitácora recortada. */
	@Test
	void s5M4a_recorteDelMismoDiaNoSeDetecta() {
		eventos(2);
		long conservado = ultima();
		eventos(3); // la anulación y el cierre que el DBA quiere esconder
		recortarHasta(conservado);
		a0600DelDiaSiguiente();

		huella.enColegio(1L, LocalDate.now(reloj));

		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'HUELLA_NO_COINCIDE'")).as("HUECO").isZero();
		assertThat(jdbc.queryForObject("SELECT secuencia FROM huella_bitacora", Long.class)).isEqualTo(conservado);
		assertThat(jdbc.queryForObject("SELECT parametros FROM mensaje WHERE tipo = 'HUELLA_BITACORA'", String.class))
				.as("HUECO: Promotoría recibe «Bitácora verificada: sí»").endsWith("sí");
	}

	/** (b) El DBA borra también la huella guardada: la reverificación solo mira las filas que quedan. */
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

		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'HUELLA_NO_COINCIDE'")).as("HUECO").isZero();
		UsuariosDePrueba.iniciarSesion(promotora);
		assertThat(alertas.alertas()).as("HUECO: ninguna alerta").isEmpty();
		assertThat(jdbc.queryForObject("SELECT MAX(secuencia) FROM huella_bitacora", Long.class))
				.as("HUECO: la secuencia retrocedió y nadie lo comprueba").isLessThan(enviadaAyer);
	}
}
