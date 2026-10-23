package pe.edu.virgenmaria.cuentasclaras.academico.siagie.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Sprint 10, tanda 3 (M12; decisión 189 ajustada por el coordinador): Dirección delega con su firma la exportación de
 * calificaciones para el SIAGIE a UNA persona de Administración (nunca de Caja ni un docente: quien cobra no recibe la
 * nómina de calificaciones de los menores) por hasta {@value #MAX_DIAS} días. SOLO INSERCIÓN: vence sola; desactivar la
 * cuenta la corta (trg_exportacion_calificaciones_nace exige la cuenta activa).
 */
@Entity
@Table(name = "delegacion_siagie")
public class DelegacionSiagie extends BaseEntity {

	public static final int MAX_DIAS = 15;

	@Column(name = "usuario_id", nullable = false, updatable = false)
	private Long usuarioId;

	@Column(nullable = false, updatable = false)
	private LocalDate desde;

	@Column(nullable = false, updatable = false)
	private LocalDate hasta;

	@Column(name = "autorizado_por", nullable = false, updatable = false, length = 60)
	private String autorizadoPor;

	protected DelegacionSiagie() {
		// requerido por JPA
	}

	public static DelegacionSiagie nueva(Long usuarioId, LocalDate desde, LocalDate hasta, String director) {
		Objects.requireNonNull(desde, "desde");
		Objects.requireNonNull(hasta, "hasta");
		if (hasta.isBefore(desde) || hasta.isAfter(desde.plusDays(MAX_DIAS))) {
			throw new IllegalArgumentException("La delegación dura de 1 a " + MAX_DIAS + " días");
		}
		DelegacionSiagie d = new DelegacionSiagie();
		d.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId");
		d.desde = desde;
		d.hasta = hasta;
		d.autorizadoPor = Objects.requireNonNull(director, "director");
		return d;
	}

	public boolean vigenteEl(LocalDate dia) {
		return !dia.isBefore(desde) && !dia.isAfter(hasta);
	}

	@PreUpdate
	void impedirCambio() {
		throw new IllegalStateException("Una delegación no se cambia: vence sola.");
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Una delegación no se borra.");
	}

	public Long getUsuarioId() {
		return usuarioId;
	}

	public LocalDate getDesde() {
		return desde;
	}

	public LocalDate getHasta() {
		return hasta;
	}

	public String getAutorizadoPor() {
		return autorizadoPor;
	}
}
