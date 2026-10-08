package pe.edu.virgenmaria.cuentasclaras.comunicacion;

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
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.ApoderadoRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Parentesco;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.TipoDocumento;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioFamilias;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.proceso.HuellaDiaria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AlertasHuella;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.proceso.DespachoMensajes;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.service.AlertasComunicacion;
import pe.edu.virgenmaria.cuentasclaras.familias.dto.AvisoRequest;
import pe.edu.virgenmaria.cuentasclaras.familias.model.TipoAvisoFamilia;
import pe.edu.virgenmaria.cuentasclaras.familias.service.AlertasFamilias;
import pe.edu.virgenmaria.cuentasclaras.familias.service.ServicioAvisosFamilia;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.time.Duration;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;

/**
 * Correcciones del sprint 5 que no son de un ataque de la auditoría: S5-B2 (un aviso por responsable de pago), S5-B3
 * (la huella guardada no basta: su mensaje debe salir), S5-M2 (un aviso grave que no cerró Promotoría sigue visible
 * 7 días) y S5-B1/QA-S5-1 de punta a punta (sin WhatsApp por G6, el aviso sale por correo).
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class CorreccionesSprint5Test {

	@Autowired private ServicioCobro cobro;
	@Autowired private ServicioFamilias familias;
	@Autowired private ServicioAlumnos alumnos;
	@Autowired private ServicioEstructura estructura;
	@Autowired private ServicioPlanesPension planes;
	@Autowired private ServicioAvisosFamilia avisos;
	@Autowired private AlertasFamilias alertasFamilias;
	@Autowired private AlertasComunicacion alertasComunicacion;
	@Autowired private AlertasHuella alertasHuella;
	@Autowired private HuellaDiaria huella;
	@Autowired private AuditoriaService auditoria;
	@Autowired private DespachoMensajes despacho;
	@Autowired private UsuarioRepository usuarios;
	@Autowired private PasswordEncoder codificador;
	@Autowired private PlatformTransactionManager transacciones;
	@Autowired private RelojAjustable reloj;
	@Autowired private JdbcTemplate jdbc;

	private EscenarioCaja.Familias f;

	@BeforeEach
	void preparar() {
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	/** Jorge, segundo apoderado de la familia Quispe y responsable de pago de Valeria (contacto SIN verificar). */
	private Long jorgeResponsableDeValeria() {
		como(EscenarioCobranza.ADMINISTRACION);
		Long jorge = familias.agregarApoderado(f.quispe(), new ApoderadoRequest(TipoDocumento.DNI, "40000067", "Quispe",
				"Mamani", "Jorge", Parentesco.PADRE, "977 666 444", null, ""));
		SecurityContextHolder.clearContext();
		jdbc.update("UPDATE alumno SET responsable_pago_id = ? WHERE id = ?", jorge, f.valeria());
		return jorge;
	}

	/** S5-B2: el pago de dos hermanos con distinto responsable exige un aviso que SALIÓ a cada uno. */
	@Test
	void unPagoSinAvisoAUnoDeSusResponsablesEsCritico() {
		jorgeResponsableDeValeria();
		como(EscenarioCaja.CAJA);
		Long pago = cobro.cobrar(efectivo(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-03"),
				cuota(jdbc, f.valeria(), "PEN-2027-03")), "900.00", "900.00"));
		SecurityContextHolder.clearContext();
		despacho.despacharColegio(1L);
		assertThat(jdbc.queryForList("SELECT estado FROM mensaje WHERE tipo = 'PAGO_REGISTRADO' AND entidad_id = ?",
				String.class, pago)).as("solo Rosa recibió el suyo (Jorge aún no verifica)").containsExactly("ENVIADO");

		reloj.avanzar(Duration.ofMinutes(61));
		como(EscenarioCobranza.PROMOTORIA);
		assertThat(alertasComunicacion.alertas()).anyMatch(a -> a.gravedad() == AlertaRevision.Gravedad.CRITICA
				&& a.texto().contains("sin aviso enviado a su responsable de pago"));
		// La ventana es de 35 días (antes 7): a los 20 días el pago sigue en la alerta.
		reloj.avanzar(Duration.ofDays(20));
		assertThat(alertasComunicacion.alertas()).anyMatch(a -> a.texto().contains("sin aviso enviado"));
	}

	/** S5-B1 y QA-S5-1 de punta a punta: el celular de Rosa es del personal (sin aprobar); el aviso sale por correo. */
	@Test
	void sinWhatsappPorG6ElAvisoSalePorCorreo() {
		Usuario caja = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "caja.g6", UsuariosDePrueba.CLAVE, false,
				Rol.CAJA);
		jdbc.update("UPDATE usuario SET telefono_whatsapp = ? WHERE id = ?", "+51" + pe.edu.virgenmaria.cuentasclaras.comun
				.prueba.EscenarioEscolar.CELULAR_ROSA, caja.getId());
		como(EscenarioCaja.CAJA);
		Long pago = cobro.cobrar(efectivo(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")), "450.00",
				"450.00"));
		assertThat(jdbc.queryForList("SELECT canal FROM mensaje WHERE tipo = 'PAGO_REGISTRADO' AND entidad_id = ?",
				String.class, pago)).containsExactly("CORREO");
	}

	/** S5-B3: guardar la huella no basta; si su mensaje no salió a Promotoría, la alerta es CRÍTICA. */
	@Test
	void laHuellaGuardadaSinMensajeEnviadoEsCritica() {
		Usuario promotora = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "promotora.b3", UsuariosDePrueba.CLAVE,
				false, Rol.PROMOTOR);
		ContextoColegio.en(1L, () -> new TransactionTemplate(transacciones).executeWithoutResult(t -> auditoria.registrar(
				auditoria.actorPara(1L, promotora.getId(), "promotora.b3", "PROMOTOR"), AccionAuditoria.INGRESO_EXITOSO,
				"usuario", promotora.getId().toString(), null, null, "Ingreso")));
		LocalDate manana = LocalDate.now(reloj).plusDays(1);
		reloj.fijar(manana.atTime(6, 0).atZone(reloj.getZone()).toInstant());
		huella.enColegio(1L, manana);
		reloj.fijar(manana.atTime(7, 30).atZone(reloj.getZone()).toInstant());

		UsuariosDePrueba.iniciarSesion(promotora);
		assertThat(alertasHuella.alertas()).anyMatch(a -> a.gravedad() == AlertaRevision.Gravedad.CRITICA
				&& a.texto().contains("no salió"));
		SecurityContextHolder.clearContext();
		despacho.despacharColegio(1L);
		UsuariosDePrueba.iniciarSesion(promotora);
		assertThat(alertasHuella.alertas()).noneMatch(a -> a.texto().contains("no salió"));
	}

	/** S5-M2: un aviso grave que cerró Dirección (sin participar) sigue visible para Promotoría durante 7 días. */
	@Test
	void unAvisoGraveQueNoCerroPromotoriaSigueVisibleSieteDias() {
		como(EscenarioCaja.CAJA);
		Long pago = cobro.cobrar(efectivo(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")), "450.00",
				"450.00"));
		como(new UsuarioAutenticado(40L, 1L, "rosa.familia", "Rosa Huamán", null, true, false, false,
				EnumSet.of(Rol.APODERADO), f.rosa()));
		Long aviso = avisos.enviar(new AvisoRequest(TipoAvisoFamilia.NO_RECONOZCO_PAGO, pago, null,
				"No reconozco este pago"));
		como(EscenarioCobranza.DIRECCION);
		avisos.atender(aviso, "Lo revisamos: es el pago de marzo de Mateo.");

		como(EscenarioCobranza.PROMOTORIA);
		assertThat(alertasFamilias.alertas()).anyMatch(a -> a.gravedad() == AlertaRevision.Gravedad.CRITICA
				&& a.texto().contains("no es Promotoría"));
		reloj.avanzar(Duration.ofDays(8));
		assertThat(alertasFamilias.alertas()).noneMatch(a -> a.texto().contains("no es Promotoría"));
	}
}
