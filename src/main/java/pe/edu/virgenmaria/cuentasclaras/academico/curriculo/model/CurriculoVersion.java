package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Sprint 10, tanda 1 (sección 3.2, decisión 184): una versión del catálogo nacional (CNEB). Es un dato de referencia
 * nacional, como el enum {@code Grado}: no tiene {@code colegio_id} y la aplicación NO la escribe (solo la migración con
 * {@code cc_migrador}; con {@code cc_app}, 1142). Corregirla es otra versión con {@code reemplazaId}: ninguna nota se toca.
 * Su huella la verifica {@code VerificadorCatalogoCurriculo} al arrancar y a diario.
 */
@Entity
@Immutable
@Table(name = "curriculo_version")
public class CurriculoVersion {

	@Id
	private Long id;

	@Column(nullable = false, updatable = false, length = 40)
	private String codigo;

	@Column(nullable = false, updatable = false, length = 200)
	private String nombre;

	@Column(nullable = false, updatable = false, length = 500)
	private String normas;

	@Column(name = "vigente_desde", nullable = false, updatable = false)
	private LocalDate vigenteDesde;

	@Column(name = "reemplaza_id", updatable = false)
	private Long reemplazaId;

	@Column(updatable = false, length = 500)
	private String nota;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private LocalDateTime creadoEn;

	protected CurriculoVersion() {
		// requerido por JPA
	}

	@PreUpdate
	@PreRemove
	void impedirCambios() {
		throw new IllegalStateException("El catálogo nacional no se cambia ni se borra: una corrección es una versión nueva.");
	}

	public Long getId() {
		return id;
	}

	public String getCodigo() {
		return codigo;
	}

	public String getNombre() {
		return nombre;
	}

	public String getNormas() {
		return normas;
	}

	public LocalDate getVigenteDesde() {
		return vigenteDesde;
	}

	public Long getReemplazaId() {
		return reemplazaId;
	}

	public String getNota() {
		return nota;
	}
}
