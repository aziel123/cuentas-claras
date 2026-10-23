package pe.edu.virgenmaria.cuentasclaras.academico.libreta.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.PeriodoEvaluacionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.dto.VerificacionVista;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.model.LibretaEmitida;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.repository.LibretaEmitidaRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sprint 10, tanda 3 (A4, E16): Dirección verifica un papel con su código {@code LIB-{id}-{16 hex}}: si existe (el id y los
 * 16 caracteres de la huella), si es la versión vigente, si la base sigue coincidiendo con su huella y los NIVELES de esa
 * versión por competencia (sin conclusiones) para compararlos con el papel. Cada consulta queda en la bitácora
 * ({@code LIBRETA_VERIFICADA}) y, con el alumno, en «Quién vio datos personales» (el controlador, LIBRETA).
 */
@Service
@PreAuthorize("hasRole('DIRECTOR')")
public class VerificacionLibreta {

	static final Pattern CODIGO = Pattern.compile("^LIB-(\\d{1,18})-([0-9a-f]{16})$");

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final LibretaEmitidaRepository libretas;

	private final RegeneracionLibreta regeneracion;

	private final PeriodoEvaluacionRepository periodos;

	private final SeccionRepository secciones;

	private final AlumnoRepository alumnos;

	private final AuditoriaService auditoria;

	public VerificacionLibreta(LibretaEmitidaRepository libretas, RegeneracionLibreta regeneracion,
			PeriodoEvaluacionRepository periodos, SeccionRepository secciones, AlumnoRepository alumnos,
			AuditoriaService auditoria) {
		this.libretas = libretas;
		this.regeneracion = regeneracion;
		this.periodos = periodos;
		this.secciones = secciones;
		this.alumnos = alumnos;
		this.auditoria = auditoria;
	}

	@Transactional
	public VerificacionVista verificar(String codigo) {
		UsuarioAutenticado persona = QuienEnSesionLibreta.actual();
		String limpio = codigo == null ? "" : codigo.strip();
		Matcher original = CODIGO.matcher(normalizar(limpio));
		Optional<LibretaEmitida> encontrada = original.matches()
				? libretas.findById(Long.valueOf(original.group(1)))
						.filter(l -> l.getHuellaContenido().startsWith(original.group(2)))
				: Optional.empty();
		auditoria.registrar(AccionAuditoria.LIBRETA_VERIFICADA, "libreta_emitida",
				encontrada.map(l -> l.getId().toString()).orElse(null), null,
				encontrada.isPresent() ? "existe" : "no existe", persona.nombreCompleto() + " verificó el código "
						+ (original.matches() ? normalizar(limpio) : "(con formato no válido)") + ": "
						+ (encontrada.isPresent() ? "corresponde a una libreta emitida." : "no corresponde a ninguna libreta."));
		if (encontrada.isEmpty()) {
			return VerificacionVista.noExiste(normalizar(limpio));
		}
		LibretaEmitida libreta = encontrada.get();
		int vigente = libretas.findFirstByPeriodoIdAndMatriculaIdOrderByNumeroVersionDesc(libreta.getPeriodoId(),
				libreta.getMatriculaId()).map(LibretaEmitida::getNumeroVersion).orElse(libreta.getNumeroVersion());
		RegeneracionLibreta.Regenerada r = regeneracion.regenerar(libreta);
		List<VerificacionVista.Nivel> niveles = new ArrayList<>();
		for (ContenidoLibreta.Area area : r.contenido().areas()) {
			for (ContenidoLibreta.Competencia c : area.competencias()) {
				niveles.add(new VerificacionVista.Nivel(area.nombre(), c.nombre(), c.delPeriodo().texto()));
			}
		}
		Long familia = alumnos.findById(libreta.getAlumnoId()).map(a -> a.getFamilia().getId()).orElse(null);
		return new VerificacionVista(libreta.codigoVerificacion(), true, vigente == libreta.getNumeroVersion(),
				r.coincide(), libreta.getId(), libreta.getAlumnoId(), familia, libreta.cabecera().alumnoNombre(),
				secciones.findById(libreta.getSeccionId()).map(s -> s.etiqueta()).orElse(""),
				periodos.findById(libreta.getPeriodoId()).map(p -> p.getNombre()).orElse(""), libreta.getNumeroVersion(),
				vigente, LibretasFamilia.emitida(libreta).format(FECHA), niveles);
	}

	/** «lib-12-ABCD…» y espacios: el código como se imprime (prefijo en mayúsculas, la huella en minúsculas). */
	static String normalizar(String codigo) {
		String c = codigo.replaceAll("\\s+", "");
		int segundo = c.indexOf('-', 4);
		if (c.length() < 5 || !c.substring(0, 4).equalsIgnoreCase("LIB-") || segundo < 0) {
			return c;
		}
		return "LIB-" + c.substring(4, segundo) + "-" + c.substring(segundo + 1).toLowerCase(Locale.ROOT);
	}
}
