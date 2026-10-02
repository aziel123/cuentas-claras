package pe.edu.virgenmaria.cuentasclaras.auditoria.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Último eslabón de la cadena de auditoría (fila única {@code id = 1}). Se lee con bloqueo
 * para asignar la secuencia siguiente y encadenar el hash del nuevo evento con el anterior.
 */
@Entity
@Table(name = "auditoria_cadena")
public class EslabonCadena {

	public static final long ID = 1L;

	@Id
	private Long id;

	@Column(name = "ultima_secuencia", nullable = false)
	private long ultimaSecuencia;

	@Column(name = "ultimo_hash", nullable = false, length = 64)
	private String ultimoHash;

	protected EslabonCadena() {
		// requerido por JPA
	}

	public long siguienteSecuencia() {
		return ultimaSecuencia + 1;
	}

	/** Mueve el eslabón al evento recién guardado. */
	public void avanzar(String hash) {
		ultimaSecuencia = siguienteSecuencia();
		ultimoHash = hash;
	}

	public Long getId() {
		return id;
	}

	public long getUltimaSecuencia() {
		return ultimaSecuencia;
	}

	public String getUltimoHash() {
		return ultimoHash;
	}
}
