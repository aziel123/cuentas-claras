package pe.edu.virgenmaria.cuentasclaras.alumnos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.util.Objects;

/**
 * Registro de una importación CONFIRMADA de alumnos desde Excel. Solo inserción: Hibernate no la actualiza
 * ({@code @Immutable}), la aplicación no la borra y en MySQL {@code cc_app} solo tiene INSERT.
 * No guarda el archivo ni datos personales: su huella SHA-256 (para avisar si se reimporta) y los conteos.
 */
@Entity
@Immutable
@Table(name = "importacion_alumnos")
public class ImportacionAlumnos extends BaseEntity {

	@Column(name = "anio_escolar_id", nullable = false, updatable = false)
	private Long anioEscolarId;

	@Column(name = "archivo_nombre", nullable = false, updatable = false, length = 150)
	private String archivoNombre;

	@Column(name = "archivo_sha256", nullable = false, updatable = false, length = 64)
	private String archivoSha256;

	@Column(name = "archivo_bytes", nullable = false, updatable = false)
	private int archivoBytes;

	@Column(nullable = false, updatable = false)
	private int filas;

	@Column(name = "alumnos_nuevos", nullable = false, updatable = false)
	private int alumnosNuevos;

	@Column(name = "alumnos_actualizados", nullable = false, updatable = false)
	private int alumnosActualizados;

	@Column(name = "alumnos_sin_cambios", nullable = false, updatable = false)
	private int alumnosSinCambios;

	@Column(name = "apoderados_nuevos", nullable = false, updatable = false)
	private int apoderadosNuevos;

	@Column(name = "apoderados_actualizados", nullable = false, updatable = false)
	private int apoderadosActualizados;

	@Column(name = "familias_nuevas", nullable = false, updatable = false)
	private int familiasNuevas;

	@Column(name = "matriculas_nuevas", nullable = false, updatable = false)
	private int matriculasNuevas;

	protected ImportacionAlumnos() {
		// requerido por JPA
	}

	/** Conteos de una importación confirmada. */
	public record Conteos(int filas, int alumnosNuevos, int alumnosActualizados, int alumnosSinCambios,
			int apoderadosNuevos, int apoderadosActualizados, int familiasNuevas, int matriculasNuevas) {
	}

	public static ImportacionAlumnos registrar(Long anioEscolarId, String archivoNombre, String archivoSha256,
			int archivoBytes, Conteos conteos) {
		ImportacionAlumnos importacion = new ImportacionAlumnos();
		importacion.anioEscolarId = Objects.requireNonNull(anioEscolarId, "anioEscolarId");
		importacion.archivoNombre = recortar(Objects.requireNonNull(archivoNombre, "archivoNombre"), 150);
		importacion.archivoSha256 = Objects.requireNonNull(archivoSha256, "archivoSha256");
		importacion.archivoBytes = archivoBytes;
		importacion.filas = conteos.filas();
		importacion.alumnosNuevos = conteos.alumnosNuevos();
		importacion.alumnosActualizados = conteos.alumnosActualizados();
		importacion.alumnosSinCambios = conteos.alumnosSinCambios();
		importacion.apoderadosNuevos = conteos.apoderadosNuevos();
		importacion.apoderadosActualizados = conteos.apoderadosActualizados();
		importacion.familiasNuevas = conteos.familiasNuevas();
		importacion.matriculasNuevas = conteos.matriculasNuevas();
		return importacion;
	}

	@PreUpdate
	@PreRemove
	void soloInsercion() {
		throw new IllegalStateException("Una importación registrada no se modifica ni se borra");
	}

	private static String recortar(String texto, int maximo) {
		return texto.length() <= maximo ? texto : texto.substring(0, maximo);
	}

	public Long getAnioEscolarId() {
		return anioEscolarId;
	}

	public String getArchivoNombre() {
		return archivoNombre;
	}

	public String getArchivoSha256() {
		return archivoSha256;
	}

	public int getArchivoBytes() {
		return archivoBytes;
	}

	public Conteos conteos() {
		return new Conteos(filas, alumnosNuevos, alumnosActualizados, alumnosSinCambios, apoderadosNuevos,
				apoderadosActualizados, familiasNuevas, matriculasNuevas);
	}
}
