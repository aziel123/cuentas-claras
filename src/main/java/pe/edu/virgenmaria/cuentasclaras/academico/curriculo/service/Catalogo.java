package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.AreaDelCatalogo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.CatalogoVista;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.CompetenciaDelCatalogo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.NivelDelCatalogo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.ValorEscala;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.VersionDelCatalogo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.AreaCurricular;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.Competencia;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.CurriculoVersion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.EscalaLogro;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.AreaCurricularRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.CompetenciaRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.CurriculoVersionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.EscalaLogroRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sprint 10, tanda 1 (sección 3.2 y contrato de la sección 6 del brief): la lectura del catálogo nacional. Nada de la norma
 * está escrito en Java: las áreas, las competencias (con {@code solo_grados}), su orden y qué valor de la escala exige
 * conclusión salen de la base, de la versión que eligió el colegio para el año. Solo lectura: el catálogo no tiene datos
 * personales y lo ven Promotoría, Dirección, Administración, los docentes y {@code sistema.academico} (Caja no).
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION','DOCENTE','SISTEMA_ACADEMICO')")
@Transactional(readOnly = true)
public class Catalogo {

	private final CurriculoVersionRepository versiones;

	private final AreaCurricularRepository areas;

	private final CompetenciaRepository competencias;

	private final EscalaLogroRepository escala;

	private final String versionPorDefecto;

	public Catalogo(CurriculoVersionRepository versiones, AreaCurricularRepository areas, CompetenciaRepository competencias,
			EscalaLogroRepository escala,
			@Value("${cuentasclaras.curriculo.version-por-defecto:CNEB-2016.1}") String versionPorDefecto) {
		this.versiones = versiones;
		this.areas = areas;
		this.competencias = competencias;
		this.escala = escala;
		this.versionPorDefecto = versionPorDefecto;
	}

	/** Las versiones cargadas por las migraciones, por código. */
	public List<VersionDelCatalogo> versiones() {
		List<CurriculoVersion> todas = versiones.findAllByOrderByCodigoAsc();
		Map<Long, String> codigos = todas.stream().collect(Collectors.toMap(CurriculoVersion::getId,
				CurriculoVersion::getCodigo));
		return todas.stream().map(v -> vista(v, codigos)).toList();
	}

	/** La versión que la pantalla propone para un año nuevo ({@code cuentasclaras.curriculo.version-por-defecto}). */
	public Optional<VersionDelCatalogo> porDefecto() {
		return versiones().stream().filter(v -> v.codigo().equals(versionPorDefecto)).findFirst()
				.or(() -> versiones().stream().reduce((a, b) -> b));
	}

	public Optional<VersionDelCatalogo> version(Long versionId) {
		return versiones().stream().filter(v -> v.id().equals(versionId)).findFirst();
	}

	/** Las áreas de un nivel en la versión, en su orden (con la de competencias transversales al final). */
	public List<AreaDelCatalogo> areasDe(Long versionId, Nivel nivel) {
		return areas.findByVersionIdAndNivelOrderByOrdenAsc(versionId, nivel).stream().map(Catalogo::vista).toList();
	}

	/** Las áreas pedidas por id (las de otra versión o inexistentes no vienen). */
	public List<AreaDelCatalogo> areas(java.util.Collection<Long> ids) {
		return ids.isEmpty() ? List.of() : areas.findByIdIn(ids).stream().map(Catalogo::vista)
				.sorted(Comparator.comparing(AreaDelCatalogo::nivel).thenComparingInt(AreaDelCatalogo::orden)).toList();
	}

	/**
	 * Las competencias que se califican en ese grado: las de las áreas de su nivel en la versión, respetando
	 * {@code solo_grados}, en el orden de la libreta y del SIAGIE ({@code area.orden}, {@code competencia.orden}).
	 */
	public List<CompetenciaDelCatalogo> competenciasDe(Long versionId, Grado grado) {
		Map<Long, AreaCurricular> delNivel = areas.findByVersionIdAndNivelOrderByOrdenAsc(versionId, grado.nivel()).stream()
				.collect(Collectors.toMap(AreaCurricular::getId, Function.identity()));
		if (delNivel.isEmpty()) {
			return List.of();
		}
		return competencias.findByAreaIdInOrderByOrdenAsc(delNivel.keySet()).stream().filter(c -> c.aplicaA(grado))
				.map(c -> vista(c, delNivel.get(c.getAreaId())))
				.sorted(Comparator.comparingInt(CompetenciaDelCatalogo::areaOrden)
						.thenComparingInt(CompetenciaDelCatalogo::orden))
				.toList();
	}

