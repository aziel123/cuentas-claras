package pe.edu.virgenmaria.cuentasclaras.operacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/**
 * Fila de {@code respaldo} (V24): la escribe SOLO {@code cc_respaldo} desde {@code scripts/respaldo/respaldar.sh}
 * (GRANT y {@code trg_respaldo_registro}); la aplicación solo la lee para saber si hubo respaldo sin tener las
 * credenciales del almacenamiento. Es técnica y de toda la base: no es de un colegio (no extiende BaseEntity) y no
 * guarda datos personales.
 */
@Entity
@Immutable
@Table(name = "respaldo")
public class Respaldo {

	/** Destino de una carpeta local: solo en dev, CI y piloto (en prod la base no lo admite). */
	public static final String DESTINO_SIMULADO = "simulado";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private LocalDateTime inicio;

	@Column(nullable = false)
	private LocalDateTime fin;

	@Column(nullable = false, length = 120)
	private String archivo;

	@Column(nullable = false, length = 64)
	private String sha256;

	@Column(nullable = false)
	private long bytes;

	@Column(name = "version_esquema", nullable = false, length = 20)
	private String versionEsquema;

	@Column(name = "secuencia_antes", nullable = false)
	private long secuenciaAntes;

	@Column(name = "hash_antes", nullable = false, length = 64)
	private String hashAntes;

	@Column(name = "secuencia_despues", nullable = false)
	private long secuenciaDespues;

	@Column(name = "hash_despues", nullable = false, length = 64)
	private String hashDespues;

	@Column(nullable = false, length = 4000)
	private String conteos;

	@Column(name = "huella_objetos", length = 64)
	private String huellaObjetos;

	@Column(nullable = false, length = 60)
	private String destino;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ComparacionRespaldo comparacion;

	@Column(length = 1000)
	private String diferencias;

	@Column(name = "creado_en", nullable = false)
	private LocalDateTime creadoEn;

	@Column(name = "creado_por", nullable = false, length = 60)
	private String creadoPor;

	protected Respaldo() {
		// requerido por JPA
	}

	public Long getId() {
		return id;
	}

	public LocalDateTime getInicio() {
		return inicio;
	}

	public LocalDateTime getFin() {
		return fin;
	}

	public String getArchivo() {
		return archivo;
	}

	public String getSha256() {
		return sha256;
	}

	public long getBytes() {
		return bytes;
	}

	public String getVersionEsquema() {
		return versionEsquema;
	}

	public long getSecuenciaDespues() {
		return secuenciaDespues;
	}

	public String getDestino() {
		return destino;
	}

	public ComparacionRespaldo getComparacion() {
		return comparacion;
	}

	public String getDiferencias() {
		return diferencias;
	}

	public boolean simulado() {
		return DESTINO_SIMULADO.equals(destino);
	}
}
