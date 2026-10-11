package pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto.ListaVerificacion;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto.PuntoLista;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * Sprint 8, tanda 1 (sección 3.4, decisión 116): PREPARACION → PRODUCCION, solo Promotoría, con la firma de su sesión
 * ({@code colegio:{id}:PRODUCCION}) y con el equipo mínimo (una Dirección y una Caja activas). En MySQL,
 * trg_colegio_cambio exige lo mismo y que nadie vuelva atrás. En la tanda 1 la condición es el equipo y los datos; la
 * tanda 2 exige la lista completa (sección 3.5).
 */
@Service
public class PasoAProduccion {

	private final ColegioRepository colegios;

	private final UsuarioRepository usuarios;

	private final EstadoPuestaEnMarcha estado;

	private final FirmaSesion firma;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	public PasoAProduccion(ColegioRepository colegios, UsuarioRepository usuarios, EstadoPuestaEnMarcha estado,
			FirmaSesion firma, AuditoriaService auditoria, Clock reloj) {
		this.colegios = colegios;
		this.usuarios = usuarios;
		this.estado = estado;
		this.firma = firma;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	/** Lo que falta para pasar a producción (vacío si ya se puede). Sin transacción: calcula la lista. */
	public ListaVerificacion lista() {
		return estado.actual();
	}

	@PreAuthorize("hasRole('PROMOTOR')")
	@Transactional
	public void pasar() {
		Long colegioId = ContextoColegio.actual();
		Colegio colegio = colegios.findById(colegioId).filter(c -> !c.esPlataforma())
				.orElseThrow(() -> new RecursoNoEncontradoException("Colegio no encontrado"));
		if (colegio.enProduccion()) {
			throw new ReglaNegocioException("El colegio ya está en producción.");
		}
		if (usuarios.contarActivosConRol(Rol.DIRECTOR) == 0 || usuarios.contarActivosConRol(Rol.CAJA) == 0) {
			throw new ReglaNegocioException("Para pasar a producción el colegio necesita al menos una cuenta de Dirección y "
					+ "una de Caja activas, de personas distintas de la Promotoría (quien cobra no aprueba).");
		}
		if (colegio.getRazonSocial() == null || colegio.getRuc() == null || colegio.getDireccion() == null) {
			throw new ReglaNegocioException("Completa la razón social, el RUC y la dirección del colegio antes de pasar a "
					+ "producción: van en cada boleta.");
		}
		UsuarioAutenticado promotor = actor();
		firma.firmar(ClaveFirma.colegioProduccion(colegioId));
		colegio.pasarAProduccion(promotor.getUsername(), LocalDateTime.now(reloj));
		colegios.saveAndFlush(colegio);
		auditoria.registrar(AccionAuditoria.COLEGIO_EN_PRODUCCION, "colegio", colegioId.toString(), "PREPARACION",
				"PRODUCCION", promotor.nombreCompleto() + " pasó el colegio a producción con la firma de su sesión: desde ahora "
						+ "se registran pagos y salen los recordatorios. Pendientes de la lista: " + pendientes());
	}

	private String pendientes() {
		// Dentro de la transacción: la lista del colegio en contexto (sin cambiar de colegio).
		ListaVerificacion lista;
		try {
			lista = estado.actual();
		}
		catch (RuntimeException e) {
			return "no calculados";
		}
		String texto = lista.puntos().stream().filter(p -> p.obligatorio() && !p.hecho()).map(PuntoLista::paso)
				.collect(Collectors.joining(", "));
		return texto.isEmpty() ? "ninguno" : texto;
	}

	private static UsuarioAutenticado actor() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario) {
			return usuario;
		}
		throw new org.springframework.security.access.AccessDeniedException("Se requiere una Promotoría en sesión");
	}
}
