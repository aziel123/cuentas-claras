package pe.edu.virgenmaria.cuentasclaras.academico.inicial;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.AreaCurricular;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.ConfiguracionEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.TipoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.AreaCurricularRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.CurriculoVersionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.service.ConfiguracionesEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.service.Periodos;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ProponerAsignacionRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.proceso.EmisionLibretas;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.HojaNotas;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.NotaPedida;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.NotasRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.service.AvanceRegistro;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.service.CorreccionesNota;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.service.RegistroNotas;
import pe.edu.virgenmaria.cuentasclaras.academico.service.Asignaciones;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.EstadoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.repository.SolicitudCambioRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.PersonaDemo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Sprint 10, tanda 3 (sección 3.12, decisión 187): las notas y las libretas del colegio de un visitante de la demo, hechas
 * por sus personas con los mismos servicios del colegio. Dirección guarda la evaluación por bimestres; Administración
 * completa el área (Matemática) de la asignación de la cuenta «docente» en 4.° Primaria A y Dirección la aprueba; el I
 * bimestre se abre, la docente pone las notas y Dirección lo cierra; sistema.libretas emite las libretas (de todas las
 * secciones: también la del hijo de la cuenta «apoderado»); la docente pide una corrección que aprueba Promotoría y sale
 * la versión 2; el II bimestre queda abierto. La exportación para el SIAGIE la saca Dirección desde la pantalla, con la
 * marca «DEMO · DATOS FICTICIOS». Solo si la fecha de hoy deja un I bimestre de al menos 3 semanas ya terminado; si algo
 * falla, la demo sigue sin libretas (queda en el log).
 */
@Component
@Profile("demo")
public class LibretasDemo {

	private static final Logger LOG = LoggerFactory.getLogger(LibretasDemo.class);

	static final String MOTIVO = "Áreas del catálogo de la carga horaria (ejemplo de la demo)";

	static final String MOTIVO_CORRECCION = "El examen recalificado muestra que logró lo esperado (ejemplo de la demo)";

	private final ConfiguracionesEvaluacion configuraciones;

	private final Periodos periodos;

	private final Asignaciones asignaciones;

	private final BandejaAprobaciones bandeja;

	private final SolicitudCambioRepository solicitudes;

	private final RegistroNotas registro;

	private final AvanceRegistro avance;

	private final CorreccionesNota correcciones;

	private final EmisionLibretas emision;

	private final AnioEscolarRepository anios;

	private final SeccionRepository secciones;

	private final UsuarioRepository usuarios;

	private final CurriculoVersionRepository versiones;

	private final AreaCurricularRepository areas;

	private final PersonaDemo personas;

	private final TransactionTemplate lectura;

	public LibretasDemo(ConfiguracionesEvaluacion configuraciones, Periodos periodos, Asignaciones asignaciones,
			BandejaAprobaciones bandeja, SolicitudCambioRepository solicitudes, RegistroNotas registro, AvanceRegistro avance,
			CorreccionesNota correcciones, EmisionLibretas emision, AnioEscolarRepository anios, SeccionRepository secciones,
			UsuarioRepository usuarios, CurriculoVersionRepository versiones, AreaCurricularRepository areas,
			PersonaDemo personas, PlatformTransactionManager transacciones) {
		this.configuraciones = configuraciones;
		this.periodos = periodos;
		this.asignaciones = asignaciones;
		this.bandeja = bandeja;
		this.solicitudes = solicitudes;
		this.registro = registro;
		this.avance = avance;
		this.correcciones = correcciones;
		this.emision = emision;
		this.anios = anios;
		this.secciones = secciones;
		this.usuarios = usuarios;
		this.versiones = versiones;
		this.areas = areas;
		this.personas = personas;
		this.lectura = new TransactionTemplate(transacciones);
		this.lectura.setReadOnly(true);
	}

	/** Las fechas del I bimestre (cerrado) y del II (abierto), o {@code null} si hoy no da para un I bimestre terminado. */
	public record Bimestres(LocalDate inicioI, LocalDate finI, LocalDate inicioII, LocalDate finII) {
	}

	public static Bimestres bimestres(LocalDate inicioClases, LocalDate finClases, LocalDate hoy) {
		LocalDate finI = hoy.minusDays(8);
		LocalDate inicioI = inicioClases.isAfter(hoy.minusDays(70)) ? inicioClases : hoy.minusDays(70);
		LocalDate inicioII = finI.plusDays(1);
		LocalDate finII = finClases.isBefore(hoy.plusDays(50)) ? finClases : hoy.plusDays(50);
		if (inicioI.plusDays(21).isAfter(finI) || !finII.isAfter(hoy)) {
			return null;
		}
		return new Bimestres(inicioI, finI, inicioII, finII);
	}

