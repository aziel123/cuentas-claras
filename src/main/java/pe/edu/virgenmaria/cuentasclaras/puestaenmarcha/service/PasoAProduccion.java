package pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ColegioEnProduccion;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto.ListaVerificacion;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto.PuntoLista;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.FirmaOperacionRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * Sprint 8 (secciones 3.4 y 3.5, decisiones 116 y 117): PREPARACION → PRODUCCION, solo Promotoría, con la firma de su
 * sesión ({@code colegio:{id}:PRODUCCION}).
 * <ul>
 *   <li><b>Tanda 2:</b> exige la lista de verificación COMPLETA (los 12 pasos obligatorios de la sección 3.5, calculados de
 *       los datos). En MySQL, trg_colegio_cambio exige además la firma, una Dirección y una Caja activas, y que nadie
 *       vuelva atrás.</li>
 *   <li>Al pasar: evento resaltado {@code COLEGIO_EN_PRODUCCION} y {@link ColegioEnProduccion}, que la mensajería escucha
 *       en la misma transacción para avisar a toda la Promotoría y al correo externo de control.</li>
 *   <li>Los dos «aceptos» de la lista, también con la firma de la Promotoría (la firma es el registro: única por colegio y
 *       clave, de solo inserción) y resaltados en la bitácora: «el colegio empieza sin deudas anteriores»
 *       ({@code colegio:{id}:SIN_SALDO_INICIAL}) y «uso el comprobante simulado y emito mi comprobante legal aparte»
 *       ({@code colegio:{id}:OSE_SIMULADO}).</li>
 * </ul>
 */
@Service
public class PasoAProduccion {

	private final ColegioRepository colegios;

	private final UsuarioRepository usuarios;

	private final EstadoPuestaEnMarcha estado;

	private final FirmaSesion firma;

	private final FirmaOperacionRepository firmas;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	private final Clock reloj;

	private final pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository anios;

	public PasoAProduccion(ColegioRepository colegios, UsuarioRepository usuarios, EstadoPuestaEnMarcha estado,
			FirmaSesion firma, FirmaOperacionRepository firmas, AuditoriaService auditoria, ApplicationEventPublisher eventos,
			Clock reloj, pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository anios) {
		this.anios = anios;
		this.colegios = colegios;
		this.usuarios = usuarios;
		this.estado = estado;
		this.firma = firma;
		this.firmas = firmas;
		this.auditoria = auditoria;
		this.eventos = eventos;
		this.reloj = reloj;
	}

	/** La lista de verificación del colegio en contexto. Sin transacción: la calcula. */
	public ListaVerificacion lista() {
		return estado.actual();
	}

	@PreAuthorize("hasRole('PROMOTOR')")
	@Transactional
	public void pasar() {
		Colegio colegio = colegioActual();
		if (colegio.enProduccion()) {
			throw new ReglaNegocioException("El colegio ya está en producción.");
		}
		if (colegio.getEstado() != pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoColegio.PREPARACION) {
			throw new ReglaNegocioException("El colegio está " + colegio.getEstado().etiqueta().toLowerCase(java.util.Locale.ROOT)
					+ ": solo un colegio en preparación pasa a producción.");
		}
		if (usuarios.contarActivosConRol(Rol.DIRECTOR) == 0 || usuarios.contarActivosConRol(Rol.CAJA) == 0) {
			throw new ReglaNegocioException("Para pasar a producción el colegio necesita al menos una cuenta de Dirección y "
					+ "una de Caja activas, de personas distintas de la Promotoría (quien cobra no aprueba).");
		}
		ListaVerificacion lista = estado.actual();
		if (!lista.completa()) {
			throw new ReglaNegocioException("Para pasar a producción completa la lista de verificación. Faltan: "
					+ pendientes(lista) + ".");
		}
		// Sprint 11, tanda 1 (sección 3.7, F18): sin año escolar no hay producción (la base también lo exige).
		if (anios.findAllByOrderByAnioDesc().stream().noneMatch(a -> !a.cerrado())) {
			throw new ReglaNegocioException("Para pasar a producción el colegio necesita su año escolar con las fechas de "
					+ "clases (paso 3 de la puesta en marcha): lo crea Promotoría o Dirección, con su firma.");
		}
		UsuarioAutenticado promotor = actor();
		LocalDateTime ahora = LocalDateTime.now(reloj);
		firma.firmar(ClaveFirma.colegioProduccion(colegio.getId()));
		colegio.pasarAProduccion(promotor.getUsername(), ahora);
		colegios.saveAndFlush(colegio);
		auditoria.registrar(AccionAuditoria.COLEGIO_EN_PRODUCCION, "colegio", colegio.getId().toString(), "PREPARACION",
				"PRODUCCION", promotor.nombreCompleto() + " pasó el colegio a producción con la firma de su sesión y la "
						+ "lista de verificación completa: desde ahora se registran pagos y salen los recordatorios. Se avisó "
						+ "a toda la Promotoría y al correo externo de control.");
		eventos.publishEvent(new ColegioEnProduccion(colegio.getId(), promotor.getUsername(), ahora));
	}