	/** Si ese valor de la escala exige conclusión descriptiva en el nivel (el mínimo del catálogo, RVM 048-2024). */
	public boolean exigeConclusion(Long versionId, Nivel nivel, String valor) {
		return escala.findByVersionIdAndNivelAndValor(versionId, nivel, valor)
				.orElseThrow(() -> new IllegalArgumentException("El valor " + valor + " no está en la escala de " + nivel))
				.isExigeConclusion();
	}

	/** La escala de un nivel (AD, A, B y C), en su orden. */
	public List<ValorEscala> escalaDe(Long versionId, Nivel nivel) {
		return escala.findByVersionIdOrderByNivelAscOrdenAsc(versionId).stream().filter(e -> e.getNivel() == nivel)
				.map(Catalogo::vista).toList();
	}

	/** La pantalla de solo lectura del catálogo: la versión pedida (por código) o la de por defecto. */
	public CatalogoVista vistaCatalogo(String codigo) {
		List<VersionDelCatalogo> todas = versiones();
		VersionDelCatalogo elegida = codigo == null ? porDefecto().orElse(null)
				: todas.stream().filter(v -> v.codigo().equals(codigo)).findFirst()
						.orElseThrow(() -> new RecursoNoEncontradoException("Versión del currículo no encontrada"));
		if (elegida == null) {
			return new CatalogoVista(todas, null, List.of(), 0, 0, 0);
		}
		List<AreaCurricular> todasLasAreas = areas.findByVersionIdOrderByNivelAscOrdenAsc(elegida.id());
		Map<Long, AreaCurricular> porId = todasLasAreas.stream().collect(Collectors.toMap(AreaCurricular::getId,
				Function.identity()));
		Map<Long, List<CompetenciaDelCatalogo>> porArea = competencias.findByVersionIdOrderByCodigoAsc(elegida.id())
				.stream().map(c -> vista(c, porId.get(c.getAreaId())))
				.sorted(Comparator.comparingInt(CompetenciaDelCatalogo::orden))
				.collect(Collectors.groupingBy(CompetenciaDelCatalogo::areaId));
		List<EscalaLogro> valores = escala.findByVersionIdOrderByNivelAscOrdenAsc(elegida.id());
		List<NivelDelCatalogo> niveles = new ArrayList<>();
		int totalCompetencias = 0;
		for (Nivel nivel : Nivel.values()) {
			List<NivelDelCatalogo.AreaConCompetencias> delNivel = new ArrayList<>();
			for (AreaCurricular a : todasLasAreas) {
				if (a.getNivel() == nivel) {
					List<CompetenciaDelCatalogo> suyas = porArea.getOrDefault(a.getId(), List.of());
					totalCompetencias += suyas.size();
					delNivel.add(new NivelDelCatalogo.AreaConCompetencias(vista(a), suyas));
				}
			}
			niveles.add(new NivelDelCatalogo(nivel, delNivel, valores.stream().filter(e -> e.getNivel() == nivel)
					.map(Catalogo::vista).toList()));
		}
		return new CatalogoVista(todas, elegida, niveles, todasLasAreas.size(), totalCompetencias, valores.size());
	}

	private static VersionDelCatalogo vista(CurriculoVersion v, Map<Long, String> codigos) {
		return new VersionDelCatalogo(v.getId(), v.getCodigo(), v.getNombre(), v.getNormas(), v.getVigenteDesde(),
				v.getReemplazaId() == null ? null : codigos.get(v.getReemplazaId()), v.getNota());
	}

	static AreaDelCatalogo vista(AreaCurricular a) {
		return new AreaDelCatalogo(a.getId(), a.getVersionId(), a.getNivel(), a.getCodigo(), a.getNombre(), a.getOrden(),
				a.isTransversal(), a.isExonerable());
	}

	private static CompetenciaDelCatalogo vista(Competencia c, AreaCurricular a) {
		return new CompetenciaDelCatalogo(c.getId(), c.getCodigo(), c.getNombre(), c.getOrden(), a.getId(), a.getCodigo(),
				a.getNombre(), a.getOrden(), a.isTransversal(), c.getSoloGrados());
	}

	private static ValorEscala vista(EscalaLogro e) {
		return new ValorEscala(e.getValor(), e.getNombre(), e.getDescripcion(), e.getOrden(), e.isExigeConclusion());
	}
}
