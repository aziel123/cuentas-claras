package pe.edu.virgenmaria.cuentasclaras.academico.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.portal.AvisosDelApoderado;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.portal.ConsentimientoFichaRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.portal.ConsentimientoRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.portal.ConsentimientoVista;
import pe.edu.virgenmaria.cuentasclaras.academico.model.Canal;
import pe.edu.virgenmaria.cuentasclaras.academico.model.ConsentimientoAviso;
import pe.edu.virgenmaria.cuentasclaras.academico.model.Finalidad;
import pe.edu.virgenmaria.cuentasclaras.academico.model.OrigenConsentimiento;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.ConsentimientoAvisoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Sprint 9, tanda 2 (sección 3.6, H2, decisión 152; E37, E38): el consentimiento por finalidad y canal para los avisos de
 * asistencia (y, en la tanda 3, de comunicados).
 * <ul>
 *   <li>{@link #avisos}: el portal muestra SIEMPRE todo (los consentimientos de cada apoderado activo de la familia),
 *       haya o no consentimiento.</li>
 *   <li>{@link #registrarEnPortal}: el propio apoderado da o retira el suyo, con su sesión (trg_consentimiento_aviso_nace
 *       exige que la cuenta sea la de ESE apoderado).</li>
 *   <li>{@link #registrarDesdeFicha}: Administración o Dirección registra el «sí» de la ficha de matrícula firmada, con el
 *       código de la ficha como evidencia (la familia lo ve y lo retira en el portal). Si una misma persona registra más de
 *       30 en un día, el evento lo dice (revisión, no bloqueo).</li>
 * </ul>
 */
@Service
@PreAuthorize("hasAnyRole('APODERADO','DIRECTOR','ADMINISTRACION')")
public class ConsentimientosAviso {

	/** Más de esto en un día por la misma persona desde la ficha: el evento lo resalta en el texto (sección 12). */
	static final int ALERTA_FICHAS_DIA = 30;

	private final ConsentimientoAvisoRepository consentimientos;

	private final ApoderadoRepository apoderados;

	private final ColegioRepository colegios;

	private final SesionDeQuienEscribe sesiones;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	public ConsentimientosAviso(ConsentimientoAvisoRepository consentimientos, ApoderadoRepository apoderados,
			ColegioRepository colegios, SesionDeQuienEscribe sesiones, AuditoriaService auditoria, Clock reloj) {
		this.consentimientos = consentimientos;
		this.apoderados = apoderados;
		this.colegios = colegios;
		this.sesiones = sesiones;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	/** Los avisos de cada apoderado activo de la familia del apoderado en sesión. */
	@PreAuthorize("hasRole('APODERADO')")
	@Transactional(readOnly = true)
	public List<AvisosDelApoderado> avisos() {
		Apoderado yo = apoderadoEnSesion();
		List<AvisosDelApoderado> resultado = new ArrayList<>();
		for (Apoderado a : apoderados.findByFamiliaIdOrderByApellidoPaternoAsc(yo.getFamilia().getId())) {
			if (!a.isActivo()) {
				continue;
			}
			resultado.add(new AvisosDelApoderado(a.getId(), a.getNombres() + " " + a.getApellidoPaterno(),
					a.getId().equals(yo.getId()), a.telefonoVerificado(), a.correoVerificado(), vistas(a)));
		}
		return resultado;
	}

	/** El apoderado da o retira su consentimiento para una finalidad y un canal (vale la última fila). */
	@PreAuthorize("hasRole('APODERADO')")
	@Transactional
	public void registrarEnPortal(ConsentimientoRequest pedido) {
		UsuarioAutenticado persona = PersonaEnSesion.actual();
		Apoderado yo = apoderadoEnSesion();
		// Sprint 9, tanda 3: también los comunicados; el «sí» por WhatsApp a los comunicados puede marcar al apoderado como
		// el contacto principal de su familia (recibe el resumen diario).
		boolean principal = pedido.principal() && pedido.finalidad() == Finalidad.COMUNICADOS
				&& pedido.canal() == Canal.WHATSAPP && pedido.otorga();
		Optional<ConsentimientoAviso> actual = consentimientos.findFirstByApoderadoIdAndFinalidadAndCanalOrderByIdDesc(
				yo.getId(), pedido.finalidad(), pedido.canal());
		boolean vigente = actual.map(ConsentimientoAviso::isOtorgado).orElse(pedido.canal().salvoOposicion());
		boolean eraPrincipal = actual.map(ConsentimientoAviso::isContactoPrincipal).orElse(false);
		if (actual.isPresent() && vigente == pedido.otorga() && eraPrincipal == principal) {
			return;
		}
		ConsentimientoAviso nuevo = consentimientos.saveAndFlush(ConsentimientoAviso.enPortal(yo.getFamilia().getId(),
				yo.getId(), pedido.finalidad(), pedido.canal(), pedido.otorga(), principal, versionAviso(),
				sesiones.de(persona)));
		auditoria.registrar(AccionAuditoria.CONSENTIMIENTO_AVISO, "consentimiento_aviso", nuevo.getId().toString(),
				actual.isEmpty() ? null : String.valueOf(vigente), String.valueOf(pedido.otorga()), "El apoderado "
						+ yo.getId() + (pedido.otorga() ? " aceptó " : " retiró ") + pedido.finalidad().etiqueta()
						.toLowerCase(java.util.Locale.ROOT) + " por " + pedido.canal().etiqueta() + " en el portal.");
	}

	/** Administración o Dirección registra el «sí» de la ficha firmada (E38: la evidencia es obligatoria). */
	@PreAuthorize("hasAnyRole('DIRECTOR','ADMINISTRACION')")
	@Transactional
	public void registrarDesdeFicha(ConsentimientoFichaRequest pedido) {
		UsuarioAutenticado persona = PersonaEnSesion.actual();
		Apoderado apoderado = apoderados.findById(pedido.apoderadoId()).filter(Apoderado::isActivo)
				.orElseThrow(() -> new RecursoNoEncontradoException("Apoderado no encontrado"));
		ConsentimientoAviso nuevo = consentimientos.saveAndFlush(ConsentimientoAviso.desdeFicha(
				apoderado.getFamilia().getId(), apoderado.getId(), pedido.finalidad(), pedido.canal(), pedido.codigoFicha(),
				versionAviso(), sesiones.de(persona)));
		long hoy = consentimientos.countByCreadoPorAndOrigenAndCreadoEnGreaterThanEqual(persona.getUsername(),
				OrigenConsentimiento.FICHA, LocalDate.now(reloj).atStartOfDay());
		auditoria.registrar(AccionAuditoria.CONSENTIMIENTO_AVISO, "consentimiento_aviso", nuevo.getId().toString(), null,
				"true", persona.nombreCompleto() + " registró desde la ficha firmada " + nuevo.getEvidencia()
						+ " el consentimiento de " + pedido.finalidad().etiqueta().toLowerCase(java.util.Locale.ROOT)
						+ " por " + pedido.canal().etiqueta() + " del apoderado " + apoderado.getId() + "."
						+ (hoy > ALERTA_FICHAS_DIA ? " Revisar: " + hoy + " consentimientos desde ficha hoy por la misma "
								+ "persona." : ""));
	}

	private List<ConsentimientoVista> vistas(Apoderado a) {
		List<ConsentimientoVista> vistas = new ArrayList<>();
		for (Finalidad finalidad : Finalidad.values()) {
			for (Canal canal : Canal.values()) {
				Optional<ConsentimientoAviso> fila = consentimientos.findFirstByApoderadoIdAndFinalidadAndCanalOrderByIdDesc(
						a.getId(), finalidad, canal);
				boolean contacto = canal == Canal.WHATSAPP ? a.telefonoVerificado() : a.correoVerificado();
				vistas.add(new ConsentimientoVista(finalidad.name(), finalidad.etiqueta(), canal.name(), canal.etiqueta(),
						fila.map(ConsentimientoAviso::isOtorgado).orElse(canal.salvoOposicion()), fila.isEmpty(),
						fila.map(f -> f.getOrigen() == OrigenConsentimiento.FICHA ? "Ficha de matrícula" : "Portal")
								.orElse(null), fila.map(ConsentimientoAviso::getCreadoEn).orElse(null), contacto));
			}
		}
		return vistas;
	}

	private String versionAviso() {
		return colegios.findById(ContextoColegio.actual()).map(Colegio::getAvisoPrivacidadVersion)
				.filter(v -> v != null && !v.isBlank()).map(v -> v.length() > 20 ? v.substring(0, 20) : v)
				.orElse("SIN_APROBAR");
	}

	private Apoderado apoderadoEnSesion() {
		Long id = PersonaEnSesion.actual().apoderadoId();
		if (id == null) {
			throw new RecursoNoEncontradoException("Apoderado no encontrado");
		}
		return apoderados.findById(id).filter(Apoderado::isActivo)
				.orElseThrow(() -> new RecursoNoEncontradoException("Apoderado no encontrado"));
	}
}