	/** «El colegio empieza sin deudas anteriores»: la Promotoría lo firma (en vez de un lote de saldo inicial). */
	@PreAuthorize("hasRole('PROMOTOR')")
	@Transactional
	public void sinSaldoInicial() {
		Colegio colegio = colegioActual();
		String clave = ClaveFirma.colegioSinSaldoInicial(colegio.getId());
		exigirSinFirmar(clave, "Ya se firmó que el colegio empieza sin deudas anteriores.");
		UsuarioAutenticado promotor = actor();
		firma.firmar(clave);
		auditoria.registrar(AccionAuditoria.COLEGIO_SIN_SALDO_INICIAL, "colegio", colegio.getId().toString(), null,
				"SIN_SALDO_INICIAL", promotor.nombreCompleto() + " firmó que el colegio empieza sin deudas anteriores de las "
						+ "familias: no se carga un saldo inicial. Si después aparece una deuda anterior, se carga con un lote "
						+ "de saldo inicial que confirma otra persona.");
	}

	/** «Uso el comprobante simulado y emito mi comprobante legal aparte»: la Promotoría lo firma. */
	@PreAuthorize("hasRole('PROMOTOR')")
	@Transactional
	public void aceptarOseSimulado() {
		Colegio colegio = colegioActual();
		String clave = ClaveFirma.colegioOseSimulado(colegio.getId());
		exigirSinFirmar(clave, "Ya se firmó el uso del comprobante simulado.");
		UsuarioAutenticado promotor = actor();
		firma.firmar(clave);
		auditoria.registrar(AccionAuditoria.COLEGIO_OSE_SIMULADO_ACEPTADO, "colegio", colegio.getId().toString(), null,
				"OSE_SIMULADO", promotor.nombreCompleto() + " firmó que el colegio usa el comprobante simulado (sin valor ante "
						+ "SUNAT) y emite su comprobante legal aparte, hasta tener su OSE real en Conectores.");
	}

	private void exigirSinFirmar(String clave, String mensaje) {
		if (firmas.existsByClave(clave)) {
			throw new ReglaNegocioException(mensaje);
		}
	}

	private Colegio colegioActual() {
		Long colegioId = ContextoColegio.actual();
		return colegios.findById(colegioId).filter(c -> !c.esPlataforma())
				.orElseThrow(() -> new RecursoNoEncontradoException("Colegio no encontrado"));
	}

	private static String pendientes(ListaVerificacion lista) {
		return lista.puntos().stream().filter(p -> p.obligatorio() && !p.hecho()).map(p -> p.paso().toLowerCase())
				.collect(Collectors.joining(", "));
	}

	/** Lo que falta, para el texto de la pantalla (vacío si nada). */
	public static String pendientesDe(ListaVerificacion lista) {
		return lista.puntos().stream().filter(p -> p.obligatorio() && !p.hecho()).map(PuntoLista::paso)
				.collect(Collectors.joining(", "));
	}

	private static UsuarioAutenticado actor() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario) {
			return usuario;
		}
		throw new org.springframework.security.access.AccessDeniedException("Se requiere una Promotoría en sesión");
	}
}
