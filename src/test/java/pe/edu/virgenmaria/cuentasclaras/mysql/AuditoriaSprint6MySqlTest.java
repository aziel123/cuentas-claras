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
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.panel.proceso.ResumenDiarioTarea;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioContactoPersonal;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Auditoría del sprint 6 contra MySQL 8 real, con los permisos de cc_app: reproduce los ataques que pasan por encima de
 * los triggers nuevos. Corre como la fase 2 del job mysql, con CC_PRUEBA_MYSQL=true. Cada prueba queda en verde
 * mientras el hallazgo siga abierto: al corregirlo hay que invertir sus aserciones. Usa nombres únicos y no limpia.
 */
@SpringBootTest
@ActiveProfiles({ "test", "mysql" })
@EnabledIfEnvironmentVariable(named = "CC_PRUEBA_MYSQL", matches = "true")
class AuditoriaSprint6MySqlTest {

	private static final String INSERTAR_MENSAJE = "INSERT INTO mensaje (colegio_id, clave, tipo, canal, destinatario_tipo, usuario_id, destino, plantilla, parametros, entidad, entidad_id, estado, creado_en, creado_por, actualizado_en) VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(6), ?, NOW(6))";

	private static final String INSERTAR_FOTO = "INSERT INTO resumen_diario (colegio_id, fecha, cortado_en, cobrado_total, pagos_cantidad, cobrado_efectivo, pagos_efectivo, cobrado_mes, deuda_vencida, familias_morosas, cajas_sin_cerrar, cierres_con_diferencia, solicitudes_pendientes, alertas_criticas, avisos_familias, avisos_entregados, creado_en, creado_por, actualizado_en) VALUES (1, ?, ?, 0, 0, 0, 0, 0, 0, 0, ?, ?, ?, ?, ?, ?, NOW(6), ?, NOW(6))";

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

	// S6-A1. El celular de la promotora cambia sin solicitud: la cuenta pasa un momento por «cuenta de apoderado».

	/**
	 * Con las credenciales de cc_app: trg_usuario_contacto solo vigila a quien YA es del personal en la fila anterior
	 * (OLD.apoderado_id IS NULL). Tres UPDATE: enlazar la cuenta de la promotora a un apoderado cualquiera (no toca el
	 * contacto), cambiar el celular y el correo (OLD ya es «de apoderado»: el trigger no mira) y quitar el enlace (no toca
	 * el contacto). Queda una promotora activa con el celular del atacante, sin solicitud, sin aviso al número anterior y
	 * sin evento en la bitácora: el resumen, la huella y las alertas salen desde ahora a ese número.
	 */
	@Test
	void s6a1ElCelularDeLaPromotoraCambiaPasandoPorUnaCuentaDeApoderado() {
		Usuario promotora = guardar("promo.a1." + sufijo, Rol.PROMOTOR);
		Long apoderado = apoderadoSinCuenta();
		String atacante = celular();
		Integer directo = codigoAl(() -> jdbc.update("UPDATE usuario SET telefono_whatsapp = ? WHERE id = ?", atacante, promotora.getId()));
		assertThat(directo).as("el camino directo sí lo frena el trigger").isEqualTo(1644);

		jdbc.update("UPDATE usuario SET apoderado_id = ? WHERE id = ?", apoderado, promotora.getId());
		jdbc.update("UPDATE usuario SET telefono_whatsapp = ?, correo = ? WHERE id = ?", atacante, "atacante." + sufijo + "@correo.pe", promotora.getId());
		jdbc.update("UPDATE usuario SET apoderado_id = NULL WHERE id = ?", promotora.getId());

		Map<String, Object> fila = jdbc.queryForMap("SELECT telefono_whatsapp, apoderado_id, contacto_solicitud_id, activo FROM usuario WHERE id = ?", promotora.getId());
		assertThat(fila).containsEntry("telefono_whatsapp", atacante).containsEntry("apoderado_id", null).containsEntry("contacto_solicitud_id", null);
		Long solicitudes = jdbc.queryForObject("SELECT COUNT(1) FROM solicitud_cambio WHERE entidad = ? AND entidad_id = ?", Long.class, "usuario", promotora.getId());
		assertThat(solicitudes).as("sin solicitud ni aprobación").isZero();
		int alerta = jdbc.update(INSERTAR_MENSAJE, "s6a1-" + sufijo, "ALERTA_PROMOTORIA", "WHATSAPP", "USUARIO", promotora.getId(), atacante, "ALERTA_PROMOTORIA", "Cierre de caja con diferencia", "aviso", null, "PENDIENTE", "sistema.panel");
		assertThat(alerta).as("trg_mensaje_nace acepta la alerta al celular del atacante").isEqualTo(1);
	}

	// S6-A1, segunda variante: una solicitud aprobada ANTIGUA de la misma persona sirve otra vez, con otro número.