	public void cargar(long colegioId, String promotoria, String direccion, String administracion, String docente, int anio,
			LocalDate hoy) {
		try {
			AnioEscolar delAnio = leer(colegioId, () -> anios.findByAnio(anio).orElseThrow());
			Bimestres fechas = bimestres(delAnio.getInicioClases(), delAnio.getFinClases(), hoy);
			if (fechas == null) {
				LOG.info("La demo del colegio {} no tiene libretas: hoy ({}) no deja un I bimestre terminado.", colegioId, hoy);
				return;
			}
			Long anioId = delAnio.getId();
			Long version = leer(colegioId, () -> versiones.findByCodigo("CNEB-2016.1").orElseThrow().getId());
			personas.como(colegioId, direccion, () -> configuraciones.guardar(anioId, new ConfiguracionEvaluacion.Valores(
					version, TipoPeriodo.BIMESTRE, 500, false, 7, "Castellano", "DRE Lima Metropolitana", "UGEL 03")));
			Long matematica = leer(colegioId, () -> areas.findByVersionIdAndNivelOrderByOrdenAsc(version, Nivel.PRIMARIA)
					.stream().filter(a -> "MAT".equals(a.getCodigo())).map(AreaCurricular::getId).findFirst().orElseThrow());
			Long seccion = leer(colegioId, () -> secciones.findByAnioEscolarIdAndGradoAndNombre(anioId, Grado.PRIMARIA_4, "A")
					.map(Seccion::getId).orElseThrow());
			Long docenteId = leer(colegioId, () -> usuarios.findByNombreUsuario(docente).orElseThrow().getId());
			// La asignación del sprint 9 («Matemática» en texto) se reemplaza por la del catálogo (la aprueba Dirección).
			Long asignacion = personas.como(colegioId, administracion, () -> asignaciones.proponer(ProponerAsignacionRequest
					.conAreas(seccion, docenteId, TipoAsignacion.DOCENTE, MOTIVO, List.of(matematica))));
			aprobar(colegioId, direccion, pendiente(colegioId, TipoSolicitud.ASIGNACION_DOCENTE, asignacion));
			// El I bimestre: se abre, la docente pone las notas y Dirección lo cierra.
			Long primero = personas.como(colegioId, direccion, () -> periodos.programar(anioId, fechas.inicioI(),
					fechas.finI()));
			personas.como(colegioId, direccion, () -> {
				periodos.abrir(primero);
				return null;
			});
			personas.como(colegioId, docente, () -> {
				HojaNotas hoja = registro.hoja(seccion, matematica, primero, null);
				List<NotaPedida> notas = new ArrayList<>();
				String[] niveles = { "A", "AD", "A", "B" };
				for (HojaNotas.FilaAlumno fila : hoja.filas()) {
					for (HojaNotas.ColumnaCompetencia c : hoja.competencias()) {
						String nivel = niveles[(fila.numero() + c.id().intValue()) % niveles.length];
						boolean conConclusion = hoja.conclusionSiempre() || hoja.exigenConclusion().contains(nivel);
						notas.add(new NotaPedida(fila.matriculaId(), c.id(), nivel, conConclusion
								? "Resuelve problemas de cantidad con apoyo; seguirá practicando en casa (ejemplo de la demo)."
								: null));
					}
				}
				return registro.guardar(seccion, matematica, primero, new NotasRequest(notas, null));
			});
			personas.como(colegioId, direccion, () -> {
				avance.cerrar(primero);
				return null;
			});
			// sistema.libretas emite las libretas del I bimestre (todas las secciones, versión 1).
			emision.enColegio(colegioId);
			// La docente pide una corrección (B → A) y Promotoría la aprueba: la versión 2 con su aviso.
			Long nota = personas.como(colegioId, docente, () -> registro.hoja(seccion, matematica, primero, null).filas()
					.stream().flatMap(f -> f.celdas().stream()).filter(c -> "B".equals(c.nivel()) && c.calificacionId() != null)
					.map(HojaNotas.Celda::calificacionId).findFirst().orElse(null));
			if (nota != null) {
				Long correccion = personas.como(colegioId, docente, () -> correcciones.pedir(nota, "A", null,
						MOTIVO_CORRECCION));
				aprobar(colegioId, promotoria, correccion);
				emision.enColegio(colegioId);
			}
			// El II bimestre, abierto.
			Long segundo = personas.como(colegioId, direccion, () -> periodos.programar(anioId, fechas.inicioII(),
					fechas.finII()));
			personas.como(colegioId, direccion, () -> {
				periodos.abrir(segundo);
				return null;
			});
		}
		catch (ReglaNegocioException | IllegalStateException | java.util.NoSuchElementException e) {
			LOG.warn("No se cargaron las libretas de la demo del colegio {}: {}", colegioId, e.getMessage());
		}
	}

	private Long pendiente(long colegioId, TipoSolicitud tipo, Long entidad) {
		return leer(colegioId, () -> solicitudes.findByEstadoOrderByIdAsc(EstadoSolicitud.PENDIENTE).stream()
				.filter(s -> s.getTipo() == tipo && entidad.equals(s.getEntidadId())).findFirst().orElseThrow().getId());
	}

	private void aprobar(long colegioId, String quien, Long solicitud) {
		personas.como(colegioId, quien, () -> {
			bandeja.aprobar(solicitud, null);
			return null;
		});
	}

	private <T> T leer(long colegioId, Supplier<T> consulta) {
		return ContextoColegio.en(colegioId, () -> lectura.execute(t -> consulta.get()));
	}
}
