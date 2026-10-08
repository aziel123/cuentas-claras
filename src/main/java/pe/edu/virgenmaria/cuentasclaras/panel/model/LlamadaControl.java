package pe.edu.virgenmaria.cuentasclaras.panel.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Resultado de una llamada de control (sprint 6, tanda 3; decisión 77, P17): Promotoría o Dirección llama a una familia
 * de la muestra secreta de la semana, le pregunta PRIMERO cuánto y cuándo pagó y después compara con lo registrado. Una
 * por colegio, semana y familia ({@code uk_llamada_control}) y de SOLO INSERCIÓN (sin GRANT de UPDATE ni DELETE: 1142).
 * En MySQL, {@code trg_llamada_control_registro} exige la persona, el lunes y el pago en efectivo.
 */
@Entity
@Immutable
@Table(name = "llamada_control")
public class LlamadaControl extends BaseEntity {

	/** Lo que cabe en la columna {@code nota}. */
	public static final int MAX_NOTA = 300;

	/** El lunes de la semana de la muestra. */
	@Column(nullable = false, updatable = false)
	private LocalDate semana;

	@Column(name = "familia_id", nullable = false, updatable = false)
	private Long familiaId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private ResultadoLlamada resultado;

	@Column(updatable = false, length = MAX_NOTA)
	private String nota;

	protected LlamadaControl() {
		// requerido por JPA
	}

	/** La nota ya viene validada por el servicio («No confirma» exige una de 10 caracteres como mínimo). */
	public static LlamadaControl registrar(LocalDate semana, Long familiaId, ResultadoLlamada resultado, String nota) {
		Objects.requireNonNull(semana, "semana");
		if (semana.getDayOfWeek() != DayOfWeek.MONDAY) {
			throw new IllegalArgumentException("La semana de una llamada de control es su lunes");
		}
		if (resultado == ResultadoLlamada.NO_CONFIRMA && (nota == null || nota.isBlank())) {
			throw new IllegalArgumentException("«No confirma» lleva lo que dijo la familia");
		}
		if (nota != null && nota.length() > MAX_NOTA) {
			throw new IllegalArgumentException("La nota no cabe en la columna");
		}
		LlamadaControl l = new LlamadaControl();
		l.semana = semana;
		l.familiaId = Objects.requireNonNull(familiaId, "familiaId");
		l.resultado = Objects.requireNonNull(resultado, "resultado");
		l.nota = nota;
		return l;
	}

	@PreUpdate
	void impedirEdicion() {
		throw new IllegalStateException("La llamada de control es de solo inserción.");
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("La llamada de control no se borra.");
	}

	public LocalDate getSemana() {
		return semana;
	}

	public Long getFamiliaId() {
		return familiaId;
	}

	public ResultadoLlamada getResultado() {
		return resultado;
	}

	public String getNota() {
		return nota;
	}
}
