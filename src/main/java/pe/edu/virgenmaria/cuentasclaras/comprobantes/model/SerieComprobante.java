package pe.edu.virgenmaria.cuentasclaras.comprobantes.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.util.Objects;

/**
 * Serie de comprobantes (B001, F001, BC01, FC01) con su último número. Para emitir se bloquea
 * ({@code SELECT ... FOR UPDATE}): el número y el comprobante se guardan en la misma transacción, así un error no deja
 * huecos. Solo cambia {@code ultimo_numero} (GRANT por columna) y, en MySQL, un trigger exige que avance de uno en uno.
 */
@Entity
@Table(name = "serie_comprobante")
public class SerieComprobante extends BaseEntity {

	public static final int MAXIMO = 99_999_999;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private TipoComprobante tipo;

	@Column(nullable = false, updatable = false, length = 4)
	private String serie;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private ProveedorComprobantes proveedor;

	@Column(name = "ultimo_numero", nullable = false)
	private int ultimoNumero;

	protected SerieComprobante() {
		// requerido por JPA
	}

	public static SerieComprobante nueva(TipoComprobante tipo, String serie, ProveedorComprobantes proveedor) {
		Objects.requireNonNull(tipo, "tipo");
		Objects.requireNonNull(proveedor, "proveedor");
		if (!formatoValido(tipo, serie)) {
			throw new IllegalArgumentException("Serie no válida para " + tipo + ": " + serie);
		}
		SerieComprobante nueva = new SerieComprobante();
		nueva.tipo = tipo;
		nueva.serie = serie;
		nueva.proveedor = proveedor;
		nueva.ultimoNumero = 0;
		return nueva;
	}

	/** 4 caracteres; B… para boletas, F… para facturas y B… o F… para notas de crédito. */
	public static boolean formatoValido(TipoComprobante tipo, String serie) {
		if (serie == null || !serie.matches("[BF][A-Z0-9]{3}")) {
			return false;
		}
		return tipo == TipoComprobante.NOTA_CREDITO || serie.charAt(0) == tipo.letra();
	}

	/** Toma el número siguiente. Se guarda (con flush) antes de insertar el comprobante que lo usa. */
	public int siguiente() {
		if (ultimoNumero >= MAXIMO) {
			throw new ReglaNegocioException("La serie " + serie + " llegó a su último número: pide una serie nueva.");
		}
		ultimoNumero++;
		return ultimoNumero;
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Las series de comprobantes no se borran.");
	}

	public TipoComprobante getTipo() {
		return tipo;
	}

	public String getSerie() {
		return serie;
	}

	public ProveedorComprobantes getProveedor() {
		return proveedor;
	}

	public int getUltimoNumero() {
		return ultimoNumero;
	}
}
