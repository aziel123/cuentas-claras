package pe.edu.virgenmaria.cuentasclaras.conciliacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Motivo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Partida: un movimiento del extracto emparejado 1 a 1 con UNA cosa que debía verse en el banco (un pago digital, un
 * depósito, una liquidación, un lote de recaudación o un reembolso) o explicado (intereses, transferencia propia...).
 * <ul>
 *   <li>{@code movimiento_vigente} y {@code objeto_vigente} («PAGO:125») son únicos mientras la partida no esté
 *       DESCARTADA: un movimiento y un objeto tienen una sola partida vigente (UNIQUE).</li>
 *   <li>Nace PROPUESTA con los montos reales del movimiento y del objeto (trigger). El movimiento, el objeto, la regla y
 *       los montos no cambian ({@code updatable = false} y GRANT por columna).</li>
 *   <li>Se CONFIRMA solo con el extracto CONFIRMADO: la EXACTA, el sistema; la SUGERIDA, MANUAL o EXPLICADA, una
 *       persona que no cobró, no registró ni depositó lo emparejado (CHECK y trigger). Una vez resuelta no cambia.</li>
 * </ul>
 */
@Entity
@Table(name = "partida_conciliacion")
public class PartidaConciliacion extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "movimiento_id", nullable = false, updatable = false)
	private MovimientoBancario movimiento;

	@Enumerated(EnumType.STRING)
	@Column(name = "objeto_tipo", nullable = false, updatable = false, length = 20)
	private ObjetoPartida objetoTipo;

	@Column(name = "pago_id", updatable = false)
	private Long pagoId;

	@Column(name = "deposito_id", updatable = false)
	private Long depositoId;

	@Column(name = "liquidacion_id", updatable = false)
	private Long liquidacionId;

	@Column(name = "lote_recaudacion_id", updatable = false)
	private Long loteRecaudacionId;

	@Column(name = "reembolso_id", updatable = false)
	private Long reembolsoId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private ReglaPartida regla;

	@Column(name = "monto_movimiento", nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal montoMovimiento;

	@Column(name = "monto_objeto", nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal montoObjeto;

	@Column(nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal diferencia;

	@Enumerated(EnumType.STRING)
	@Column(updatable = false, length = 30)
	private CategoriaExplicacion categoria;

	@Column(updatable = false, length = 500)
	private String nota;

	// --- Lo que puede cambiar (GRANT UPDATE por columna; trg_partida_conciliacion_estado vigila la resolución) ---

	@Column(name = "movimiento_vigente")
	private Long movimientoVigente;

	@Column(name = "objeto_vigente", length = 40)
	private String objetoVigente;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoPartida estado;

	@Column(name = "resuelto_por", length = 60)
	private String resueltoPor;

	@Column(name = "resuelto_en")
	private LocalDateTime resueltoEn;

	protected PartidaConciliacion() {
		// requerido por JPA
	}

	/**
	 * Propuesta de emparejar el movimiento con un objeto. EXACTA exige diferencia 0; MANUAL exige una nota.
	 *
	 * @param montoObjeto el monto real del objeto (el neto de la liquidación, el total del lote...)
	 */
	public static PartidaConciliacion proponer(MovimientoBancario movimiento, ObjetoPartida tipo, Long objetoId,
			BigDecimal montoObjeto, ReglaPartida regla, String nota) {
		Objects.requireNonNull(tipo, "tipo");
		if (tipo == ObjetoPartida.EXPLICACION || regla == ReglaPartida.EXPLICADA) {
			throw new IllegalArgumentException("Una explicación se registra con explicar()");
		}
		if (movimiento.getTipo() != tipo.movimiento()) {
			throw new ReglaNegocioException("Un " + tipo.etiqueta().toLowerCase(java.util.Locale.ROOT) + " se empareja "
					+ "con un " + tipo.movimiento().etiqueta().toLowerCase(java.util.Locale.ROOT) + " del banco.");
		}
		PartidaConciliacion p = nueva(movimiento, regla, montoObjeto);
		p.objetoTipo = tipo;
		switch (tipo) {
			case PAGO -> p.pagoId = Objects.requireNonNull(objetoId, "objetoId");
			case DEPOSITO -> p.depositoId = Objects.requireNonNull(objetoId, "objetoId");
			case LIQUIDACION -> p.liquidacionId = Objects.requireNonNull(objetoId, "objetoId");
			case LOTE_RECAUDACION -> p.loteRecaudacionId = Objects.requireNonNull(objetoId, "objetoId");
			case REEMBOLSO -> p.reembolsoId = Objects.requireNonNull(objetoId, "objetoId");
			default -> throw new IllegalArgumentException(tipo.name());
		}
		p.objetoVigente = tipo.clave(objetoId);
		if (regla == ReglaPartida.EXACTA && p.diferencia.signum() != 0) {
			throw new IllegalArgumentException("Una partida exacta no tiene diferencia");
		}
		if (regla == ReglaPartida.MANUAL) {
			p.nota = Motivo.exigir(nota);
		}
		return p;
	}

	/** Un movimiento ajeno a la cobranza (intereses, transferencia propia...), con categoría y nota (de 10 a 500). */
	public static PartidaConciliacion explicar(MovimientoBancario movimiento, CategoriaExplicacion categoria,
			String nota) {
		if (categoria == null) {
			throw new ReglaNegocioException("Elige qué es el movimiento (intereses, transferencia propia...).");
		}
		PartidaConciliacion p = nueva(movimiento, ReglaPartida.EXPLICADA, movimiento.getMonto());
		p.objetoTipo = ObjetoPartida.EXPLICACION;
		p.categoria = categoria;
		p.nota = Motivo.exigir(nota);
		p.objetoVigente = null;
		return p;
	}

	private static PartidaConciliacion nueva(MovimientoBancario movimiento, ReglaPartida regla, BigDecimal montoObjeto) {
		PartidaConciliacion p = new PartidaConciliacion();
		p.movimiento = Objects.requireNonNull(movimiento, "movimiento");
		p.movimientoVigente = Objects.requireNonNull(movimiento.getId(), "el movimiento debe estar guardado");
		p.regla = Objects.requireNonNull(regla, "regla");
		p.montoMovimiento = movimiento.getMonto();
		p.montoObjeto = Dinero.normalizar(montoObjeto);
		if (p.montoObjeto.signum() <= 0) {
			throw new IllegalArgumentException("El monto del objeto es positivo");
		}
		p.diferencia = p.montoMovimiento.subtract(p.montoObjeto);
		p.estado = EstadoPartida.PROPUESTA;
		return p;
	}

	/**
	 * La confirma {@code por}: el sistema solo las EXACTAS; una persona, las demás. Quien llama ya comprobó que el extracto
	 * está CONFIRMADO y que {@code por} no cobró, registró ni depositó lo emparejado (la base lo vuelve a exigir).
	 */
	public void confirmar(String por, LocalDateTime ahora) {
		exigirPropuesta();
		boolean sistema = ActorSistema.esReservado(por);
		if (sistema != (regla == ReglaPartida.EXACTA)) {
			throw new IllegalStateException(regla == ReglaPartida.EXACTA
					? "Las partidas exactas las confirma el sistema" : "Esta partida la confirma una persona");
		}
		estado = EstadoPartida.CONFIRMADA;
		resueltoPor = Objects.requireNonNull(por, "por");
		resueltoEn = Objects.requireNonNull(ahora, "ahora");
	}

	/** No es (o ya no sirve): libera el movimiento y el objeto. La nota queda en la bitácora. */
	public void descartar(String por, LocalDateTime ahora) {
		exigirPropuesta();
		estado = EstadoPartida.DESCARTADA;
		movimientoVigente = null;
		objetoVigente = null;
		resueltoPor = Objects.requireNonNull(por, "por");
		resueltoEn = Objects.requireNonNull(ahora, "ahora");
	}

	private void exigirPropuesta() {
		if (estado != EstadoPartida.PROPUESTA) {
			throw new ReglaNegocioException("Esta partida ya se resolvió.");
		}
	}

	/** El id del objeto emparejado ({@code null} en una explicación). */
	public Long objetoId() {
		return switch (objetoTipo) {
			case PAGO -> pagoId;
			case DEPOSITO -> depositoId;
			case LIQUIDACION -> liquidacionId;
			case LOTE_RECAUDACION -> loteRecaudacionId;
			case REEMBOLSO -> reembolsoId;
			case EXPLICACION -> null;
		};
	}

	public boolean vigente() {
		return estado != EstadoPartida.DESCARTADA;
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Las partidas de la conciliación no se borran: se descartan.");
	}

	public MovimientoBancario getMovimiento() {
		return movimiento;
	}

	public ObjetoPartida getObjetoTipo() {
		return objetoTipo;
	}

	public Long getPagoId() {
		return pagoId;
	}

	public Long getDepositoId() {
		return depositoId;
	}

	public Long getLiquidacionId() {
		return liquidacionId;
	}

	public Long getLoteRecaudacionId() {
		return loteRecaudacionId;
	}

	public Long getReembolsoId() {
		return reembolsoId;
	}

	public ReglaPartida getRegla() {
		return regla;
	}

	public BigDecimal getMontoMovimiento() {
		return montoMovimiento;
	}

	public BigDecimal getMontoObjeto() {
		return montoObjeto;
	}

	public BigDecimal getDiferencia() {
		return diferencia;
	}

	public CategoriaExplicacion getCategoria() {
		return categoria;
	}

	public String getNota() {
		return nota;
	}

	public Long getMovimientoVigente() {
		return movimientoVigente;
	}

	public String getObjetoVigente() {
		return objetoVigente;
	}

	public EstadoPartida getEstado() {
		return estado;
	}

	public String getResueltoPor() {
		return resueltoPor;
	}

	public LocalDateTime getResueltoEn() {
		return resueltoEn;
	}
}
