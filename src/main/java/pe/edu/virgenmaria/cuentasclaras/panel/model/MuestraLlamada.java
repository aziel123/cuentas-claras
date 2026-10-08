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
 * Una familia de la muestra CONGELADA de la llamada de control de una semana (correcciones del sprint 6, S6-B3 y QA-S6-2):
 * la muestra se elige con la semilla secreta la primera vez que se consulta en la semana y queda aquí, de SOLO INSERCIÓN
 * (sin GRANT de UPDATE ni DELETE: 1142). Así no cambia a mitad de semana aunque cambien las candidatas. Un reemplazo
 * (S6-M2) es otra fila que dice a quién reemplaza. En MySQL, {@code trg_muestra_llamada_registro} exige la semana en curso
 * y una familia candidata.
 */
@Entity
@Immutable
@Table(name = "muestra_llamada")
public class MuestraLlamada extends BaseEntity {

	@Column(nullable = false, updatable = false)
	private LocalDate semana;

	@Column(name = "familia_id", nullable = false, updatable = false)
	private Long familiaId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private MotivoMuestra motivo;

	@Column(name = "reemplaza_familia_id", updatable = false)
	private Long reemplazaFamiliaId;

	protected MuestraLlamada() {
		// requerido por JPA
	}

	public static MuestraLlamada elegida(LocalDate semana, Long familiaId, MotivoMuestra motivo) {
		if (motivo == MotivoMuestra.REEMPLAZO) {
			throw new IllegalArgumentException("Un reemplazo dice a quién reemplaza");
		}
		return nueva(semana, familiaId, motivo, null);
	}

	public static MuestraLlamada reemplazo(LocalDate semana, Long familiaId, Long reemplazaA) {
		Objects.requireNonNull(reemplazaA, "reemplazaA");
		if (reemplazaA.equals(familiaId)) {
			throw new IllegalArgumentException("Una familia no se reemplaza a sí misma");
		}
		return nueva(semana, familiaId, MotivoMuestra.REEMPLAZO, reemplazaA);
	}

	private static MuestraLlamada nueva(LocalDate semana, Long familiaId, MotivoMuestra motivo, Long reemplazaA) {
		Objects.requireNonNull(semana, "semana");
		if (semana.getDayOfWeek() != DayOfWeek.MONDAY) {
			throw new IllegalArgumentException("La semana de la muestra es su lunes");
		}
		MuestraLlamada m = new MuestraLlamada();
		m.semana = semana;
		m.familiaId = Objects.requireNonNull(familiaId, "familiaId");
		m.motivo = Objects.requireNonNull(motivo, "motivo");
		m.reemplazaFamiliaId = reemplazaA;
		return m;
	}

	@PreUpdate
	void impedirEdicion() {
		throw new IllegalStateException("La muestra de la llamada de control es de solo inserción.");
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("La muestra de la llamada de control no se borra.");
	}

	public LocalDate getSemana() {
		return semana;
	}

	public Long getFamiliaId() {
		return familiaId;
	}

	public MotivoMuestra getMotivo() {
		return motivo;
	}

	public Long getReemplazaFamiliaId() {
		return reemplazaFamiliaId;
	}
}
