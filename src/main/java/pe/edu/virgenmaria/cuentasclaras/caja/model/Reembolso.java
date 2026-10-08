package pe.edu.virgenmaria.cuentasclaras.caja.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.TextoSeguro;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Reembolso de una devolución aprobada (correcciones del sprint 3, A1 y A2). SOLO INSERCIÓN; uno por anulación. Lo
 * registra Administración, nunca la cajera del pago (CHECK y trigger), por el MISMO medio del pago:
 * <ul>
 *   <li>un pago digital vuelve a la cuenta de origen, con el número de operación del reembolso;</li>
 *   <li>el efectivo se entrega contra la firma, el nombre y el documento de quien lo recibe (la nota de crédito impresa
 *       trae el espacio para firmar).</li>
 * </ul>
 * Mientras no se registre, Promotoría tiene una alerta crítica.
 */
@Entity
@Immutable
@Table(name = "reembolso")
public class Reembolso extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "anulacion_pago_id", nullable = false, updatable = false)
	private AnulacionPago anulacion;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private MedioPago medio;

	@Column(name = "numero_operacion", updatable = false, length = 30)
	private String numeroOperacion;

	@Column(name = "recibido_por_nombre", updatable = false, length = 150)
	private String recibidoPorNombre;

	@Column(name = "recibido_por_documento", updatable = false, length = 20)
	private String recibidoPorDocumento;

	@Column(nullable = false, updatable = false, precision = 10, scale = 2)
	private BigDecimal monto;

	@Column(nullable = false, updatable = false)
	private LocalDate fecha;

	@Column(name = "cajero_pago", nullable = false, updatable = false, length = 60)
	private String cajeroPago;

	protected Reembolso() {
		// requerido por JPA
	}

	/**
	 * @param numeroOperacion del reembolso digital (a la cuenta de origen); null en efectivo
	 * @param recibidoPorNombre quien recibe el efectivo (firma la nota de crédito); null en digital
	 */
	public static Reembolso registrar(AnulacionPago anulacion, String numeroOperacion, String recibidoPorNombre,
			String recibidoPorDocumento, LocalDate fecha, String por) {
		Objects.requireNonNull(anulacion, "anulacion");
		if (anulacion.getTipo() != TipoAnulacion.DEVOLUCION) {
			throw new ReglaNegocioException("Solo una devolución se reembolsa (una corrección pasa a otras cuotas).");
		}
		if (por == null || por.equals(anulacion.getCajeroPago())) {
			throw new ReglaNegocioException("La cajera del pago no registra su reembolso: lo hace Administración.");
		}
		Reembolso r = new Reembolso();
		r.anulacion = anulacion;
		r.medio = anulacion.getPago().getMedio();
		r.monto = anulacion.getMonto();
		r.cajeroPago = anulacion.getCajeroPago();
		r.fecha = Objects.requireNonNull(fecha, "fecha");
		if (r.medio == MedioPago.EFECTIVO) {
			String nombre = Normalizador.limpiar(recibidoPorNombre);
			String documento = Normalizador.sinEspacios(recibidoPorDocumento);
			if (nombre == null || nombre.length() < 5 || documento == null || !documento.matches("[0-9A-Za-z]{8,20}")) {
				throw new ReglaNegocioException("Escribe el nombre y el documento de quien recibe el efectivo (firma la nota "
						+ "de crédito).");
			}
			r.recibidoPorNombre = TextoSeguro.exigir(nombre, "el nombre");
			r.recibidoPorDocumento = documento;
		}
		else {
			r.numeroOperacion = NumeroOperacion.normalizar(numeroOperacion);
		}
		return r;
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Los reembolsos no se borran.");
	}

	public AnulacionPago getAnulacion() {
		return anulacion;
	}

	public MedioPago getMedio() {
		return medio;
	}

	public String getNumeroOperacion() {
		return numeroOperacion;
	}

	public String getRecibidoPorNombre() {
		return recibidoPorNombre;
	}

	public String getRecibidoPorDocumento() {
		return recibidoPorDocumento;
	}

	public BigDecimal getMonto() {
		return monto;
	}

	public LocalDate getFecha() {
		return fecha;
	}

	public String getCajeroPago() {
		return cajeroPago;
	}
}
