package pe.edu.virgenmaria.cuentasclaras.panel.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Promotoría delega a Dirección las llamadas de control de UNA semana (correcciones del sprint 6, S6-M2). Sin esta fila,
 * Dirección ve la muestra pero no registra resultados. Una por colegio y semana, de SOLO INSERCIÓN (1142): no se revoca
 * ni se reescribe; la semana siguiente vuelve a ser de Promotoría. En MySQL, {@code trg_delegacion_llamada_registro} exige
 * que la cree una persona activa de Promotoría para la semana en curso.
 */
@Entity
@Immutable
@Table(name = "delegacion_llamada")
public class DelegacionLlamada extends BaseEntity {

	@Column(nullable = false, updatable = false)
	private LocalDate semana;

	protected DelegacionLlamada() {
		// requerido por JPA
	}

	public static DelegacionLlamada de(LocalDate semana) {
		Objects.requireNonNull(semana, "semana");
		if (semana.getDayOfWeek() != DayOfWeek.MONDAY) {
			throw new IllegalArgumentException("La semana de la delegación es su lunes");
		}
		DelegacionLlamada d = new DelegacionLlamada();
		d.semana = semana;
		return d;
	}

	@PreUpdate
	void impedirEdicion() {
		throw new IllegalStateException("La delegación de las llamadas es de solo inserción.");
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("La delegación de las llamadas no se borra.");
	}

	public LocalDate getSemana() {
		return semana;
	}
}
