package pe.edu.virgenmaria.cuentasclaras.matricula.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;

import java.util.List;

/**
 * Consultas de solo lectura sobre las matrículas RESERVADAS y su cuota de matrícula (sprint 5, tanda 2), del colegio
 * actual. Las matrículas se escriben solo con {@code alumnos.service.ReservasMatricula}.
 */
public interface MatriculasReservadasRepository extends Repository<Matricula, Long> {

	String RESERVADA = "pe.edu.virgenmaria.cuentasclaras.alumnos.model.EstadoMatricula.RESERVADA";

	String ACTIVA = "pe.edu.virgenmaria.cuentasclaras.alumnos.model.EstadoMatricula.ACTIVA";

	String TIPO_MATRICULA = "pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoCuota.MATRICULA";

	String CUOTA = "pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoCuota.";

	/** Reservadas con su cuota de matrícula PAGADA o EXONERADA: se activan. */
	@Query("select m.id from Matricula m where m.estado = " + RESERVADA + " and exists (select c.id from Cuota c "
			+ "where c.matriculaId = m.id and c.tipo = " + TIPO_MATRICULA + " and c.estado in (" + CUOTA + "PAGADA, "
			+ CUOTA + "EXONERADA)) order by m.id")
	List<Long> reservadasConMatriculaPagada();

	/** Reservadas sin ninguna cuota de matrícula vigente (plan con matrícula 0, o la cuota se anuló). */
	@Query("select m from Matricula m join fetch m.seccion where m.estado = " + RESERVADA
			+ " and not exists (select c.id from Cuota c where c.matriculaId = m.id and c.tipo = " + TIPO_MATRICULA
			+ " and c.estado <> " + CUOTA + "ANULADA) order by m.id")
	List<Matricula> reservadasSinCuotaDeMatriculaVigente();

	/** De esas, las que tienen su cuota de matrícula ANULADA (desistimiento aprobado). */
	@Query("select m.id from Matricula m where m.estado = " + RESERVADA + " and exists (select c.id from Cuota c "
			+ "where c.matriculaId = m.id and c.tipo = " + TIPO_MATRICULA + " and c.estado = " + CUOTA + "ANULADA) "
			+ "and not exists (select c.id from Cuota c where c.matriculaId = m.id and c.tipo = " + TIPO_MATRICULA
			+ " and c.estado <> " + CUOTA + "ANULADA) order by m.id")
	List<Long> reservadasConMatriculaAnulada();

	/**
	 * Activadas por el sistema cuya cuota de matrícula volvió a estar por pagar (se anuló el pago): no se desactivan
	 * solas (decisión 57), quedan en una alerta.
	 */
	@Query("select m from Matricula m join fetch m.alumno where m.estado = " + ACTIVA + " and m.activadaPor is not null "
			+ "and exists (select c.id from Cuota c where c.matriculaId = m.id and c.tipo = " + TIPO_MATRICULA
			+ " and c.estado in (" + CUOTA + "PENDIENTE, " + CUOTA + "PARCIAL)) order by m.id")
	List<Matricula> activasConMatriculaPorPagar();

	@Query("select count(m) from Matricula m where m.estado = " + RESERVADA + " and m.anioEscolar.id = :anio")
	long reservadasDelAnio(@org.springframework.data.repository.query.Param("anio") Long anioId);

	@Query("select count(m) from Matricula m where m.estado = " + ACTIVA + " and m.activadaPor is not null "
			+ "and m.anioEscolar.id = :anio")
	long activadasDelAnio(@org.springframework.data.repository.query.Param("anio") Long anioId);
}
