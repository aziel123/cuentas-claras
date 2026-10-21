package pe.edu.virgenmaria.cuentasclaras.academico.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ConfiguracionVista;
import pe.edu.virgenmaria.cuentasclaras.academico.model.ConfiguracionAcademica;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.ConfiguracionAcademicaRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Sprint 9, tanda 2 (decisión 148; A6 y E15): las horas y plazos de la asistencia del colegio. Las lee cualquier parte de
 * lo académico (y {@code sistema.asistencia}); las cambia SOLO Dirección, con la firma de su sesión
 * ({@code configuracion_academica:{colegio}:CAMBIADA:{version}}), y el cambio queda RESALTADO en la bitácora con el antes y
 * el después. Los rangos los fija también la base (CHECK de V33).
 */
@Service
@PreAuthorize("hasAnyRole('DIRECTOR','DOCENTE','APODERADO','SISTEMA_ASISTENCIA','PROMOTOR','SISTEMA_PANEL')")
public class ConfiguracionesAcademicas {

	private final ConfiguracionAcademicaRepository configuraciones;

	private final FirmaSesion firma;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	public ConfiguracionesAcademicas(ConfiguracionAcademicaRepository configuraciones, FirmaSesion firma,
			AuditoriaService auditoria, Clock reloj) {
		this.configuraciones = configuraciones;
		this.firma = firma;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	/** La del colegio actual o, sin fila, la de por defecto (sin guardarla). */
	@Transactional(readOnly = true)
	public ConfiguracionAcademica vigente() {
		Long colegio = ContextoColegio.actual();
		return configuraciones.findById(colegio).orElseGet(() -> ConfiguracionAcademica.porDefecto(colegio));
	}

	/** La pantalla de Dirección. */
	@PreAuthorize("hasRole('DIRECTOR')")
	@Transactional(readOnly = true)
	public ConfiguracionVista vista() {
		ConfiguracionAcademica c = vigente();
		return new ConfiguracionVista(c.getHoraAlerta(), c.getHoraAviso(), c.getHoraSegundoAviso(),
				c.getDiasJustificacion(), c.getDiasRegistroTardio(), c.getHoraResumenComunicados(), c.getTopeResumenesMes(),
				c.getActualizadoPor(), c.getActualizadoEn());
	}

	/** Dirección cambia las horas y plazos, con su firma. Queda resaltado en la bitácora (E15). */
	@PreAuthorize("hasRole('DIRECTOR')")
	@Transactional
	public void cambiar(ConfiguracionAcademica.Valores valores) {
		valores.validar();
		UsuarioAutenticado persona = PersonaEnSesion.actual();
		Long colegio = ContextoColegio.actual();
		LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
		ConfiguracionAcademica actual = configuraciones.bloquear(colegio).orElse(null);
		String antes = texto(actual == null ? ConfiguracionAcademica.porDefecto(colegio).valores() : actual.valores());
		if (antes.equals(texto(valores))) {
			return;
		}
		ConfiguracionAcademica guardar;
		if (actual == null) {
			guardar = ConfiguracionAcademica.nueva(colegio, valores, persona.getUsername(), ahora);
		}
		else {
			actual.cambiar(valores, persona.getUsername(), ahora);
			guardar = actual;
		}
		// La firma de Dirección, antes del cambio (trg_configuracion_academica_nace y _cambio la exigen a su nombre).
		firma.firmar(ClaveFirma.configuracionAcademica(colegio, guardar.versionAlGuardar()));
		configuraciones.saveAndFlush(guardar);
		auditoria.registrar(AccionAuditoria.CONFIGURACION_ACADEMICA_CAMBIADA, "configuracion_academica", colegio.toString(),
				antes, texto(valores), persona.nombreCompleto() + " cambió las horas y plazos de la asistencia. Antes: "
						+ antes + ". Ahora: " + texto(valores) + ".");
	}

	static String texto(ConfiguracionAcademica.Valores v) {
		return "alerta " + v.horaAlerta() + ", aviso " + v.horaAviso() + ", segundo aviso y cierre " + v.horaSegundoAviso()
				+ ", justificación " + v.diasJustificacion() + " días hábiles, registro tardío " + v.diasRegistroTardio()
				+ " días hábiles, resumen de comunicados " + v.horaResumenComunicados() + ", tope " + v.topeResumenesMes()
				+ " al mes";
	}
}
