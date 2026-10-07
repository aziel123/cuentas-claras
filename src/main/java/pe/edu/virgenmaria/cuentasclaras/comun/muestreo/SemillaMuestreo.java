package pe.edu.virgenmaria.cuentasclaras.comun.muestreo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Semilla SECRETA del muestreo de un día (sprint 5, tanda 3; hallazgo 5 y G21). SOLO INSERCIÓN (sin GRANT de UPDATE ni
 * DELETE). Nace con {@code SecureRandom}: nadie la deduce de la fecha.
 */
@Entity
@Table(name = "semilla_muestreo")
public class SemillaMuestreo extends BaseEntity {

	/** Para qué muestreo es. */
	public enum Ambito {
		/** Las 3 verificaciones bancarias del día hábil anterior que ve Promotoría. */
		CAJA
	}

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private Ambito ambito;

	@Column(nullable = false, updatable = false)
	private LocalDate fecha;

	@Column(nullable = false, updatable = false)
	private long semilla;

	protected SemillaMuestreo() {
	}

	static SemillaMuestreo nueva(Ambito ambito, LocalDate fecha, long semilla) {
		SemillaMuestreo s = new SemillaMuestreo();
		s.ambito = Objects.requireNonNull(ambito, "ambito");
		s.fecha = Objects.requireNonNull(fecha, "fecha");
		s.semilla = semilla;
		return s;
	}

	public Ambito getAmbito() {
		return ambito;
	}

	public LocalDate getFecha() {
		return fecha;
	}

	public long getSemilla() {
		return semilla;
	}
}
