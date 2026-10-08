package pe.edu.virgenmaria.cuentasclaras.mysql;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.panel.proceso.ResumenDiarioTarea;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioContactoPersonal;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Auditoría del sprint 6 contra MySQL 8 real, con los permisos de cc_app: reproducía los ataques que pasaban por encima
 * de los triggers. Corre como la fase 2 del job mysql, con CC_PRUEBA_MYSQL=true. Correcciones del sprint 6 (V23 y los
 * triggers de esa tanda): sus aserciones se INVIRTIERON y dicen «CORREGIDO»: cada prueba en verde significa que el ataque
 * ya falla con 1644. Usa nombres únicos y no limpia.
 */
@SpringBootTest
@ActiveProfiles({ "test", "mysql" })
@EnabledIfEnvironmentVariable(named = "CC_PRUEBA_MYSQL", matches = "true")
class AuditoriaSprint6MySqlTest {

	private static final ZoneId LIMA = ZoneId.of("America/Lima");

	private static final String INSERTAR_MENSAJE = "INSERT INTO mensaje (colegio_id, clave, tipo, canal, destinatario_tipo, usuario_id, destino, plantilla, parametros, entidad, entidad_id, estado, creado_en, creado_por, actualizado_en) VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(6), ?, NOW(6))";

	private static final String INSERTAR_FOTO = "INSERT INTO resumen_diario (colegio_id, fecha, cortado_en, cobrado_total, pagos_cantidad, cobrado_efectivo, pagos_efectivo, cobrado_mes, deuda_vencida, familias_morosas, cajas_sin_cerrar, cierres_con_diferencia, solicitudes_pendientes, alertas_criticas, avisos_familias, avisos_entregados, parametros, creado_en, creado_por, actualizado_en) VALUES (1, ?, ?, 0, 0, 0, 0, 0, 0, 0, ?, ?, ?, ?, ?, ?, ?, NOW(6), ?, NOW(6))";

	private final String sufijo = Long.toString(System.nanoTime(), 36);

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ResumenDiarioTarea resumenDiario;

	@Autowired
	private ServicioContactoPersonal contactoPersonal;

