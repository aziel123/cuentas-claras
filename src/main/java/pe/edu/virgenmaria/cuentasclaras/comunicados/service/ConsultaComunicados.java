package pe.edu.virgenmaria.cuentasclaras.comunicados.service;

import org.springframework.data.domain.Limit;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.api.SeccionesDocente;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comunicados.dto.ComunicadoVista;
import pe.edu.virgenmaria.cuentasclaras.comunicados.dto.DetalleComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.Comunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.ComunicadoAdjunto;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.EstadoComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.repository.ComunicadoAdjuntoRepository;
import pe.edu.virgenmaria.cuentasclaras.comunicados.repository.ComunicadoRepository;
import pe.edu.virgenmaria.cuentasclaras.comunicados.repository.ConfirmacionLecturaRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.util.List;

/**
 * Sprint 9, tanda 3 (secciones 3.8 a 3.10): lo que ve el personal. Dirección y Promotoría ven todos los comunicados del
 * colegio (y los retiran); el docente, solo los suyos (los de otro: 404, E18). El PDF solo se descarga de un comunicado
 * ENVIADO (B8), salvo que esté POR_APROBAR y quien lo pide lo pueda aprobar en la bandeja.
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','DOCENTE')")
public class ConsultaComunicados {

	private static final int MAX_LISTA = 200;

	private final ComunicadoRepository comunicados;

	private final ComunicadoAdjuntoRepository adjuntos;

	private final ConfirmacionLecturaRepository confirmaciones;

	private final EstadisticasComunicado estadisticas;

	private final SeccionesDocente seccionesDocente;

	private final EnvioComunicados envio;

	private final PieComunicado pie;

	public ConsultaComunicados(ComunicadoRepository comunicados, ComunicadoAdjuntoRepository adjuntos,
			ConfirmacionLecturaRepository confirmaciones, EstadisticasComunicado estadisticas,
			SeccionesDocente seccionesDocente, EnvioComunicados envio, PieComunicado pie) {
		this.comunicados = comunicados;
		this.adjuntos = adjuntos;
		this.confirmaciones = confirmaciones;
		this.estadisticas = estadisticas;
		this.seccionesDocente = seccionesDocente;
		this.envio = envio;
		this.pie = pie;
	}

	/** Los comunicados que ve la persona en sesión, del más reciente al más antiguo. */
	@Transactional(readOnly = true)
	public List<ComunicadoVista> lista() {
		UsuarioAutenticado persona = SesionDelEmisor.persona();
		List<Comunicado> todos = esDireccion(persona) ? comunicados.findAllByOrderByIdDesc(Limit.of(MAX_LISTA))
				: comunicados.findByEmisorUsuarioIdOrderByIdDesc(persona.usuarioId(), Limit.of(MAX_LISTA));
		return todos.stream().map(this::vista).toList();
	}

	@Transactional(readOnly = true)
	public DetalleComunicado detalle(long id) {
		UsuarioAutenticado persona = SesionDelEmisor.persona();
		Comunicado c = visible(id, persona);
		boolean direccion = esDireccion(persona);
		// B16: la lista de pendientes (con nombres de alumnos) solo con la asignación VIGENTE en la sección.
		boolean pendientes = direccion || (c.getSeccionId() != null && seccionesDocente.mias().stream()
				.anyMatch(s -> s.seccionId().equals(c.getSeccionId())));
		boolean suyo = c.getEmisorUsuarioId().equals(persona.usuarioId());
		boolean corregible = c.getDestinatarios() != null && ((c.getEstado() == EstadoComunicado.ENVIADO
				&& (direccion || suyo)) || (c.getEstado() == EstadoComunicado.RETIRADO && direccion));
		List<ComunicadoVista> fe = comunicados.findByCorrigeIdOrderByIdAsc(c.getId()).stream().map(this::vista).toList();
		return new DetalleComunicado(vista(c), EnvioComunicados.parrafos(c.getCuerpo()), pie.texto(),
				c.isRequiereConfirmacion(), c.getCorrigeId(), fe, estadisticas.de(c, pendientes),
				direccion && c.getEstado() == EstadoComunicado.ENVIADO, corregible, c.getRetiradoPor(), c.getRetiradoEn(),
				c.getMotivoRetiro(), c.getAdjuntoId() != null && adjuntoDescargable(c, persona), c.getSolicitudId());
	}

	/** El PDF saneado (B8: 404 si el comunicado no está ENVIADO, salvo para quien lo puede aprobar en la bandeja). */
	@Transactional(readOnly = true)
	public byte[] adjunto(long id) {
		UsuarioAutenticado persona = SesionDelEmisor.persona();
		Comunicado c = esDireccion(persona) ? comunicados.findById(id).orElseThrow(EnvioComunicados::noEncontrado)
				: visible(id, persona);
		if (c.getAdjuntoId() == null || !adjuntoDescargable(c, persona)) {
			throw new RecursoNoEncontradoException("Adjunto no encontrado");
		}
		return adjuntos.findById(c.getAdjuntoId()).map(ComunicadoAdjunto::getContenido)
				.orElseThrow(() -> new RecursoNoEncontradoException("Adjunto no encontrado"));
	}

	private boolean adjuntoDescargable(Comunicado c, UsuarioAutenticado persona) {
		if (c.getEstado() == EstadoComunicado.ENVIADO) {
			return true;
		}
		return c.getEstado() == EstadoComunicado.POR_APROBAR && !c.getEmisorUsuarioId().equals(persona.usuarioId())
				&& persona.roles().stream().anyMatch(c.getEmisorRol()::loApruebaQuienTiene);
	}

	/** Dirección y Promotoría ven todos; el docente, solo los suyos (los de otro «no existen»: 404). */
	private Comunicado visible(long id, UsuarioAutenticado persona) {
		Comunicado c = comunicados.findById(id).orElseThrow(EnvioComunicados::noEncontrado);
		if (!esDireccion(persona) && !c.getEmisorUsuarioId().equals(persona.usuarioId())) {
			throw EnvioComunicados.noEncontrado();
		}
		return c;
	}

	private static boolean esDireccion(UsuarioAutenticado persona) {
		return persona.roles().contains(Rol.DIRECTOR) || persona.roles().contains(Rol.PROMOTOR);
	}

	private ComunicadoVista vista(Comunicado c) {
		return new ComunicadoVista(c.getId(), c.getTitulo(), c.getEmisorEtiqueta(),
				envio.textoAlcance(AlcanceDeFamilias.Alcance.de(c)), c.getEstado().name(), estadoTexto(c),
				c.getEnviadoEn(), c.getDestinatarios(), confirmaciones.countByComunicadoId(c.getId()), c.esFeDeErratas(),
				c.isUrgente(), c.getAdjuntoId() != null);
	}

	static String estadoTexto(Comunicado c) {
		return switch (c.getEstado()) {
			case POR_APROBAR -> "Por aprobar";
			case ENVIADO -> c.esFeDeErratas() ? "Fe de erratas enviada" : "Enviado";
			case RECHAZADO -> "Rechazado";
			case RETIRADO -> "Retirado";
		};
	}
}
