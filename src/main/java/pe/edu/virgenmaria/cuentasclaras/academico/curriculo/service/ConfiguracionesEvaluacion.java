package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.ConfiguracionEvaluacionVista;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.OpcionAnio;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.VersionDelCatalogo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.ConfiguracionEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.EstadoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.PeriodoEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.TipoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.ConfiguracionEvaluacionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.PeriodoEvaluacionRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Sprint 10, tanda 1 (sección 3.3; S1, S3, S9 y S11; E11 y M7): cómo se evalúa cada año del colegio. La ven Dirección,
 * Administración y Promotoría; la guarda SOLO Dirección, con la firma de su sesión
 * ({@code configuracion_evaluacion:{anio}:GUARDADA:{version}}), y el cambio queda resaltado en la bitácora con el antes y
 * el después. Desde que abre un periodo del año, la versión del currículo, el tipo de periodo y los días de cierre
 * anticipado no cambian; un tipo con menos periodos que los ya programados, tampoco. En MySQL lo exigen además
 * trg_configuracion_evaluacion_nace y _cambio.
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION')")
public class ConfiguracionesEvaluacion {

	static final String ENTIDAD = "configuracion_evaluacion";

	private final ConfiguracionEvaluacionRepository configuraciones;

	private final PeriodoEvaluacionRepository periodos;

	private final AnioEscolarRepository anios;

	private final Catalogo catalogo;

	private final FirmaSesion firma;

	private final AuditoriaService auditoria;

	public ConfiguracionesEvaluacion(ConfiguracionEvaluacionRepository configuraciones,
			PeriodoEvaluacionRepository periodos, AnioEscolarRepository anios, Catalogo catalogo, FirmaSesion firma,
			AuditoriaService auditoria) {
		this.configuraciones = configuraciones;
		this.periodos = periodos;
		this.anios = anios;
		this.catalogo = catalogo;
		this.firma = firma;
		this.auditoria = auditoria;
	}

	/** La pantalla del año (por defecto, el año en curso o el último que no está cerrado). */
	@Transactional(readOnly = true)
	public ConfiguracionEvaluacionVista vista(Long anioId) {
		UsuarioAutenticado persona = QuienEnSesion.actual();
		List<AnioEscolar> todos = anios.findAllByOrderByAnioDesc();
		List<OpcionAnio> opciones = AniosDeEvaluacion.opciones(todos);
		Optional<AnioEscolar> elegido = AniosDeEvaluacion.elegir(todos, anioId);
		List<VersionDelCatalogo> versiones = catalogo.versiones();
		boolean puedeGuardar = persona.roles().contains(Rol.DIRECTOR);
		if (elegido.isEmpty()) {
			return new ConfiguracionEvaluacionVista(opciones, null, null, false, false, false, puedeGuardar, versiones, null,
					null, TipoPeriodo.BIMESTRE, ConfiguracionEvaluacion.CONCLUSION_POR_DEFECTO, false,
					ConfiguracionEvaluacion.DIAS_CIERRE_POR_DEFECTO, ConfiguracionEvaluacion.LENGUA_POR_DEFECTO, null, null,
					null, null);
		}
		AnioEscolar anio = elegido.get();
		boolean fijos = periodos.existsByAnioEscolarIdAndEstadoNot(anio.getId(), EstadoPeriodo.PROGRAMADO);
		Optional<ConfiguracionEvaluacion> guardada = configuraciones.findByAnioEscolarId(anio.getId());
		if (guardada.isEmpty()) {
			// Los valores por defecto (S1, S3, S9 y S11), sin guardar: BIMESTRE, 500, 7 días y «Castellano».
			Optional<VersionDelCatalogo> porDefecto = catalogo.porDefecto();
			return new ConfiguracionEvaluacionVista(opciones, anio.getId(), anio.getAnio(), anio.cerrado(), false, fijos,
					puedeGuardar && !anio.cerrado(), versiones, porDefecto.map(VersionDelCatalogo::id).orElse(null),
					porDefecto.map(VersionDelCatalogo::codigo).orElse(null), TipoPeriodo.BIMESTRE,
					ConfiguracionEvaluacion.CONCLUSION_POR_DEFECTO, false, ConfiguracionEvaluacion.DIAS_CIERRE_POR_DEFECTO,
					ConfiguracionEvaluacion.LENGUA_POR_DEFECTO, null, null, null, null);
		}
		ConfiguracionEvaluacion c = guardada.get();
		return new ConfiguracionEvaluacionVista(opciones, anio.getId(), anio.getAnio(), anio.cerrado(), true, fijos,
				puedeGuardar && !anio.cerrado(), versiones, c.getCurriculoVersionId(),
				catalogo.version(c.getCurriculoVersionId()).map(VersionDelCatalogo::codigo).orElse(null), c.getTipoPeriodo(),
				c.getConclusionMax(), c.isConclusionSiempre(), c.getDiasCierreAnticipado(), c.getLenguaMaterna(), c.getDre(),
				c.getUgel(), c.getActualizadoPor(), c.getActualizadoEn());
	}

