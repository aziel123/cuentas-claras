package pe.edu.virgenmaria.cuentasclaras.comun.fecha;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Día no laborable EXTRA del colegio (sprint 5, tanda 3): un día decretado no laborable o un feriado local. Los feriados
 * nacionales NO están aquí: están en el código ({@link FeriadosNacionales}). Lo registran Promotoría o Dirección, solo
 * para fechas futuras (en MySQL lo exige trg_feriado_registro), y se anula una vez y antes de su fecha (trg_feriado_anulacion).
 * La fecha y la descripción no cambian (GRANT por columna: 1143). {@code vigente}: TRUE o NULL (anulado).
 */
@Entity
@Table(name = "feriado")
public class Feriado extends BaseEntity {

	@Column(nullable = false, updatable = false)
	private LocalDate fecha;

	@Column(nullable = false, updatable = false, length = 80)
	private String descripcion;

	@Column
	private Boolean vigente;

	@Column(name = "anulado_por", length = 60)
	private String anuladoPor;

	@Column(name = "anulado_en")
	private LocalDateTime anuladoEn;

	@Column(name = "motivo_anulacion", length = 500)
	private String motivoAnulacion;

	protected Feriado() {
	}

	public static Feriado nuevo(LocalDate fecha, String descripcion) {
		Feriado f = new Feriado();
		f.fecha = Objects.requireNonNull(fecha, "fecha");
		f.descripcion = Objects.requireNonNull(descripcion, "descripcion");
		f.vigente = Boolean.TRUE;
		return f;
	}

	public void anular(String motivo, String por, LocalDateTime cuando) {
		if (!isVigente()) {
			throw new IllegalStateException("El feriado ya está anulado");
		}
		vigente = null;
		motivoAnulacion = Objects.requireNonNull(motivo, "motivo");
		anuladoPor = Objects.requireNonNull(por, "por");
		anuladoEn = Objects.requireNonNull(cuando, "cuando");
	}

	public boolean isVigente() {
		return Boolean.TRUE.equals(vigente);
	}

	public LocalDate getFecha() {
		return fecha;
	}

	public String getDescripcion() {
		return descripcion;
	}

	public String getAnuladoPor() {
		return anuladoPor;
	}

	public LocalDateTime getAnuladoEn() {
		return anuladoEn;
	}

	public String getMotivoAnulacion() {
		return motivoAnulacion;
	}
}
