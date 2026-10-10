package pe.edu.virgenmaria.cuentasclaras.colegio.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.util.Objects;

/**
 * Sprint 8, tanda 1 (sección 3.7, decisión 123): el logo del colegio, SIEMPRE un PNG reescrito por la aplicación
 * ({@code LogosColegio}: se decodifica, se reescala a {@value #MAX_LADO} px como máximo y se vuelve a codificar, así se
 * pierden metadatos y cualquier contenido políglota). Uno vigente por colegio ({@code uk_colegio_logo_vigente} con
 * {@code vigente} TRUE o NULL); el contenido no cambia (GRANT por columna: solo {@code vigente}). Nunca se borra: al
 * subir otro, el anterior deja de estar vigente.
 */
@Entity
@Table(name = "colegio_logo")
public class LogoColegio extends BaseEntity {

	public static final int MAX_LADO = 400;

	public static final int MAX_BYTES = 204_800;

	@Lob
	@Column(nullable = false, updatable = false)
	private byte[] contenido;

	@Column(nullable = false, updatable = false, length = 64)
	private String sha256;

	@Column(nullable = false, updatable = false)
	private int bytes;

	@Column(nullable = false, updatable = false)
	private int ancho;

	@Column(nullable = false, updatable = false)
	private int alto;

	/** {@code TRUE} si es el vigente; {@code null} si fue reemplazado. */
	@Column
	private Boolean vigente = Boolean.TRUE;

	protected LogoColegio() {
		// requerido por JPA
	}

	/** Un logo recién reescrito como PNG, vigente desde que se guarda. */
	public static LogoColegio vigente(byte[] png, String sha256, int ancho, int alto) {
		Objects.requireNonNull(png, "png");
		if (png.length == 0 || png.length > MAX_BYTES || ancho < 1 || ancho > MAX_LADO || alto < 1 || alto > MAX_LADO) {
			throw new IllegalArgumentException("El logo debe ser un PNG de hasta 200 KB y " + MAX_LADO + " px por lado");
		}
		LogoColegio l = new LogoColegio();
		l.contenido = png.clone();
		l.sha256 = Objects.requireNonNull(sha256, "sha256");
		l.bytes = png.length;
		l.ancho = ancho;
		l.alto = alto;
		l.vigente = Boolean.TRUE;
		return l;
	}

	/** Deja de ser el vigente (lo reemplaza otro). */
	public void reemplazar() {
		this.vigente = null;
	}

	@PreRemove
	void impedirBorrado() {
		throw new UnsupportedOperationException("Los logos no se borran: se reemplazan");
	}

	public byte[] getContenido() {
		return contenido.clone();
	}

	public String getSha256() {
		return sha256;
	}

	public int getBytes() {
		return bytes;
	}

	public int getAncho() {
		return ancho;
	}

	public int getAlto() {
		return alto;
	}

	public boolean esVigente() {
		return Boolean.TRUE.equals(vigente);
	}
}
