package pe.edu.virgenmaria.cuentasclaras.comunicados.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.academico.api.SeccionesDocente;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.SeccionDocente;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.RegistroSolicitudes;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoAnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.ContenidoSinCobranza;
import pe.edu.virgenmaria.cuentasclaras.comunicados.dto.AdjuntoValidado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.dto.BorradorComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.dto.OpcionSeccionComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.dto.OpcionesComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.dto.VistaPrevia;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.AlcanceComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.Comunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.ComunicadoAdjunto;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.ComunicadoDestinatario;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.EmisorRol;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.EstadoComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.repository.ComunicadoAdjuntoRepository;
import pe.edu.virgenmaria.cuentasclaras.comunicados.repository.ComunicadoDestinatarioRepository;
import pe.edu.virgenmaria.cuentasclaras.comunicados.repository.ComunicadoRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Sprint 9, tanda 3 (secciones 3.8 y 3.9; E16 a E22, E27 a E29; B1, B2, B3, B4, B9, B13): escribir y enviar comunicados.
 * <ul>
 *   <li><b>Nadie se hace pasar por Dirección:</b> el emisor se elige entre los roles REALES de la cuenta y la etiqueta la
 *       arma {@link EtiquetaEmisor}; el docente, solo a una sección con su asignación VIGENTE (no auxiliar) y sin urgente.</li>
 *   <li><b>Filtro sin excepciones</b> ({@link FiltroContenido}, también para Dirección): el rechazo queda en la bitácora con
 *       la regla (sin el texto) aunque no se envíe nada.</li>
 *   <li><b>Firma del envío</b> ({@code comunicado:{huella40}:ENVIAR}, también si queda POR_APROBAR) y huella del
 *       contenido que recalcula la base (B2): el mismo contenido no sale dos veces.</li>
 *   <li><b>Todo PDF</b> (decisión del usuario en las correcciones del sprint 9, sea quien sea el emisor): POR_APROBAR
 *       con su {@code PUBLICAR_COMUNICADO}, que aprueba el otro órgano en la bandeja. {@code trg_comunicado_nace} lo
 *       exige sin mirar {@code requiere_revision}.</li>
 *   <li><b>Destinatarios congelados</b> al enviarse; la fe de erratas llega a TODOS los del original (B1).</li>
 *   <li><b>Retirar no borra</b> (E28): Dirección o Promotoría, con su firma y motivo.</li>
 * </ul>
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','DOCENTE')")
public class EnvioComunicados {

	public static final String ENTIDAD = "comunicado";

	private final ComunicadoRepository comunicados;

	private final ComunicadoDestinatarioRepository destinatarios;

	private final ComunicadoAdjuntoRepository adjuntos;

	private final AnioEscolarRepository anios;

	private final SeccionRepository secciones;

	private final SeccionesDocente seccionesDocente;

	private final EtiquetaEmisor etiquetas;

	private final FiltroContenido filtro;

	private final AlcanceDeFamilias alcances;

	private final AdjuntosComunicado adjuntosComunicado;

	private final PieComunicado pie;

	private final FirmaSesion firma;

	private final SesionDelEmisor sesiones;

	private final RegistroSolicitudes solicitudes;

	private final AuditoriaService auditoria;

	private final IntegridadComunicados integridad;

	private final Clock reloj;

	private final TransactionTemplate transaccion;

	/** El rechazo del filtro queda en la bitácora aunque la transacción del envío no siga (REQUIRES_NEW). */
	private final TransactionTemplate aparte;

