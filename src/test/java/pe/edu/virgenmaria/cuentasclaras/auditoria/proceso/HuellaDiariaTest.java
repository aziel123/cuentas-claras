package pe.edu.virgenmaria.cuentasclaras.auditoria.proceso;

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
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AlertasHuella;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * Sprint 5 (G13): la huella de la bitácora sale todos los días al celular de Promotoría (y al correo externo del
 * contador que configuró el DBA). Si alguien recorta la cola de la bitácora con acceso a la base, la reverificación del
 * día siguiente lo detecta: el evento resaltado, la alerta CRÍTICA y el mensaje del día con «Bitácora verificada: NO».
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class HuellaDiariaTest {

	/** Lunes 05/10/2026 a las 10:00 en Lima. */
	private static final Instant LUNES = Instant.parse("2026-10-05T15:00:00Z");

	@Autowired
	private HuellaDiaria huella;

	@Autowired
	private AuditoriaService auditoria;

	@Autowired
	private AlertasHuella alertas;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private PlatformTransactionManager transacciones;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private JdbcTemplate jdbc;

	private Usuario promotora;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		reloj.fijar(LUNES);
		promotora = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "promotora", UsuariosDePrueba.CLAVE, false,
				Rol.PROMOTOR);
		UsuariosDePrueba.guardar(usuarios, codificador, 1L, "cajera", UsuariosDePrueba.CLAVE, false, Rol.CAJA);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	/** Eventos del colegio 1 en la bitácora, a la hora del reloj. */
	private void eventos(int cantidad) {
		ContextoColegio.en(1L, () -> new TransactionTemplate(transacciones).executeWithoutResult(t -> {
			for (int i = 0; i < cantidad; i++) {
				auditoria.registrar(auditoria.actorPara(1L, promotora.getId(), "promotora", "PROMOTOR"),
						AccionAuditoria.INGRESO_EXITOSO, "usuario", promotora.getId().toString(), null, null, "Ingreso " + i);
			}
		}));
	}

	private void manana0600() {
		LocalDate siguiente = LocalDate.now(reloj).plusDays(1);
		reloj.fijar(siguiente.atTime(6, 0).atZone(reloj.getZone()).toInstant());
	}

	@Test
	void laHuellaSaleCadaDiaAPromotoriaYAlCorreoExterno() {
		jdbc.update("INSERT INTO configuracion_bd (clave, valor, creado_en) VALUES ('huella_correo_externo', "
				+ "'contador@estudio.pe', CURRENT_TIMESTAMP)");
		eventos(3);
		long ultimo = jdbc.queryForObject("SELECT MAX(secuencia) FROM evento_auditoria", Long.class);
		String hash = jdbc.queryForObject("SELECT hash FROM evento_auditoria WHERE secuencia = ?", String.class, ultimo);
		manana0600();

		huella.enColegio(1L, LocalDate.now(reloj));

		assertThat(jdbc.queryForMap("SELECT * FROM huella_bitacora")).containsEntry("fecha",
				java.sql.Date.valueOf(LocalDate.of(2026, 10, 5))).containsEntry("secuencia", ultimo)
				.containsEntry("codigo", hash.substring(0, 16)).containsEntry("eventos_del_dia", 3)
				.containsEntry("creado_por", "sistema.auditoria");
		List<Map<String, Object>> mensajes = jdbc.queryForList("SELECT destinatario_tipo, usuario_id, canal, destino, "
				+ "parametros FROM mensaje WHERE tipo = 'HUELLA_BITACORA' ORDER BY destinatario_tipo");
		// Solo Promotoría (no la cajera) y el correo externo del contador.
		assertThat(mensajes).extracting(m -> m.get("destinatario_tipo")).containsExactly("EXTERNO", "USUARIO");
		assertThat(mensajes.get(0)).containsEntry("canal", "CORREO").containsEntry("destino", "contador@estudio.pe");
		assertThat(mensajes.get(1)).containsEntry("usuario_id", promotora.getId()).containsEntry("canal", "WHATSAPP");
		assertThat((String) mensajes.get(1).get("parametros")).contains("05/10/2026").contains(Long.toString(ultimo))
				.contains(hash.substring(0, 16)).endsWith("sí");
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'HUELLA_ENVIADA'")).isEqualTo(1);

		// Es idempotente: otra pasada del mismo día no duplica nada.
		huella.enColegio(1L, LocalDate.now(reloj));
		assertThat(contar(jdbc, "huella_bitacora")).isEqualTo(1);
	}

	@Test
	void unRecorteDeLaBitacoraEsCriticoAlDiaSiguiente() {
		eventos(2);
		long conservado = jdbc.queryForObject("SELECT MAX(secuencia) FROM evento_auditoria", Long.class);
		String hashConservado = jdbc.queryForObject("SELECT hash FROM evento_auditoria WHERE secuencia = ?", String.class,
				conservado);
		eventos(2);
		manana0600();
		huella.enColegio(1L, LocalDate.now(reloj));
		assertThat(contar(jdbc, "huella_bitacora")).isEqualTo(1);

		// Alguien con acceso a la base borra la cola de la bitácora y retrocede el eslabón: la cadena «cuadra».
		jdbc.update("DELETE FROM evento_auditoria WHERE secuencia > ?", conservado);
		jdbc.update("UPDATE auditoria_cadena SET ultima_secuencia = ?, ultimo_hash = ? WHERE id = 1", conservado,
				hashConservado);

		reloj.avanzar(Duration.ofDays(1));
		huella.enColegio(1L, LocalDate.now(reloj));

		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'HUELLA_NO_COINCIDE'")).isEqualTo(1);
		assertThat(jdbc.queryForList("SELECT parametros FROM mensaje WHERE tipo = 'HUELLA_BITACORA' ORDER BY id",
				String.class)).last().asString().contains("NO");
		UsuariosDePrueba.iniciarSesion(promotora);
		assertThat(alertas.alertas()).anySatisfy(a -> {
			assertThat(a.gravedad()).isEqualTo(AlertaRevision.Gravedad.CRITICA);
			assertThat(a.texto()).contains("no coincide con una huella");
		});
	}
}