	@Autowired
	private BandejaAprobaciones bandeja;

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
	}

	// S6-A1. El celular de la promotora cambiaba sin solicitud: la cuenta pasaba un momento por «cuenta de apoderado».

	/**
	 * CORREGIDO. Con las credenciales de cc_app: trg_usuario_contacto (versión de V23) no deja enlazar la cuenta de la
	 * promotora (con roles del personal) a un apoderado, ni desenlazar una cuenta de apoderado, y vigila la cuenta si es
	 * del personal antes O después del UPDATE. Variante con usuario_rol: aunque se le quiten los roles, se enlace, se
	 * cambie el celular y se le devuelva el rol PROMOTOR (usuario_rol no tiene trigger), la cuenta enlazada a un apoderado
	 * no recibe alertas ni el resumen (trg_mensaje_nace exige apoderado_id vacío): el atacante no recibe nada.
	 */
	@Test
	void s6a1ElCelularDeLaPromotoraCambiaPasandoPorUnaCuentaDeApoderado() {
		Usuario promotora = guardar("promo.a1." + sufijo, Rol.PROMOTOR);
		Long apoderado = apoderadoSinCuenta();
		String atacante = celular();
		Integer directo = codigoAl(() -> jdbc.update("UPDATE usuario SET telefono_whatsapp = ? WHERE id = ?", atacante, promotora.getId()));
		assertThat(directo).as("el camino directo sí lo frena el trigger").isEqualTo(1644);

		assertThat(codigoAl(() -> jdbc.update("UPDATE usuario SET apoderado_id = ? WHERE id = ?", apoderado, promotora.getId())))
				.as("CORREGIDO: una cuenta del personal no se enlaza a un apoderado").isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE usuario SET apoderado_id = ?, telefono_whatsapp = ? WHERE id = ?",
				apoderado, atacante, promotora.getId()))).as("ni en el mismo UPDATE").isEqualTo(1644);
		Map<String, Object> fila = jdbc.queryForMap("SELECT telefono_whatsapp, apoderado_id FROM usuario WHERE id = ?", promotora.getId());
		assertThat(fila).containsEntry("telefono_whatsapp", promotora.getTelefonoWhatsapp()).containsEntry("apoderado_id", null);

		// Una cuenta de apoderado (nace enlazada, como la crea su ficha) no vuelve a ser «del personal».
		Long otroApoderado = apoderadoSinCuenta();
		String dni = jdbc.queryForObject("SELECT numero_documento FROM apoderado WHERE id = ?", String.class, otroApoderado);
		Usuario cuentaApoderado = ContextoColegio.en(1L, () -> usuarios.save(Usuario.deApoderado(dni, "Apoderado " + dni,
				null, codificador.encode(UsuariosDePrueba.CLAVE), otroApoderado)));
		assertThat(codigoAl(() -> jdbc.update("UPDATE usuario SET apoderado_id = NULL WHERE id = ?", cuentaApoderado.getId())))
				.as("CORREGIDO: no se desenlaza").isEqualTo(1644);

		// Variante con los roles: la cuenta queda enlazada y con el celular del atacante, pero no recibe nada.
		Usuario victima = guardar("promo.a1r." + sufijo, Rol.PROMOTOR);
		Long tercero = apoderadoSinCuenta();
		jdbc.update("UPDATE usuario_rol SET rol = 'APODERADO' WHERE usuario_id = ?", victima.getId());
		jdbc.update("UPDATE usuario SET apoderado_id = ? WHERE id = ?", tercero, victima.getId());
		jdbc.update("UPDATE usuario SET telefono_whatsapp = ? WHERE id = ?", atacante, victima.getId());
		jdbc.update("UPDATE usuario_rol SET rol = 'PROMOTOR' WHERE usuario_id = ?", victima.getId());
		assertThat(codigoAl(() -> jdbc.update(INSERTAR_MENSAJE, "s6a1-" + sufijo, "ALERTA_PROMOTORIA", "WHATSAPP", "USUARIO",
				victima.getId(), atacante, "ALERTA_PROMOTORIA", "Cierre de caja con diferencia", "aviso", null, "PENDIENTE",
				"sistema.panel"))).as("CORREGIDO: la alerta no sale a una cuenta enlazada a un apoderado").isEqualTo(1644);
		// La cuenta rara no queda activa para las demás pruebas (desactivar no toca el contacto: el trigger lo deja).
		assertThat(jdbc.update("UPDATE usuario SET activo = FALSE WHERE id = ?", victima.getId())).isEqualTo(1);
	}

	// S6-A1, segunda variante: una solicitud aprobada ANTIGUA de la misma persona servía otra vez, con otro número.

	/**
	 * CORREGIDO. La promotora cambió su celular dos veces, con solicitudes 1 y 2 aprobadas por Dirección. Con cc_app se
	 * intenta volver a enlazar la 1 (que ya no está en ninguna fila) con otro número: el trigger exige una solicitud MÁS
	 * NUEVA que la anterior y que el número sea EXACTAMENTE el aprobado en ella. Tampoco sirve la solicitud vigente con
	 * otro número.
	 */
	@Test
	void s6a1ReusaUnaSolicitudAprobadaAntiguaParaPonerOtroCelular() {
		Usuario promotora = guardar("promo.a1b." + sufijo, Rol.PROMOTOR);
		Usuario directora = guardar("dir.a1b." + sufijo, Rol.DIRECTOR);
		UsuariosDePrueba.iniciarSesion(promotora);
		Long primera = contactoPersonal.solicitar(promotora.getId(), nueve(), null, "Cambié de número de celular este mes");
		UsuariosDePrueba.iniciarSesion(directora);
		bandeja.aprobar(primera, null);
		UsuariosDePrueba.iniciarSesion(promotora);
		Long segunda = contactoPersonal.solicitar(promotora.getId(), nueve(), null, "Volví a cambiar de número de celular");
		UsuariosDePrueba.iniciarSesion(directora);
		bandeja.aprobar(segunda, null);
		SecurityContextHolder.clearContext();
		String vigente = jdbc.queryForObject("SELECT telefono_whatsapp FROM usuario WHERE id = ?", String.class, promotora.getId());

		String atacante = celular();
		assertThat(codigoAl(() -> jdbc.update("UPDATE usuario SET telefono_whatsapp = ?, contacto_solicitud_id = ? WHERE id = ?",
				atacante, primera, promotora.getId()))).as("CORREGIDO: la solicitud 1 no se reusa").isEqualTo(1644);
		String datosPrimera = jdbc.queryForObject("SELECT datos FROM solicitud_cambio WHERE id = ?", String.class, primera);
		String aprobadoEnLaPrimera = datosPrimera.replaceAll(".*\"telefono\"\\s*:\\s*\"([^\"]*)\".*", "$1");
		assertThat(codigoAl(() -> jdbc.update("UPDATE usuario SET telefono_whatsapp = ?, contacto_solicitud_id = ? WHERE id = ?",
				aprobadoEnLaPrimera, primera, promotora.getId()))).as("ni con el número que se aprobó en ella").isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE usuario SET telefono_whatsapp = ? WHERE id = ?", atacante,
				promotora.getId()))).as("ni la vigente con otro número").isEqualTo(1644);
		assertThat(jdbc.queryForObject("SELECT telefono_whatsapp FROM usuario WHERE id = ?", String.class, promotora.getId()))
				.isEqualTo(vigente);
	}

	// S6-M1. Una foto plantada y un resumen con cifras inventadas sustituían al de sistema.panel.

	/**
	 * CORREGIDO. Con cc_app (que puede escribir creado_por = sistema.panel): la foto de otro día, la de hoy con un corte que
	 * no es de ahora, la de hoy con conteos que no son los de las tablas y la de hoy sin el texto de sus cifras fallan con
	 * 1644 (trg_resumen_diario_registro, versión de V23). Y un mensaje RESUMEN_DIARIO con cifras inventadas que apunta a la
	 * foto VERDADERA de hoy también falla: trg_mensaje_nace exige el texto exacto de la foto.
	 */
	@Test
	void s6m1UnaFotoPlantadaYUnResumenInventadoSustituyenAlDeSistemaPanel() {
		LocalDate pasado = LocalDate.of(1951, 1, 1).plusDays(Math.floorMod(System.nanoTime(), 3000));
		LocalDate hoy = LocalDate.now(LIMA);
		LocalDateTime ahora = LocalDateTime.now(LIMA);
		Usuario promotora = guardar("promo.m1." + sufijo, Rol.PROMOTOR);
		UsuariosDePrueba.iniciarSesion(promotora);
		contactoPersonal.solicitar(promotora.getId(), nueve(), null, "Pedido pendiente para la prueba de la foto");
		SecurityContextHolder.clearContext();
		Long pendientes = jdbc.queryForObject("SELECT COUNT(1) FROM solicitud_cambio WHERE colegio_id = 1 AND estado = ?", Long.class, "PENDIENTE");
		assertThat(pendientes).isPositive();

		assertThat(codigoAl(() -> jdbc.update(INSERTAR_FOTO, pasado, pasado.atStartOfDay().plusSeconds(1), 0, 0, 0, 0, 0, 0,
				null, "sistema.panel"))).as("CORREGIDO: la foto de otro día").isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update(INSERTAR_FOTO, hoy, ahora.minusHours(3), 0, 0, pendientes, 0, 0, 0, null,
				"sistema.panel"))).as("CORREGIDO: la foto de hoy con un corte que no es de ahora").isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update(INSERTAR_FOTO, hoy, ahora, 0, 0, 0, 0, 0, 0, null, "sistema.panel")))
				.as("CORREGIDO: 0 pendientes cuando hay pendientes (y sin texto)").isEqualTo(1644);

		// La foto verdadera de hoy (la genera sistema.panel; si ya existe, la de otra prueba) y un mensaje inventado. En un
		// domingo o feriado sin cobros no hay foto de hoy: esa parte la cubre también
		// PermisosMySqlTest.elResumenYLasAlertasSoloVanAPromotoria, que registra un cobro antes.
		resumenDiario.enColegio(1L, hoy);
		Long fotoId = jdbc.queryForObject("SELECT MAX(id) FROM resumen_diario WHERE colegio_id = 1 AND fecha = ?", Long.class, hoy);
		if (fotoId == null) {
			return;
		}
		String verdadero = jdbc.queryForObject("SELECT parametros FROM resumen_diario WHERE id = ?", String.class, fotoId);
		assertThat(verdadero).as("la foto guarda su texto").isNotBlank();
		String inventado = String.join("\n", hoy.toString(), "S/ 12,000.00", "40", "80 %", "S/ 2,400.00",
				"2 cerrada(s), 0 sin cerrar, 0 con diferencia; desde el resumen anterior, 0 cierre(s) con diferencia",
				"S/ 99,000.00", "S/ 0.00 de 0 familia(s)", "0", "0", "evento 1", "esta semana no hay a quién llamar");
		String clave = "RESUMEN_DIARIO:resumen_diario:" + fotoId + ":USUARIO:" + promotora.getId() + ":WHATSAPP:" + sufijo;
		assertThat(codigoAl(() -> jdbc.update(INSERTAR_MENSAJE, clave, "RESUMEN_DIARIO", "WHATSAPP", "USUARIO",
				promotora.getId(), promotora.getTelefonoWhatsapp(), "RESUMEN_DIARIO", inventado, "resumen_diario", fotoId,
				"PENDIENTE", "sistema.panel"))).as("CORREGIDO: el resumen con cifras que no son las de la foto").isEqualTo(1644);
		assertThat(jdbc.update(INSERTAR_MENSAJE, clave, "RESUMEN_DIARIO", "WHATSAPP", "USUARIO", promotora.getId(),
				promotora.getTelefonoWhatsapp(), "RESUMEN_DIARIO", verdadero, "resumen_diario", fotoId, "PENDIENTE",
				"sistema.panel")).as("control: con el texto de la foto sí nace").isEqualTo(1);
	}

	private Usuario guardar(String nombre, Rol rol) {
		return UsuariosDePrueba.guardar(usuarios, codificador, 1L, nombre, UsuariosDePrueba.CLAVE, false, rol);
	}

	/** 9 dígitos que empiezan con 9, como los escribe una persona en el formulario. */
	private static String nueve() {
		return "9" + String.format("%08d", Math.floorMod(System.nanoTime(), 100_000_000L));
	}

	/** El mismo, normalizado como lo guarda la aplicación. */
	private static String celular() {
		return "+51" + nueve();
	}

	/** Una familia con un apoderado sin cuenta en línea, creados con los permisos de cc_app. */
	private Long apoderadoSinCuenta() {
		String nombre = "Familia puente " + sufijo + System.nanoTime();
		jdbc.update("INSERT INTO familia (colegio_id, nombre, creado_en, creado_por, actualizado_en) VALUES (1, ?, NOW(6), ?, NOW(6))", nombre, "auditoria");
		Long familia = jdbc.queryForObject("SELECT MAX(id) FROM familia WHERE nombre = ?", Long.class, nombre);
		String dni = String.format("%08d", Math.floorMod(System.nanoTime(), 100_000_000L));
		jdbc.update("INSERT INTO apoderado (colegio_id, familia_id, tipo_documento, numero_documento, apellido_paterno, nombres, parentesco, telefono_whatsapp, nombre_busqueda, activo, creado_en, creado_por, actualizado_en) VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, NOW(6), ?, NOW(6))", familia, "DNI", dni, "Puente", "Ap", "OTRO", celular(), "puente ap", "auditoria");
		return jdbc.queryForObject("SELECT id FROM apoderado WHERE colegio_id = 1 AND numero_documento = ?", Long.class, dni);
	}

	private Integer codigoAl(Runnable sentencia) {
		try {
			sentencia.run();
			return null;
		}
		catch (DataAccessException e) {
			for (Throwable t = e; t != null; t = t.getCause()) {
				if (t instanceof SQLException sql) {
					return sql.getErrorCode();
				}
			}
			return null;
		}
	}
}
