package pe.edu.virgenmaria.cuentasclaras.colegio.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Colegio cliente de la plataforma. Toda entidad de negocio referencia a un colegio
 * mediante {@code colegioId} (multi-colegio).
 */
@Entity
@Table(name = "colegio")
public class Colegio {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 150)
	private String nombre;

	@Column(length = 11)
	private String ruc;

	@Column(nullable = false)
	private boolean activo = true;

	@Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
	private LocalDateTime creadoEn;

	protected Colegio() {
		// requerido por JPA
	}

	public Colegio(String nombre) {
		this.nombre = nombre;
	}

	public Long getId() {
		return id;
	}

	public String getNombre() {
		return nombre;
	}

	public String getRuc() {
		return ruc;
	}

	public boolean isActivo() {
		return activo;
	}

	public LocalDateTime getCreadoEn() {
		return creadoEn;
	}
}
