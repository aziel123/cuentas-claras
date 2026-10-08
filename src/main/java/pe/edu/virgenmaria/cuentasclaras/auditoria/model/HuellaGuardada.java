package pe.edu.virgenmaria.cuentasclaras.auditoria.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

/**
 * Huella diaria de la bitácora de un colegio (sprint 5): su último evento hasta las 23:59:59 del día (secuencia global y
 * los 16 primeros caracteres de su hash) y cuántos eventos tuvo ese día. La registra {@code sistema.auditoria} a las
 * 06:00 y sale por mensaje al celular de Promotoría (y al correo externo del contador si el DBA lo configuró): lo que
 * vale es esa copia fuera del sistema. SOLO INSERCIÓN (sin GRANT de UPDATE: 1142); en MySQL un trigger exige que
 * coincida con un evento de ESE colegio.
 */
@Entity
@Table(name = "huella_bitacora")
public class HuellaGuardada extends BaseEntity {

	@Column(nullable = false, updatable = false)
	private LocalDate fecha;

	@Column(nullable = false, updatable = false)
	private long secuencia;

	@Column(nullable = false, updatable = false, length = 16)
	private String codigo;

	@Column(name = "eventos_del_dia", nullable = false, updatable = false)
	private int eventosDelDia;

	protected HuellaGuardada() {
		// requerido por JPA
	}

	public static HuellaGuardada de(LocalDate fecha, long secuencia, String hash, int eventosDelDia) {
		HuellaGuardada h = new HuellaGuardada();
		h.fecha = Objects.requireNonNull(fecha, "fecha");
		h.secuencia = secuencia;
		h.codigo = Objects.requireNonNull(hash, "hash").substring(0, 16).toLowerCase(Locale.ROOT);
		h.eventosDelDia = eventosDelDia;
		return h;
	}

	/** Si el hash de un evento (el de su secuencia) coincide con esta huella. */
	public boolean coincideCon(String hash) {
		return hash != null && hash.toLowerCase(Locale.ROOT).startsWith(codigo);
	}

	@PreUpdate
	void impedirEdicion() {
		throw new IllegalStateException("La huella de la bitácora es de solo inserción.");
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("La huella de la bitácora no se borra.");
	}

	public LocalDate getFecha() {
		return fecha;
	}

	public long getSecuencia() {
		return secuencia;
	}

	public String getCodigo() {
		return codigo;
	}

	public int getEventosDelDia() {
		return eventosDelDia;
	}
}
