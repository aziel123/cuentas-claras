package pe.edu.virgenmaria.cuentasclaras.auditoria.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

/**
 * Correcciones del sprint 5 (S5-M4): huella de la HORA de la bitácora de un colegio, en horario de caja (lunes a sábado,
 * de 08:00 a 19:00). Un recorte del mismo día, antes de la huella diaria, deja de coincidir con la huella de la hora y la
 * huella diaria siguiente no puede retroceder por debajo de ella. SOLO INSERCIÓN.
 */
@Entity
@Table(name = "huella_hora")
public class HuellaHora extends BaseEntity {

	@Column(nullable = false, updatable = false)
	private LocalDateTime momento;

	@Column(nullable = false, updatable = false)
	private long secuencia;

	@Column(nullable = false, updatable = false, length = 16)
	private String codigo;

	protected HuellaHora() {
		// requerido por JPA
	}

	public static HuellaHora de(LocalDateTime momento, long secuencia, String hash) {
		HuellaHora h = new HuellaHora();
		h.momento = Objects.requireNonNull(momento, "momento");
		h.secuencia = secuencia;
		h.codigo = Objects.requireNonNull(hash, "hash").substring(0, 16).toLowerCase(Locale.ROOT);
		return h;
	}

	public boolean coincideCon(String hash) {
		return hash != null && hash.toLowerCase(Locale.ROOT).startsWith(codigo);
	}

	@PreUpdate
	void impedirEdicion() {
		throw new IllegalStateException("La huella de la hora es de solo inserción.");
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("La huella de la hora no se borra.");
	}

	public LocalDateTime getMomento() {
		return momento;
	}

	public long getSecuencia() {
		return secuencia;
	}

	public String getCodigo() {
		return codigo;
	}
}
