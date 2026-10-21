package pe.edu.virgenmaria.cuentasclaras.academico.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.academico.model.MarcaAsistencia;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Sprint 9, tanda 2: marcas de asistencia del colegio actual (las filtra {@code @TenantId}). SOLO INSERCIÓN: no extiende
 * CrudRepository (no hereda {@code delete*}) ni tiene {@code @Modifying}. La marca vigente de un alumno en un registro es
 * la que ninguna otra reemplaza.
 */
public interface MarcaAsistenciaRepository extends Repository<MarcaAsistencia, Long> {

	MarcaAsistencia saveAndFlush(MarcaAsistencia marca);

	Optional<MarcaAsistencia> findById(Long id);

	/** Las marcas de un registro, en orden de llegada (la historia completa). */
	List<MarcaAsistencia> findByRegistroIdOrderByIdAsc(Long registroId);

	/** Las vigentes de un registro: las que nadie reemplazó. */
	@Query("select m from MarcaAsistencia m where m.registroId = :registro and not exists "
			+ "(select 1 from MarcaAsistencia s where s.reemplazaId = m.id) order by m.id asc")
	List<MarcaAsistencia> vigentesDelRegistro(@Param("registro") Long registroId);

	/** Si otra marca ya reemplazó a esta (entonces no es la vigente). */
	boolean existsByReemplazaId(Long reemplazaId);

	/** Las vigentes de unos alumnos entre dos fechas (el mes del portal y el reporte por alumno). */
	@Query("select m from MarcaAsistencia m where m.alumnoId in :alumnos and m.fecha between :desde and :hasta and not "
			+ "exists (select 1 from MarcaAsistencia s where s.reemplazaId = m.id) order by m.fecha asc, m.id asc")
	List<MarcaAsistencia> vigentesDeAlumnos(@Param("alumnos") Collection<Long> alumnos, @Param("desde") LocalDate desde,
			@Param("hasta") LocalDate hasta);

	/** Las vigentes de una sección entre dos fechas (el reporte mensual por sección). */
	@Query("select m from MarcaAsistencia m where m.seccionId = :seccion and m.fecha between :desde and :hasta and not "
			+ "exists (select 1 from MarcaAsistencia s where s.reemplazaId = m.id) order by m.fecha asc, m.id asc")
	List<MarcaAsistencia> vigentesDeSeccion(@Param("seccion") Long seccionId, @Param("desde") LocalDate desde,
			@Param("hasta") LocalDate hasta);

	/** Las vigentes de un día (el primer aviso de las familias). */
	@Query("select m from MarcaAsistencia m where m.fecha = :fecha and not exists "
			+ "(select 1 from MarcaAsistencia s where s.reemplazaId = m.id) order by m.id asc")
	List<MarcaAsistencia> vigentesDelDia(@Param("fecha") LocalDate fecha);

	/** Las marcas posteriores a la marca de agua (A2), en orden de llegada. */
	List<MarcaAsistencia> findByIdGreaterThanOrderByIdAsc(Long id);

	/** El id más alto del colegio (0 si no hay marcas). */
	@Query("select coalesce(max(m.id), 0) from MarcaAsistencia m")
	Long ultimoId();

	/** Cuántas marcas iniciales tiene cada registro: [registro_id, cantidad] (A4). */
	@Query("select m.registroId, count(m) from MarcaAsistencia m where m.registroId in :registros and m.primera = true "
			+ "group by m.registroId")
	List<Object[]> inicialesPorRegistro(@Param("registros") Collection<Long> registros);

	/** Las marcas que no son del registro inicial de una fecha (la lista de Dirección «correcciones del día»). */
	@Query("select m from MarcaAsistencia m where m.fecha = :fecha and m.origen <> pe.edu.virgenmaria.cuentasclaras"
			+ ".academico.model.OrigenMarca.REGISTRO order by m.id desc")
	List<MarcaAsistencia> cambiosDelDia(@Param("fecha") LocalDate fecha);
}
