package pe.edu.virgenmaria.cuentasclaras.comun.archivo;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Lob;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.util.Objects;

/**
 * Archivo ORIGINAL subido (recaudación del banco; en la tanda 3, extracto o liquidación), tal cual llegó y con su
 * SHA-256: es la evidencia con la que cualquiera puede comparar lo cargado con lo que entregó el banco. SOLO INSERCIÓN
 * ({@code @Immutable} y, en MySQL, sin GRANT de UPDATE ni DELETE). Hasta 2 MB (CHECK).
 */
@Entity
@Immutable
@Table(name = "archivo_cargado")
public class ArchivoCargado extends BaseEntity {

	/** Tamaño máximo (CHECK {@code ck_archivo_cargado_tamano}). */
	public static final int MAXIMO_BYTES = 2 * 1024 * 1024;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private TipoArchivo tipo;

	@Column(nullable = false, updatable = false, length = 150)
	private String nombre;

	@Column(nullable = false, updatable = false, length = 64)
	private String sha256;

	@Column(nullable = false, updatable = false)
	private int bytes;

	@Lob
	@Basic(fetch = FetchType.LAZY)
	@Column(nullable = false, updatable = false, columnDefinition = "LONGBLOB")
	private byte[] contenido;

	protected ArchivoCargado() {
		// requerido por JPA
	}

	static ArchivoCargado nuevo(TipoArchivo tipo, String nombre, String sha256, byte[] contenido) {
		Objects.requireNonNull(contenido, "contenido");
		if (contenido.length == 0 || contenido.length > MAXIMO_BYTES) {
			throw new IllegalArgumentException("El archivo debe pesar entre 1 byte y 2 MB");
		}
		ArchivoCargado archivo = new ArchivoCargado();
		archivo.tipo = Objects.requireNonNull(tipo, "tipo");
		archivo.nombre = Objects.requireNonNull(nombre, "nombre");
		archivo.sha256 = Objects.requireNonNull(sha256, "sha256");
		archivo.bytes = contenido.length;
		archivo.contenido = contenido.clone();
		return archivo;
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Los archivos originales del banco no se borran: son evidencia.");
	}

	public TipoArchivo getTipo() {
		return tipo;
	}

	public String getNombre() {
		return nombre;
	}

	public String getSha256() {
		return sha256;
	}

	public int getBytes() {
		return bytes;
	}

	/** Una copia del contenido original. */
	public byte[] contenido() {
		return contenido.clone();
	}
}
