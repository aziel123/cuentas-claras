package pe.edu.virgenmaria.cuentasclaras.familias.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * «¿Algo no cuadra?» (sprint 5, tanda 2): el aviso que una familia envía desde el portal. Lo ven y lo atienden SOLO
 * Promotoría y Dirección: Caja y Administración pueden ser parte del problema (G1). El texto, el tipo, la familia y las
 * referencias no cambian (sin GRANT de UPDATE: 1143); se atiende una vez, con respuesta, por una persona.
 */
@Entity
@Table(name = "aviso_familia")
public class AvisoFamilia extends BaseEntity {

	public static final int MAX_TEXTO = 500;

	@Column(name = "familia_id", nullable = false, updatable = false)
	private Long familiaId;

	@Column(name = "apoderado_id", nullable = false, updatable = false)
	private Long apoderadoId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40, updatable = false)
	private TipoAvisoFamilia tipo;

	@Column(name = "pago_id", updatable = false)
	private Long pagoId;

	@Column(name = "cuota_id", updatable = false)
	private Long cuotaId;

	@Column(nullable = false, length = MAX_TEXTO, updatable = false)
	private String texto;

	/** Sprint 7, tanda 3: solo en los pedidos sobre datos personales; no cambia (1143 en MySQL). */
	@Enumerated(EnumType.STRING)
	@Column(length = 20, updatable = false)
	private DerechoDatos derecho;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoAvisoFamilia estado;

	@Column(name = "atendido_por", length = 60)
	private String atendidoPor;

	@Column(name = "atendido_en")
	private LocalDateTime atendidoEn;

	@Column(length = MAX_TEXTO)
	private String respuesta;

	protected AvisoFamilia() {
		// requerido por JPA
	}

	public static AvisoFamilia nuevo(Long familiaId, Long apoderadoId, TipoAvisoFamilia tipo, Long pagoId, Long cuotaId,
			String texto) {
		return nuevo(familiaId, apoderadoId, tipo, pagoId, cuotaId, null, texto);
	}

	/**
	 * Sprint 7, tanda 3: un pedido sobre datos personales lleva su derecho y no refiere pagos ni cuotas; los demás avisos
	 * no llevan derecho (también lo exige el CHECK ck_aviso_familia_derecho).
	 */
	public static AvisoFamilia nuevo(Long familiaId, Long apoderadoId, TipoAvisoFamilia tipo, Long pagoId, Long cuotaId,
			DerechoDatos derecho, String texto) {
		Objects.requireNonNull(tipo, "tipo");
		if (tipo == TipoAvisoFamilia.DATOS_PERSONALES) {
			if (derecho == null) {
				throw new ReglaNegocioException("Elige qué quieres pedir sobre tus datos personales.");
			}
			if (pagoId != null || cuotaId != null) {
				throw new ReglaNegocioException("Un pedido sobre tus datos personales no se refiere a un pago ni a una "
						+ "cuota: deja esos campos vacíos.");
			}
		}
		else if (derecho != null) {
			throw new ReglaNegocioException("El pedido sobre datos personales va con el tipo «"
					+ TipoAvisoFamilia.DATOS_PERSONALES.etiqueta() + "».");
		}
		AvisoFamilia aviso = new AvisoFamilia();
		aviso.derecho = derecho;
		aviso.familiaId = Objects.requireNonNull(familiaId, "familiaId");
		aviso.apoderadoId = Objects.requireNonNull(apoderadoId, "apoderadoId");
		aviso.tipo = Objects.requireNonNull(tipo, "tipo");
		aviso.pagoId = pagoId;
		aviso.cuotaId = cuotaId;
		aviso.texto = limpiar(texto, "Cuéntanos qué pasó (hasta " + MAX_TEXTO + " caracteres).");
		aviso.estado = EstadoAvisoFamilia.ABIERTO;
		return aviso;
	}

	/** Promotoría o Dirección responden (la familia ve la respuesta en el portal). Una sola vez. */
	public void atender(String respuesta, String quien, LocalDateTime ahora) {
		if (estado != EstadoAvisoFamilia.ABIERTO) {
			throw new ReglaNegocioException("Este aviso ya fue atendido.");
		}
		this.respuesta = limpiar(respuesta, "Escribe la respuesta para la familia (hasta " + MAX_TEXTO + " caracteres).");
		atendidoPor = Objects.requireNonNull(quien, "quien");
		atendidoEn = Objects.requireNonNull(ahora, "ahora");
		estado = EstadoAvisoFamilia.ATENDIDO;
	}

	private static String limpiar(String texto, String mensaje) {
		String limpio = texto == null ? "" : texto.strip().replaceAll("\\s+", " ");
		if (limpio.length() < 5 || limpio.length() > MAX_TEXTO) {
			throw new ReglaNegocioException(mensaje + " Escribe al menos 5 caracteres.");
		}
		return limpio;
	}

	public Long getFamiliaId() {
		return familiaId;
	}

	public Long getApoderadoId() {
		return apoderadoId;
	}

	public TipoAvisoFamilia getTipo() {
		return tipo;
	}

	public Long getPagoId() {
		return pagoId;
	}

	public Long getCuotaId() {
		return cuotaId;
	}

	public DerechoDatos getDerecho() {
		return derecho;
	}

	public String getTexto() {
		return texto;
	}

	public EstadoAvisoFamilia getEstado() {
		return estado;
	}

	public String getAtendidoPor() {
		return atendidoPor;
	}

	public LocalDateTime getAtendidoEn() {
		return atendidoEn;
	}

	public String getRespuesta() {
		return respuesta;
	}
}