	/**
	 * Dirección guarda la configuración del año con su firma. Si no cambia nada, no guarda ni firma. @return si guardó
	 */
	@PreAuthorize("hasRole('DIRECTOR')")
	@Transactional
	public boolean guardar(Long anioId, ConfiguracionEvaluacion.Valores pedidos) {
		ConfiguracionEvaluacion.Valores valores = Objects.requireNonNull(pedidos, "valores").validados();
		UsuarioAutenticado persona = QuienEnSesion.actual();
		AnioEscolar anio = anios.bloquear(anioId).orElseThrow(() -> new RecursoNoEncontradoException(
				"Año escolar no encontrado"));
		if (anio.cerrado()) {
			throw new ReglaNegocioException("El año " + anio.getAnio() + " está cerrado: su evaluación ya no cambia.");
		}
		VersionDelCatalogo version = catalogo.version(valores.curriculoVersionId()).orElseThrow(
				() -> new ReglaNegocioException("Esa versión del currículo no existe."));
		ConfiguracionEvaluacion actual = configuraciones.bloquearDelAnio(anio.getId()).orElse(null);
		List<PeriodoEvaluacion> delAnio = periodos.findByAnioEscolarIdOrderByNumeroAsc(anio.getId());
		boolean abierto = delAnio.stream().anyMatch(p -> p.getEstado() != EstadoPeriodo.PROGRAMADO);
		if (actual != null && abierto) {
			ConfiguracionEvaluacion.Valores antes = actual.valores();
			// E11 y M7: con un periodo abierto (o cerrado), lo que decide qué se califica y cuándo se cierra no cambia.
			if (!antes.curriculoVersionId().equals(valores.curriculoVersionId()) || antes.tipoPeriodo() != valores.tipoPeriodo()
					|| antes.diasCierreAnticipado() != valores.diasCierreAnticipado()) {
				throw new ReglaNegocioException("El año ya tiene un periodo abierto: la versión del currículo, el tipo de "
						+ "periodo y los días de cierre anticipado ya no cambian.");
			}
		}
		if (delAnio.stream().anyMatch(p -> p.getNumero() > valores.tipoPeriodo().periodos())) {
			throw new ReglaNegocioException("El año ya tiene " + delAnio.size() + " periodos programados: con "
					+ valores.tipoPeriodo().etiqueta().toLowerCase(java.util.Locale.ROOT) + "s tendría solo "
					+ valores.tipoPeriodo().periodos() + ".");
		}
		String antes = actual == null ? null : texto(actual.valores(), versionDe(actual.getCurriculoVersionId()));
		String despues = texto(valores, version.codigo());
		if (antes != null && antes.equals(despues)) {
			return false;
		}
		ConfiguracionEvaluacion guardar;
		if (actual == null) {
			guardar = ConfiguracionEvaluacion.nueva(anio.getId(), valores, persona.getUsername());
		}
		else {
			actual.cambiar(valores, persona.getUsername());
			guardar = actual;
		}
		// La firma de Dirección, antes del cambio (trg_configuracion_evaluacion_nace y _cambio la exigen a su nombre).
		firma.firmar(ClaveFirma.configuracionEvaluacion(anio.getId(), guardar.versionAlGuardar()));
		ConfiguracionEvaluacion guardada = configuraciones.saveAndFlush(guardar);
		auditoria.registrar(AccionAuditoria.CONFIGURACION_EVALUACION_GUARDADA, ENTIDAD, guardada.getId().toString(), antes,
				despues, persona.nombreCompleto() + " guardó cómo se evalúa el año " + anio.getAnio() + ". "
						+ (antes == null ? "Primera configuración: " + despues : "Antes: " + antes + ". Ahora: " + despues)
						+ ".");
		return true;
	}

	private String versionDe(Long versionId) {
		return catalogo.version(versionId).map(VersionDelCatalogo::codigo).orElse("versión " + versionId);
	}

	static String texto(ConfiguracionEvaluacion.Valores v, String version) {
		return "currículo " + version + ", " + v.tipoPeriodo().periodos() + " "
				+ v.tipoPeriodo().etiqueta().toLowerCase(java.util.Locale.ROOT) + "s, conclusión hasta " + v.conclusionMax()
				+ " caracteres" + (v.conclusionSiempre() ? " y siempre exigida" : "") + ", cierre desde "
				+ v.diasCierreAnticipado() + " días antes del fin, lengua materna " + v.lenguaMaterna()
				+ (v.dre() == null ? "" : ", DRE " + v.dre()) + (v.ugel() == null ? "" : ", UGEL " + v.ugel());
	}
}