	public EnvioComunicados(ComunicadoRepository comunicados, ComunicadoDestinatarioRepository destinatarios,
			ComunicadoAdjuntoRepository adjuntos, AnioEscolarRepository anios, SeccionRepository secciones,
			SeccionesDocente seccionesDocente, EtiquetaEmisor etiquetas, FiltroContenido filtro, AlcanceDeFamilias alcances,
			AdjuntosComunicado adjuntosComunicado, PieComunicado pie, FirmaSesion firma, SesionDelEmisor sesiones,
			RegistroSolicitudes solicitudes, AuditoriaService auditoria, IntegridadComunicados integridad, Clock reloj,
			PlatformTransactionManager transacciones) {
		this.comunicados = comunicados;
		this.destinatarios = destinatarios;
		this.adjuntos = adjuntos;
		this.anios = anios;
		this.secciones = secciones;
		this.seccionesDocente = seccionesDocente;
		this.etiquetas = etiquetas;
		this.filtro = filtro;
		this.alcances = alcances;
		this.adjuntosComunicado = adjuntosComunicado;
		this.pie = pie;
		this.firma = firma;
		this.sesiones = sesiones;
		this.solicitudes = solicitudes;
		this.auditoria = auditoria;
		this.integridad = integridad;
		this.reloj = reloj;
		this.transaccion = new TransactionTemplate(transacciones);
		this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
		this.aparte = new TransactionTemplate(transacciones);
		this.aparte.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	/** Con qué roles puede firmar la persona en sesión y a qué alcances (el docente, solo sus secciones). */
	@Transactional(readOnly = true)
	public OpcionesComunicado opciones() {
		UsuarioAutenticado persona = SesionDelEmisor.persona();
		List<EmisorRol> emisores = emisoresDe(persona);
		boolean direccion = emisores.contains(EmisorRol.DIRECCION) || emisores.contains(EmisorRol.PROMOTORIA);
		List<OpcionSeccionComunicado> opciones = new ArrayList<>();
		if (direccion) {
			anios.findByEstado(EstadoAnioEscolar.EN_CURSO).ifPresent(a -> secciones
					.findByAnioEscolarIdOrderByGradoAscNombreAsc(a.getId()).stream().filter(Seccion::isActiva)
					.forEach(s -> opciones.add(new OpcionSeccionComunicado(s.getId(), s.etiqueta()))));
		}
		else {
			seccionesDeDocente().forEach(s -> opciones.add(new OpcionSeccionComunicado(s.seccionId(), s.seccion())));
		}
		List<AlcanceComunicado> alcancesPosibles = direccion ? Arrays.asList(AlcanceComunicado.values())
				: List.of(AlcanceComunicado.SECCION);
		return new OpcionesComunicado(emisores, alcancesPosibles, Arrays.asList(Nivel.values()),
				Arrays.asList(Grado.values()), opciones, direccion, !emisores.isEmpty());
	}

	/** Cómo lo verá la familia y a cuántas familias llega. No guarda nada (salvo el rechazo del filtro en la bitácora). */
	public VistaPrevia previsualizar(BorradorComunicado borrador, AdjuntoValidado adjunto, Long corrigeId) {
		return transaccion.execute(t -> {
			t.setRollbackOnly();
			Preparado p = preparar(borrador, adjunto, corrigeId);
			boolean porAprobar = adjunto != null || p.ambigua() != null;
			return new VistaPrevia(p.etiqueta(), textoAlcance(p.alcance()), p.familias().size(), p.titulo(),
					parrafos(p.cuerpo()), pie.texto(), borrador.urgente(), borrador.requiereConfirmacion(),
					adjunto == null ? null : adjunto.paginas(), porAprobar, porAprobar ? quienAprueba(p.emisor()) : null);
		});
	}

	/** Envía un comunicado (o lo deja por aprobar). Devuelve su id. */
	public Long enviar(BorradorComunicado borrador, AdjuntoValidado adjunto) {
		return transaccion.execute(t -> guardar(preparar(borrador, adjunto, null), adjunto, null));
	}

	/**
	 * La fe de erratas de un comunicado (que nunca se edita): un comunicado nuevo al MISMO alcance, del mismo emisor o de
	 * Dirección o Promotoría; la de uno retirado, solo Dirección o Promotoría (B9). Llega a TODOS los del original (B1).
	 */
	public Long enviarFeDeErratas(long originalId, BorradorComunicado borrador, AdjuntoValidado adjunto) {
		return transaccion.execute(t -> {
			Preparado p = preparar(borrador, adjunto, originalId);
			return guardar(p, adjunto, p.original());
		});
	}

	/** Dirección o Promotoría retira un comunicado enviado, con su firma y un motivo que verán las familias. */
	@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
	@Transactional
	public void retirar(long id, String motivo) {
		UsuarioAutenticado persona = SesionDelEmisor.persona();
		Comunicado c = comunicados.findById(id).orElseThrow(EnvioComunicados::noEncontrado);
		if (c.getEstado() != EstadoComunicado.ENVIADO) {
			throw new ReglaNegocioException("Solo se retira un comunicado enviado.");
		}
		firma.firmar(ClaveFirma.comunicadoRetirado(c.getId()));
		c.retirar(persona.getUsername(), motivo, ahora());
		comunicados.saveAndFlush(c);
		auditoria.registrar(AccionAuditoria.COMUNICADO_RETIRADO, ENTIDAD, c.getId().toString(), "ENVIADO", "RETIRADO",
				persona.nombreCompleto() + " retiró el comunicado " + c.getId() + " (" + c.getEmisorEtiqueta() + "). Motivo: "
						+ c.getMotivoRetiro() + ". Las familias ven la constancia; el texto queda para Dirección.");
	}

	/**
	 * Congela los destinatarios de un comunicado ya ENVIADO (al enviarse o al aprobarse) y cierra la lista. Normal: las
	 * familias con un alumno ACTIVO en el alcance; fe de erratas: TODAS las del original (B1). Lo usa también la bandeja.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public int congelarDestinatarios(Comunicado c) {
		Map<Long, Integer> familias = new LinkedHashMap<>();
		if (c.esFeDeErratas()) {
			destinatarios.findByComunicadoIdOrderByIdAsc(c.getCorrigeId())
					.forEach(d -> familias.put(d.getFamiliaId(), d.getAlumnos()));
		}
		else {
			familias.putAll(alcances.familias(c.getAnioEscolarId(), AlcanceDeFamilias.Alcance.de(c)));
		}
		for (Map.Entry<Long, Integer> f : familias.entrySet()) {
			destinatarios.save(ComunicadoDestinatario.nuevo(c.getId(), f.getKey(), f.getValue()));
		}
		c.cerrarDestinatarios(familias.size());
		comunicados.saveAndFlush(c);
		return familias.size();
	}

	/** El evento del envío, con la huella del contenido (la compara «Verificar integridad», E19). */
	@Transactional(propagation = Propagation.MANDATORY)
	public void registrarEnvio(Comunicado c, String shaAdjunto) {
		AccionAuditoria accion = c.esFeDeErratas() ? AccionAuditoria.FE_DE_ERRATAS_ENVIADA
				: AccionAuditoria.COMUNICADO_ENVIADO;
		auditoria.registrar(accion, ENTIDAD, c.getId().toString(), c.esFeDeErratas() ? "corrige " + c.getCorrigeId()
				: null, c.getHuellaContenido(), c.getEmisorEtiqueta() + " a " + textoAlcance(AlcanceDeFamilias.Alcance.de(c))
						+ ": " + c.getDestinatarios() + " familia(s)" + (shaAdjunto == null ? "" : "; PDF " + shaAdjunto)
						+ (c.isUrgente() ? "; urgente" : "") + ".");
		integridad.revisarDestinatarios(c);
	}

	private Long guardar(Preparado p, AdjuntoValidado adjunto, Comunicado original) {
		if (original != null) {
			// La fe de erratas se vuelve a comprobar dentro de la transacción (el original pudo cambiar de estado).
			Comunicado vigente = comunicados.findById(original.getId()).orElseThrow(EnvioComunicados::noEncontrado);
			exigirCorregible(vigente, p.emisor(), SesionDelEmisor.persona());
		}
		String shaAdjunto = null;
		Long adjuntoId = null;
		if (adjunto != null) {
			ComunicadoAdjunto guardado = adjuntosComunicado.guardar(adjunto);
			adjuntoId = guardado.getId();
			shaAdjunto = guardado.getSha256();
		}
		AlcanceDeFamilias.Alcance alcance = p.alcance();
		String huella = HuellaComunicado.de(alcance.tipo(), alcance.nivel(), alcance.grado(), alcance.seccionId(),
				p.titulo(), p.cuerpo(), shaAdjunto);
		if (comunicados.existsByHuellaContenido(huella)) {
			throw new ReglaNegocioException("Ya se envió un comunicado con este mismo contenido a este alcance: para "
					+ "corregirlo, envía una fe de erratas.");
		}
		boolean porAprobar = adjunto != null || p.ambigua() != null;
		firma.firmar(ClaveFirma.comunicadoEnviar(huella));
		Comunicado c = comunicados.saveAndFlush(Comunicado.nuevo(p.anio().getId(), original == null ? null
				: original.getId(), new Comunicado.Emisor(p.persona().usuarioId(), p.emisor(), p.etiqueta(),
						sesiones.de(p.persona())), new Comunicado.Contenido(alcance.tipo(), alcance.nivel(), alcance.grado(),
								alcance.seccionId(), p.titulo(), p.cuerpo(), p.urgente(), p.requiereConfirmacion()),
				adjuntoId, huella, porAprobar, porAprobar ? null : ahora()));
		if (porAprobar) {
			Map<String, String> datos = new LinkedHashMap<>();
			datos.put("emisor", p.emisor().name());
			if (shaAdjunto != null) {
				datos.put("adjunto", shaAdjunto);
			}
			if (p.ambigua() != null) {
				datos.put(DATO_PALABRA, p.ambigua());
			}
			String porque = (adjunto == null ? "" : "con PDF")
					+ (adjunto != null && p.ambigua() != null ? " y " : "")
					+ (p.ambigua() == null ? "" : "con «" + p.ambigua() + "»: " + ContenidoSinCobranza.MOTIVO_AMBIGUO);
			SolicitudCambio solicitud = solicitudes.crear(TipoSolicitud.PUBLICAR_COMUNICADO, ENTIDAD, c.getId(),
					"Publicar «" + p.titulo() + "» de " + p.etiqueta() + " a " + textoAlcance(alcance) + " (" + porque + ")",
					datos, (adjunto != null && adjunto.requiereRevision() ? "El PDF tiene poco texto o imágenes. " : "")
							+ (p.ambigua() == null ? "" : "El texto dice «" + p.ambigua() + "», que "
									+ ContenidoSinCobranza.MOTIVO_AMBIGUO + ". ")
							+ "Lo revisa " + quienAprueba(p.emisor()) + " antes de salir.");
			c.ligarSolicitud(solicitud.getId());
			comunicados.saveAndFlush(c);
			auditoria.registrar(AccionAuditoria.COMUNICADO_POR_APROBAR, ENTIDAD, c.getId().toString(), null, huella,
					p.etiqueta() + " dejó un comunicado " + porque + " a " + textoAlcance(alcance) + " por aprobar "
							+ "(solicitud " + solicitud.getId() + "; lo aprueba " + quienAprueba(p.emisor()) + ").");
			return c.getId();
		}
		congelarDestinatarios(c);
		registrarEnvio(c, shaAdjunto);
		return c.getId();
	}

	/** Lo que se valida antes de abrir la transacción (el rechazo del filtro queda en la bitácora igual). */
	private Preparado preparar(BorradorComunicado borrador, AdjuntoValidado adjunto, Long corrigeId) {
		Objects.requireNonNull(borrador, "borrador");
		UsuarioAutenticado persona = SesionDelEmisor.persona();
		EmisorRol emisor = borrador.emisor();
		if (emisor == null || !emisoresDe(persona).contains(emisor)) {
			throw new ReglaNegocioException("No puedes firmar como " + (emisor == null ? "ese emisor"
					: emisor.etiqueta()) + ": el comunicado sale con el rol de tu cuenta.");
		}
		AnioEscolar anio = anios.findByEstado(EstadoAnioEscolar.EN_CURSO)
				.orElseThrow(() -> new ReglaNegocioException("No hay un año escolar en curso."));
		Comunicado original = null;
		AlcanceDeFamilias.Alcance alcance;
		if (corrigeId != null) {
			original = comunicados.findById(corrigeId).orElseThrow(EnvioComunicados::noEncontrado);
			exigirCorregible(original, emisor, persona);
			alcance = AlcanceDeFamilias.Alcance.de(original);
		}
		else {
			alcance = alcance(borrador, emisor, anio);
		}
		if (emisor == EmisorRol.DOCENTE && borrador.urgente()) {
			throw new ReglaNegocioException("Un docente no envía comunicados urgentes: pídeselo a Dirección.");
		}
		String titulo = HuellaComunicado.normalizarTitulo(FiltroContenido.sinCaracteresDeFormato(borrador.titulo()));
		String cuerpo = HuellaComunicado.normalizarCuerpo(FiltroContenido.sinCaracteresDeFormato(borrador.cuerpo()));
		if (titulo.length() < Comunicado.TITULO_MIN || titulo.length() > Comunicado.TITULO_MAX) {
			throw new ReglaNegocioException("El título va de 5 a 120 caracteres.");
		}
		if (cuerpo.length() < Comunicado.CUERPO_MIN || cuerpo.length() > Comunicado.CUERPO_MAX) {
			throw new ReglaNegocioException("El texto va de 10 a 4,000 caracteres.");
		}
		FiltroContenido.Resultado revision = filtro.revisar(titulo + "\n" + cuerpo);
		if (!revision.aceptado()) {
			String detalle = persona.nombreCompleto() + " (" + emisor.etiqueta() + ") intentó enviar un comunicado a "
					+ textoAlcance(alcance) + " que el filtro rechazó: " + revision.motivo()
					+ (revision.regla().deCobranza() ? ". Revisar: es de cobranza o de pagos"
							+ (emisor == EmisorRol.DOCENTE ? " y lo intentó un docente" : "") : "")
					+ ".";
			aparte.executeWithoutResult(t -> auditoria.registrar(AccionAuditoria.COMUNICADO_RECHAZADO_POR_CONTENIDO,
					ENTIDAD, null, null, revision.regla().name(), detalle));
			throw new ReglaNegocioException("El comunicado no se puede enviar: " + revision.motivo()
					+ (revision.fragmento() == null ? "" : " («" + revision.fragmento() + "»)") + ". Los comunicados no "
					+ "tratan cobranzas ni piden pagos, cuentas, contactos ni enlaces a otros sitios.");
		}
		Map<Long, Integer> familias = original != null
				? familiasDelOriginal(original)
				: alcances.familias(anio.getId(), alcance);
		if (familias.isEmpty()) {
			throw new ReglaNegocioException("No hay familias con alumnos matriculados en ese alcance.");
		}
		String etiqueta = etiquetas.de(persona, emisor, alcance.seccionId());
		String ambigua = filtro.paraAprobacion(titulo + "\n" + cuerpo).orElse(null);
		return new Preparado(persona, emisor, etiqueta, anio, alcance, titulo, cuerpo, borrador.urgente(),
				borrador.requiereConfirmacion(), familias, original, ambigua);
	}

	private Map<Long, Integer> familiasDelOriginal(Comunicado original) {
		Map<Long, Integer> familias = new LinkedHashMap<>();
		destinatarios.findByComunicadoIdOrderByIdAsc(original.getId())
				.forEach(d -> familias.put(d.getFamiliaId(), d.getAlumnos()));
		return familias;
	}

	/** B9 y E20: el original enviado (del mismo emisor, o Dirección o Promotoría) o retirado (solo Dirección o Promotoría). */
	private void exigirCorregible(Comunicado original, EmisorRol emisor, UsuarioAutenticado persona) {
		boolean direccion = emisor == EmisorRol.DIRECCION || emisor == EmisorRol.PROMOTORIA;
		boolean suyo = original.getEmisorUsuarioId().equals(persona.usuarioId());
		if (!direccion && !suyo) {
			throw noEncontrado();
		}
		if (original.getEstado() == EstadoComunicado.RETIRADO && !direccion) {
			throw new ReglaNegocioException("La fe de erratas de un comunicado retirado la envía Dirección o Promotoría.");
		}
		if (original.getEstado() != EstadoComunicado.ENVIADO && original.getEstado() != EstadoComunicado.RETIRADO) {
			throw new ReglaNegocioException("Solo se corrige un comunicado que ya salió.");
		}
		if (original.getDestinatarios() == null) {
			throw new ReglaNegocioException("Ese comunicado todavía no tiene su lista de familias cerrada.");
		}
	}

	private AlcanceDeFamilias.Alcance alcance(BorradorComunicado b, EmisorRol emisor, AnioEscolar anio) {
		AlcanceComunicado tipo = Objects.requireNonNull(b.alcance(), "alcance");
		if (emisor == EmisorRol.DOCENTE) {
			if (tipo != AlcanceComunicado.SECCION || b.seccionId() == null) {
				throw new ReglaNegocioException("Un docente escribe solo a una de sus secciones.");
			}
			// 404 si no es suya (o es solo auxiliar en ella): la sección «no existe» para él.
			seccionesDocente.exigir(b.seccionId(), false);
			if (seccionesDeDocente().stream().noneMatch(s -> s.seccionId().equals(b.seccionId()))) {
				throw new RecursoNoEncontradoException("Sección no encontrada");
			}
		}
		return switch (tipo) {
			case COLEGIO -> new AlcanceDeFamilias.Alcance(tipo, null, null, null);
			case NIVEL -> new AlcanceDeFamilias.Alcance(tipo, exigido(b.nivel(), "Elige el nivel."), null, null);
			case GRADO -> new AlcanceDeFamilias.Alcance(tipo, null, exigido(b.grado(), "Elige el grado."), null);
			case SECCION -> {
				Seccion s = secciones.findById(exigido(b.seccionId(), "Elige la sección."))
						.filter(x -> x.getAnioEscolar().getId().equals(anio.getId()) && x.isActiva())
						.orElseThrow(() -> new RecursoNoEncontradoException("Sección no encontrada"));
				yield new AlcanceDeFamilias.Alcance(tipo, null, null, s.getId());
			}
		};
	}

	/** Las secciones donde el docente escribe: con su asignación vigente de tutor o de área (el auxiliar no envía). */
	private List<SeccionDocente> seccionesDeDocente() {
		return seccionesDocente.mias().stream().filter(s -> s.tutor() || !s.funcion().toLowerCase(java.util.Locale.ROOT)
				.matches("auxiliar.*")).toList();
	}

	private List<EmisorRol> emisoresDe(UsuarioAutenticado persona) {
		List<EmisorRol> emisores = new ArrayList<>();
		if (persona.roles().contains(Rol.PROMOTOR)) {
			emisores.add(EmisorRol.PROMOTORIA);
		}
		if (persona.roles().contains(Rol.DIRECTOR)) {
			emisores.add(EmisorRol.DIRECCION);
		}
		if (persona.roles().contains(Rol.DOCENTE) && !seccionesDeDocente().isEmpty()) {
			emisores.add(EmisorRol.DOCENTE);
		}
		return emisores;
	}

	/** B3: quién aprueba lo que deja por aprobar cada emisor. */
	public static String quienAprueba(EmisorRol emisor) {
		return switch (emisor) {
			case DIRECCION -> "Promotoría";
			case PROMOTORIA -> "Dirección";
			case DOCENTE -> "Dirección o Promotoría";
		};
	}

	/** «todo el colegio», «el nivel Primaria», «2.° de Primaria» o «3.° Primaria B». */
	public String textoAlcance(AlcanceDeFamilias.Alcance alcance) {
		return switch (alcance.tipo()) {
			case COLEGIO -> "todo el colegio";
			case NIVEL -> "el nivel " + alcance.nivel().name().charAt(0) + alcance.nivel().name().substring(1)
					.toLowerCase(java.util.Locale.ROOT);
			case GRADO -> alcance.grado().etiqueta();
			case SECCION -> secciones.findById(alcance.seccionId()).map(Seccion::etiqueta).orElse("una sección");
		};
	}

	/** El texto en párrafos (se muestra escapado, con {@code th:text}). */
	public static List<String> parrafos(String cuerpo) {
		return Arrays.stream(cuerpo.split("\\n\\s*\\n|\\n")).map(String::strip).filter(p -> !p.isEmpty()).toList();
	}

	private static <T> T exigido(T valor, String mensaje) {
		if (valor == null) {
			throw new ReglaNegocioException(mensaje);
		}
		return valor;
	}

	private LocalDateTime ahora() {
		return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
	}

	static RecursoNoEncontradoException noEncontrado() {
		return new RecursoNoEncontradoException("Comunicado no encontrado");
	}

	private record Preparado(UsuarioAutenticado persona, EmisorRol emisor, String etiqueta, AnioEscolar anio,
			AlcanceDeFamilias.Alcance alcance, String titulo, String cuerpo, boolean urgente, boolean requiereConfirmacion,
			Map<Long, Integer> familias, Comunicado original, String ambigua) {
	}

	/** La palabra ambigua que mandó el comunicado a aprobación (en los datos de su solicitud). */
	static final String DATO_PALABRA = "palabra";
}
