package pe.edu.virgenmaria.cuentasclaras.pasarela.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

/**
 * Aviso (webhook) recibido de la pasarela, ya autenticado. Bandeja de entrada idempotente: el mismo evento no se procesa
 * dos veces (UNIQUE por colegio, proveedor y evento). No guarda el cuerpo (puede traer datos del pagador): solo su
 * SHA-256. El aviso solo despierta la consulta a la pasarela: nunca registra dinero por sí mismo.
 */
@Entity
@Table(name = "evento_pasarela")
public class EventoPasarela extends BaseEntity {

	public static final int MAX_INTENTOS = 5;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private ProveedorPasarela proveedor;

	@Column(name = "evento_id", nullable = false, updatable = false, length = 100)
	private String eventoId;

	@Column(nullable = false, updatable = false, length = 60)
	private String tipo;

	@Column(name = "orden_pago_id", updatable = false)
	private Long ordenPagoId;

	@Column(name = "cuerpo_sha256", nullable = false, updatable = false, length = 64)
	private String cuerpoSha256;

	// --- Lo que cambia al procesarse (GRANT UPDATE por columna) ---

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoEvento estado;

	@Column(nullable = false)
	private int intentos;

	@Column(length = 250)
	private String resultado;

	@Column(name = "procesado_en")
	private LocalDateTime procesadoEn;

	protected EventoPasarela() {
		// requerido por JPA
	}

	public static EventoPasarela recibido(ProveedorPasarela proveedor, String eventoId, String tipo, Long ordenPagoId,
			String cuerpoSha256) {
		EventoPasarela evento = new EventoPasarela();
		evento.proveedor = Objects.requireNonNull(proveedor, "proveedor");
		evento.eventoId = recortar(Objects.requireNonNull(eventoId, "eventoId"), 100);
		evento.tipo = recortar(Objects.requireNonNull(tipo, "tipo"), 60);
		evento.ordenPagoId = ordenPagoId;
		evento.cuerpoSha256 = Objects.requireNonNull(cuerpoSha256, "cuerpoSha256");
		evento.estado = EstadoEvento.RECIBIDO;
		evento.intentos = 0;
		return evento;
	}

	/** Procesado (PROCESADO o IGNORADO): ya no se vuelve a procesar. */
	public void terminar(EstadoEvento final_, String detalle, LocalDateTime ahora) {
		if (final_ == EstadoEvento.RECIBIDO) {
			throw new IllegalArgumentException("Un evento termina procesado, ignorado o en error");
		}
		intentos++;
		estado = final_;
		resultado = detalle == null ? null : recortar(detalle, 250);
		procesadoEn = Objects.requireNonNull(ahora, "ahora");
	}

	/** Falló al procesarse: suma un intento; al quinto queda en ERROR (alerta crítica). */
	public void fallo(String detalle, LocalDateTime ahora) {
		intentos++;
		resultado = detalle == null ? null : recortar(detalle, 250);
		if (intentos >= MAX_INTENTOS) {
			estado = EstadoEvento.ERROR;
			procesadoEn = Objects.requireNonNull(ahora, "ahora");
		}
	}

	private static String recortar(String texto, int maximo) {
		return texto.length() <= maximo ? texto : texto.substring(0, maximo);
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Los avisos de la pasarela no se borran.");
	}

	public ProveedorPasarela getProveedor() {
		return proveedor;
	}

	public String getEventoId() {
		return eventoId;
	}

	public String getTipo() {
		return tipo;
	}

	public Long getOrdenPagoId() {
		return ordenPagoId;
	}

	public String getCuerpoSha256() {
		return cuerpoSha256;
	}

	public EstadoEvento getEstado() {
		return estado;
	}

	public int getIntentos() {
		return intentos;
	}

	public String getResultado() {
		return resultado;
	}

	public LocalDateTime getProcesadoEn() {
		return procesadoEn;
	}
}
