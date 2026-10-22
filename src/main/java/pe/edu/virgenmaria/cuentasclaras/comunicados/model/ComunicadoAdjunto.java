package pe.edu.virgenmaria.cuentasclaras.comunicados.model;

import jakarta.persistence.Lob;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.util.Objects;

/**
 * Sprint 9, tanda 3 (sección 3.9, B3, B5): el PDF saneado de un comunicado, tal como lo volvió a guardar el analizador
 * (proceso hijo). SOLO INSERCIÓN (1142). Guarda los dos SHA-256 (el del original y el del saneado; solo se sirve el
 * saneado) y si requiere revisión (poco texto o imágenes): ese PDF sale solo con la aprobación del otro órgano.
 */
@Entity
@Table(name = "comunicado_adjunto")
public class ComunicadoAdjunto extends BaseEntity {

	// Como el logo del colegio (sprint 8): MEDIUMBLOB, hasta 2 MB (CHECK de la base).
	@Lob
	@Column(nullable = false, updatable = false, columnDefinition = "MEDIUMBLOB")
	private byte[] contenido;

	@Column(nullable = false, updatable = false)
	private Integer bytes;

	@Column(nullable = false, updatable = false)
	private Integer paginas;

	@Column(nullable = false, updatable = false, length = 64)
	private String sha256;

	@Column(name = "sha256_original", nullable = false, updatable = false, length = 64)
	private String sha256Original;

	@Column(name = "requiere_revision", nullable = false, updatable = false)
	private Boolean requiereRevision;

	protected ComunicadoAdjunto() {
		// requerido por JPA
	}

	public static ComunicadoAdjunto nuevo(byte[] saneado, int paginas, String sha256, String sha256Original,
			boolean requiereRevision) {
		ComunicadoAdjunto a = new ComunicadoAdjunto();
		a.contenido = Objects.requireNonNull(saneado, "saneado").clone();
		a.bytes = saneado.length;
		a.paginas = paginas;
		a.sha256 = Objects.requireNonNull(sha256, "sha256");
		a.sha256Original = Objects.requireNonNull(sha256Original, "sha256Original");
		a.requiereRevision = requiereRevision;
		return a;
	}

	@PreUpdate
	void impedirCambio() {
		throw new IllegalStateException("El adjunto de un comunicado es de solo inserción.");
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("El adjunto de un comunicado no se borra.");
	}

	public byte[] getContenido() {
		return contenido.clone();
	}

	public int getBytes() {
		return bytes;
	}

	public int getPaginas() {
		return paginas;
	}

	public String getSha256() {
		return sha256;
	}

	public String getSha256Original() {
		return sha256Original;
	}

	public boolean isRequiereRevision() {
		return requiereRevision;
	}
}
