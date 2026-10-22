package pe.edu.virgenmaria.cuentasclaras.comunicados.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comunicados.dto.ComunicadoParaFamilia;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.Comunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.ComunicadoAdjunto;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.ComunicadoDestinatario;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.ConfirmacionLectura;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.EstadoComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.repository.ComunicadoAdjuntoRepository;
import pe.edu.virgenmaria.cuentasclaras.comunicados.repository.ComunicadoDestinatarioRepository;
import pe.edu.virgenmaria.cuentasclaras.comunicados.repository.ComunicadoRepository;
import pe.edu.virgenmaria.cuentasclaras.comunicados.repository.ConfirmacionLecturaRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sprint 9, tanda 3 (sección 3.10, E23 a E25, E28): los comunicados en el portal de la familia. Solo los de la familia del
 * apoderado en sesión (de otra familia, aunque sea del mismo colegio: 404). Abrirlo marca «abierto» la primera vez;
 * «Confirmo que lo leí» es un POST con CSRF desde la sesión de un apoderado de la familia destinataria (no hay enlace que
 * confirme). Uno retirado muestra la constancia, no el texto. El mismo trato con deuda o sin ella (INDECOPI, E39).
 */
@Service
@PreAuthorize("hasRole('APODERADO')")
public class LecturaFamilia {

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final ComunicadoRepository comunicados;

	private final ComunicadoDestinatarioRepository destinatarios;

	private final ConfirmacionLecturaRepository confirmaciones;

	private final ComunicadoAdjuntoRepository adjuntos;

	private final ApoderadoRepository apoderados;

	private final UsuarioRepository usuarios;

	private final SesionDelEmisor sesiones;

	private final PieComunicado pie;

	private final Clock reloj;

	public LecturaFamilia(ComunicadoRepository comunicados, ComunicadoDestinatarioRepository destinatarios,
			ConfirmacionLecturaRepository confirmaciones, ComunicadoAdjuntoRepository adjuntos,
			ApoderadoRepository apoderados, UsuarioRepository usuarios, SesionDelEmisor sesiones, PieComunicado pie,
			Clock reloj) {
		this.comunicados = comunicados;
		this.destinatarios = destinatarios;
		this.confirmaciones = confirmaciones;
		this.adjuntos = adjuntos;
		this.apoderados = apoderados;
		this.usuarios = usuarios;
		this.sesiones = sesiones;
		this.pie = pie;
		this.reloj = reloj;
	}

	/** Los comunicados de la familia, del más reciente al más antiguo (sin el texto). */
	@Transactional(readOnly = true)
	public List<ComunicadoParaFamilia> mios() {
		Apoderado yo = apoderadoEnSesion();
		List<ComunicadoDestinatario> mias = destinatarios.findByFamiliaIdOrderByIdDesc(yo.getFamilia().getId());
		Map<Long, Comunicado> porId = comunicados.findByIdIn(mias.stream().map(ComunicadoDestinatario::getComunicadoId)
				.toList()).stream().collect(Collectors.toMap(Comunicado::getId, Function.identity()));
		return mias.stream().filter(d -> porId.containsKey(d.getComunicadoId()))
				.map(d -> vista(porId.get(d.getComunicadoId()), d, false))
				.sorted(Comparator.comparing(ComunicadoParaFamilia::enviadoEn, Comparator.nullsLast(Comparator
						.reverseOrder())))
				.toList();
	}

	/** Abre un comunicado de la familia (la primera vez queda «abierto»). */
	@Transactional
	public ComunicadoParaFamilia abrir(long id) {
		Apoderado yo = apoderadoEnSesion();
		ComunicadoDestinatario destinatario = destinatario(id, yo);
		Comunicado c = comunicados.findById(id).orElseThrow(LecturaFamilia::noEncontrado);
		ComunicadoParaFamilia vista = vista(c, destinatario, true);
		if (destinatario.marcarAbierto(ahora())) {
			destinatarios.saveAndFlush(destinatario);
		}
		return vista;
	}

