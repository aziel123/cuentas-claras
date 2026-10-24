package pe.edu.virgenmaria.cuentasclaras.academico.libreta.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.PeriodoEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.PeriodoEvaluacionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.dto.Descarga;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.dto.PortalLibretasVista;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.model.EntregaLibreta;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.model.LibretaEmitida;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.repository.EntregaLibretaRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.repository.LibretaEmitidaRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.service.SesionDeQuienEscribe;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Sprint 10, tanda 3 (secciones 3.7 y 3.8; E27, E32): las libretas en el portal de la familia. El apoderado en sesión ve y
 * descarga TODAS las versiones de las libretas de SUS hijos (otra familia u otro colegio: 404). Cada descarga deja su
 * constancia de entrega PORTAL con su sesión (trg_entrega_libreta_nace). Si la huella no coincide, no se entrega ese PDF y
 * la familia ve hasta cuándo se la entregará el colegio (A3).
 * <p>
 * <b>INDECOPI (Ley 29571):</b> este servicio no puede leer cobranza (ArchUnit): la familia que debe descarga su libreta igual
 * que la que está al día ({@code LibretaConDeudaTest}).
 */
@Service
@PreAuthorize("hasRole('APODERADO')")
public class LibretasFamilia {

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final LibretaEmitidaRepository libretas;

	private final EntregaLibretaRepository entregas;

	private final PeriodoEvaluacionRepository periodos;

	private final AlumnoRepository alumnos;

	private final ApoderadoRepository apoderados;

	private final RegeneracionLibreta regeneracion;

	private final SesionDeQuienEscribe sesiones;

	private final Clock reloj;

	public LibretasFamilia(LibretaEmitidaRepository libretas, EntregaLibretaRepository entregas,
			PeriodoEvaluacionRepository periodos, AlumnoRepository alumnos, ApoderadoRepository apoderados,
			RegeneracionLibreta regeneracion, SesionDeQuienEscribe sesiones, Clock reloj) {
		this.libretas = libretas;
		this.entregas = entregas;
		this.periodos = periodos;
		this.alumnos = alumnos;
		this.apoderados = apoderados;
		this.regeneracion = regeneracion;
		this.sesiones = sesiones;
		this.reloj = reloj;
	}

	/** Las libretas de cada hijo, por periodo (el más reciente primero) y versión. */
	@Transactional(readOnly = true)
	public PortalLibretasVista lista() {
		Apoderado apoderado = apoderadoEnSesion();
		List<Alumno> hijos = alumnos.findByFamiliaIdOrderByFechaNacimientoAsc(apoderado.getFamilia().getId());
		List<LibretaEmitida> todas = hijos.isEmpty() ? List.of()
				: libretas.findByAlumnoIdInOrderByPeriodoIdDescAlumnoIdAscNumeroVersionDesc(hijos.stream()
						.map(Alumno::getId).toList());
		Map<Long, PeriodoEvaluacion> porId = new LinkedHashMap<>();
		todas.forEach(l -> porId.computeIfAbsent(l.getPeriodoId(), id -> periodos.findById(id).orElse(null)));
		List<PortalLibretasVista.Hijo> resultado = new ArrayList<>();
		for (Alumno hijo : hijos) {
			Map<Long, List<LibretaEmitida>> porPeriodo = todas.stream().filter(l -> l.getAlumnoId().equals(hijo.getId()))
					.collect(Collectors.groupingBy(LibretaEmitida::getPeriodoId, LinkedHashMap::new, Collectors.toList()));
			List<PortalLibretasVista.Periodo> lista = new ArrayList<>();
			porPeriodo.forEach((periodo, versiones) -> {
				int mayor = versiones.stream().mapToInt(LibretaEmitida::getNumeroVersion).max().orElse(1);
				PeriodoEvaluacion p = porId.get(periodo);
				lista.add(new PortalLibretasVista.Periodo(p == null ? "Periodo" : p.getNombre(),
						versiones.get(0).cabecera() == null ? 0 : anio(versiones.get(0)), versiones.stream()
								.map(l -> new PortalLibretasVista.Version(l.getId(), l.getNumeroVersion(),
										emitida(l).format(FECHA), l.getMotivo().etiqueta(), l.getCambios(),
										l.getNumeroVersion() == mayor))
								.toList()));
			});
			resultado.add(new PortalLibretasVista.Hijo(hijo.getId(), hijo.getNombres(), lista));
		}
		return new PortalLibretasVista(resultado);
	}

	/**
	 * Descarga una libreta de un hijo (cualquier versión). E32: de otra familia u otro colegio, 404. Deja la constancia de
	 * entrega PORTAL con la sesión del apoderado; con la huella distinta, no hay PDF sino la fecha de entrega (A3).
	 */
	@Transactional
	public Descarga descargar(long libretaId) {
		UsuarioAutenticado persona = QuienEnSesionLibreta.actual();
		Apoderado apoderado = apoderadoEnSesion();
		LibretaEmitida libreta = libretas.findById(libretaId)
				.filter(l -> alumnos.findById(l.getAlumnoId()).map(a -> a.getFamilia().getId()
						.equals(apoderado.getFamilia().getId())).orElse(false))
				.orElseThrow(() -> new RecursoNoEncontradoException("Libreta no encontrada"));
		Long familia = apoderado.getFamilia().getId();
		RegeneracionLibreta.Regenerada r = regeneracion.regenerar(libreta);
		if (!r.coincide()) {
			return Descarga.enRevision(regeneracion.plazo(libreta), libreta.getAlumnoId(), familia);
		}
		byte[] pdf = regeneracion.pdf(List.of(r));
		entregas.saveAndFlush(EntregaLibreta.porPortal(libreta.getId(), apoderado.getId(), sesiones.de(persona),
				LocalDateTime.now(reloj)));
		return Descarga.pdf(pdf, "libreta-" + libreta.getId() + "-v" + libreta.getNumeroVersion() + ".pdf",
				libreta.getAlumnoId(), familia, 1, 0);
	}

	private Apoderado apoderadoEnSesion() {
		Long id = QuienEnSesionLibreta.actual().apoderadoId();
		return Optional.ofNullable(id).flatMap(apoderados::findById).filter(Apoderado::isActivo)
				.orElseThrow(() -> new RecursoNoEncontradoException("Apoderado no encontrado"));
	}

	private int anio(LibretaEmitida l) {
		return periodos.findById(l.getPeriodoId()).map(p -> p.getInicio().getYear()).orElse(0);
	}

	static LocalDateTime emitida(LibretaEmitida l) {
		return l.getRegistradoBd() != null ? l.getRegistradoBd() : l.getCreadoEn();
	}
}
