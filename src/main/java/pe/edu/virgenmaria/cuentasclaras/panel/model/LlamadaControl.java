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
 * por colegio, semana, familia e intento ({@code uk_llamada_control_intento}, V23) y de SOLO INSERCIÓN (sin GRANT de UPDATE
 * ni DELETE: 1142). S6-M2: «No contesta» no cierra la plaza; se reintenta una vez (intento 2) y, si tampoco contesta, la
 * familia se reemplaza en la muestra.
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

	/** 1 o 2: el segundo intento solo después de un «No contesta» (V23). */
	@Column(nullable = false, updatable = false)
	private int intento;

	/** S6-M2: la registró Dirección porque Promotoría le delegó la semana (Promotoría recibe el aviso). */
	@Column(name = "por_delegacion", nullable = false, updatable = false)
	private boolean porDelegacion;

	/**
	 * Sprint 7, tanda 2 (residual S6): la hora en que se registró según la BASE (la pone trg_llamada_control_registro; la
	 * aplicación no la escribe). En MySQL, el segundo intento exige un «No contesta» de hace una hora o más con esta hora.
	 * En H2 queda en NULL.
	 */
	@Column(name = "registrada_bd", insertable = false, updatable = false)
	private java.time.LocalDateTime registradaBd;

	protected LlamadaControl() {
		// requerido por JPA
	}

	/** La nota ya viene validada por el servicio («No confirma» exige una de 10 caracteres como mínimo). */
	public static LlamadaControl registrar(LocalDate semana, Long familiaId, ResultadoLlamada resultado, String nota) {
		return registrar(semana, familiaId, resultado, nota, 1, false);
	}

	/**
	 * El intento {@code intento} (1 o 2) de la llamada a esa familia en esa semana; {@code porDelegacion} si la registra
	 * Dirección con la semana delegada por Promotoría.
	 */
	public static LlamadaControl registrar(LocalDate semana, Long familiaId, ResultadoLlamada resultado, String nota,
			int intento, boolean porDelegacion) {
		if (intento != 1 && intento != 2) {
			throw new IllegalArgumentException("Una llamada de control tiene uno o dos intentos");
		}
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
		l.intento = intento;
		l.porDelegacion = porDelegacion;
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

	public java.time.LocalDateTime getRegistradaBd() {
		return registradaBd;
	}

	public int getIntento() {
		return intento;
	}

	public boolean isPorDelegacion() {
		return porDelegacion;
	}
}