	/** «Confirmo que lo leí», con la cuenta y la sesión del apoderado (una por familia). */
	@Transactional
	public void confirmar(long id) {
		UsuarioAutenticado persona = SesionDelEmisor.persona();
		Apoderado yo = apoderadoEnSesion();
		destinatario(id, yo);
		Comunicado c = comunicados.findById(id).orElseThrow(LecturaFamilia::noEncontrado);
		if (c.getEstado() != EstadoComunicado.ENVIADO) {
			throw new ReglaNegocioException("Este comunicado fue retirado: ya no se confirma.");
		}
		if (!c.isRequiereConfirmacion()) {
			throw new ReglaNegocioException("Este comunicado no pide confirmación de lectura.");
		}
		if (confirmaciones.existsByComunicadoIdAndFamiliaId(id, yo.getFamilia().getId())) {
			return;
		}
		confirmaciones.saveAndFlush(ConfirmacionLectura.de(id, yo.getFamilia().getId(), yo.getId(), persona.usuarioId(),
				sesiones.de(persona)));
	}

	/** El PDF de un comunicado ENVIADO de la familia. */
	@Transactional(readOnly = true)
	public byte[] adjunto(long id) {
		Apoderado yo = apoderadoEnSesion();
		destinatario(id, yo);
		Comunicado c = comunicados.findById(id).orElseThrow(LecturaFamilia::noEncontrado);
		if (c.getEstado() != EstadoComunicado.ENVIADO || c.getAdjuntoId() == null) {
			throw new RecursoNoEncontradoException("Adjunto no encontrado");
		}
		return adjuntos.findById(c.getAdjuntoId()).map(ComunicadoAdjunto::getContenido)
				.orElseThrow(() -> new RecursoNoEncontradoException("Adjunto no encontrado"));
	}

	private ComunicadoParaFamilia vista(Comunicado c, ComunicadoDestinatario d, boolean conTexto) {
		boolean retirado = c.getEstado() == EstadoComunicado.RETIRADO;
		boolean confirmado = confirmaciones.existsByComunicadoIdAndFamiliaId(c.getId(), d.getFamiliaId());
		List<Long> fe = comunicados.findByCorrigeIdOrderByIdAsc(c.getId()).stream()
				.filter(x -> x.getEstado() == EstadoComunicado.ENVIADO).map(Comunicado::getId).toList();
		String constancia = retirado ? "Este comunicado fue retirado por " + quienRetiro(c) + " el "
				+ c.getRetiradoEn().format(FECHA) + ". Motivo: " + c.getMotivoRetiro() : null;
		return new ComunicadoParaFamilia(c.getId(), c.getTitulo(), c.getEmisorEtiqueta(), c.getEnviadoEn(),
				conTexto && !retirado ? EnvioComunicados.parrafos(c.getCuerpo()) : List.of(), pie.texto(), c.isUrgente(),
				c.isRequiereConfirmacion() && !retirado, confirmado, d.getAbiertoEn() == null, retirado, constancia,
				!retirado && c.getAdjuntoId() != null, c.getCorrigeId(), fe);
	}

	private String quienRetiro(Comunicado c) {
		return usuarios.findByNombreUsuario(c.getRetiradoPor())
				.map(u -> u.getRoles().contains(Rol.DIRECTOR) ? "Dirección" : "Promotoría").orElse("el colegio");
	}

	private ComunicadoDestinatario destinatario(long id, Apoderado yo) {
		return destinatarios.findByComunicadoIdAndFamiliaId(id, yo.getFamilia().getId())
				.orElseThrow(LecturaFamilia::noEncontrado);
	}

	private Apoderado apoderadoEnSesion() {
		Long id = SesionDelEmisor.persona().apoderadoId();
		if (id == null) {
			throw new RecursoNoEncontradoException("Apoderado no encontrado");
		}
		return apoderados.findById(id).filter(Apoderado::isActivo)
				.orElseThrow(() -> new RecursoNoEncontradoException("Apoderado no encontrado"));
	}

	private LocalDateTime ahora() {
		return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
	}

	private static RecursoNoEncontradoException noEncontrado() {
		return new RecursoNoEncontradoException("Comunicado no encontrado");
	}
}