	/**
	 * La promotora cambió su celular dos veces, con solicitudes 1 y 2 aprobadas por Dirección. Con cc_app se vuelve a
	 * enlazar la 1, que ya no está en ninguna fila: uk_usuario_contacto_solicitud no lo impide, y el trigger solo pide que
	 * la solicitud enlazada sea suya y esté APROBADA; no compara el número nuevo con el que se aprobó en sus datos.
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

		String atacante = celular();
		int filas = jdbc.update("UPDATE usuario SET telefono_whatsapp = ?, contacto_solicitud_id = ? WHERE id = ?", atacante, primera, promotora.getId());
		assertThat(filas).as("la solicitud 1 se reusa").isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT telefono_whatsapp FROM usuario WHERE id = ?", String.class, promotora.getId())).isEqualTo(atacante);
		String datos = jdbc.queryForObject("SELECT datos FROM solicitud_cambio WHERE id = ?", String.class, primera);
		assertThat(datos).as("lo aprobado era otro número").doesNotContain(atacante.substring(3));
	}

	// S6-M1. Una foto plantada antes de las 19:30 y un resumen con cifras inventadas sustituyen al de sistema.panel.

	/**
	 * Con cc_app, que puede escribir creado_por = sistema.panel: se planta la foto de un día con las 7 cifras de los
	 * libros, que el trigger exige, pero con los conteos que el trigger NO mira: 0 cajas sin cerrar, 0 cierres con
	 * diferencia, 0 pendientes y 0 alertas críticas, aunque haya solicitudes pendientes; y un mensaje RESUMEN_DIARIO a la
	 * promotora con parámetros inventados: el trigger del mensaje solo exige que apunte a una foto del colegio. A las 19:30
	 * sistema.panel encuentra la foto y no hace nada: no envía el resumen verdadero ni deja RESUMEN_DIARIO_NO_SALIO, y la
	 * alerta «no salió» tampoco salta porque el mensaje falso sí sale.
	 */
	@Test
	void s6m1UnaFotoPlantadaYUnResumenInventadoSustituyenAlDeSistemaPanel() {
		LocalDate dia = LocalDate.of(1951, 1, 1).plusDays(Math.floorMod(System.nanoTime(), 3000));
		Usuario promotora = guardar("promo.m1." + sufijo, Rol.PROMOTOR);
		UsuariosDePrueba.iniciarSesion(promotora);
		contactoPersonal.solicitar(promotora.getId(), nueve(), null, "Pedido pendiente para la prueba de la foto");
		SecurityContextHolder.clearContext();
		Long pendientes = jdbc.queryForObject("SELECT COUNT(1) FROM solicitud_cambio WHERE colegio_id = 1 AND estado = ?", Long.class, "PENDIENTE");
		assertThat(pendientes).isPositive();

		int foto = jdbc.update(INSERTAR_FOTO, dia, dia.atStartOfDay().plusSeconds(1), 0, 0, 0, 0, 0, 0, "sistema.panel");
		assertThat(foto).as("la foto con 0 pendientes y 0 alertas críticas pasa el trigger").isEqualTo(1);
		Long fotoId = jdbc.queryForObject("SELECT id FROM resumen_diario WHERE colegio_id = 1 AND fecha = ?", Long.class, dia);
		String inventado = String.join("\n", dia.toString(), "S/ 12,000.00", "40", "80 %", "S/ 2,400.00", "2 cerrada(s), 0 sin cerrar, 0 con diferencia", "S/ 99,000.00", "S/ 0.00 de 0 familia(s)", "0", "0", "evento 1");
		String clave = "RESUMEN_DIARIO:resumen_diario:" + fotoId + ":USUARIO:" + promotora.getId() + ":WHATSAPP";
		int mensaje = jdbc.update(INSERTAR_MENSAJE, clave, "RESUMEN_DIARIO", "WHATSAPP", "USUARIO", promotora.getId(), promotora.getTelefonoWhatsapp(), "RESUMEN_DIARIO", inventado, "resumen_diario", fotoId, "PENDIENTE", "sistema.panel");
		assertThat(mensaje).as("el resumen con cifras que no son las de la foto pasa el trigger").isEqualTo(1);

		assertThat(resumenDiario.enColegio(1L, dia)).as("sistema.panel no genera el verdadero").isEmpty();
		Long resumenes = jdbc.queryForObject("SELECT COUNT(1) FROM mensaje WHERE tipo = ? AND entidad_id = ?", Long.class, "RESUMEN_DIARIO", fotoId);
		assertThat(resumenes).as("solo sale el inventado").isEqualTo(1);
		Long noSalio = jdbc.queryForObject("SELECT COUNT(1) FROM evento_auditoria WHERE accion = ? AND valor_nuevo = ?", Long.class, "RESUMEN_DIARIO_NO_SALIO", dia.toString());
		assertThat(noSalio).as("ni queda que no salió").isZero();
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
		String nombre = "Familia puente " + sufijo;
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
