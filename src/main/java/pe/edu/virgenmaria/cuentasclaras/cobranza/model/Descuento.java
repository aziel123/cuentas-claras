package pe.edu.virgenmaria.cuentasclaras.cobranza.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Motivo;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.TextoSeguro;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Descuento o beca para cuotas de UN alumno. Lo pide Administración y lo aprueba otra persona de Promotoría o
 * Dirección. Lo pedido (alumno, tipo, valor, cuotas, total estimado, motivo, sustento) no cambia: solo se resuelve (GRANT
 * por columna; en MySQL, un trigger impide cambiarlo una vez resuelto). Al aprobarse nacen sus {@link AjusteCuota}.
 */
@Entity
@Table(name = "descuento")
public class Descuento extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "alumno_id", nullable = false, updatable = false)
	private Alumno alumno;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "anio_escolar_id", nullable = false, updatable = false)
	private AnioEscolar anioEscolar;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private TipoDescuento tipo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private ModalidadDescuento modalidad;

	@Column(nullable = false, updatable = false, precision = 10, scale = 2)
	private BigDecimal valor;

	/** «,12,13,14,»: las cuotas pedidas (el trigger del ajuste lo usa con LIKE). */
	@Column(nullable = false, updatable = false, length = 500)
	private String cuotas;

	@Column(name = "total_estimado", nullable = false, updatable = false, precision = 10, scale = 2)
	private BigDecimal totalEstimado;

	@Column(nullable = false, updatable = false, length = 500)
	private String motivo;

	@Column(nullable = false, updatable = false, length = 200)
	private String sustento;

	// --- Lo único que cambia: la resolución (GRANT UPDATE por columna) ---

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoDescuento estado;

	@Column(name = "resuelto_por", length = 60)
	private String resueltoPor;

	@Column(name = "resuelto_en")
	private LocalDateTime resueltoEn;

	protected Descuento() {
		// requerido por JPA
	}

	public static Descuento solicitar(Alumno alumno, AnioEscolar anio, TipoDescuento tipo, ModalidadDescuento modalidad,
			BigDecimal valor, List<Long> cuotaIds, BigDecimal totalEstimado, String motivo, String sustento) {
		if (cuotaIds == null || cuotaIds.isEmpty()) {
			throw new ReglaNegocioException("Elige al menos una cuota.");
		}
		Descuento d = new Descuento();
		d.alumno = Objects.requireNonNull(alumno, "alumno");
		d.anioEscolar = Objects.requireNonNull(anio, "anio");
		d.tipo = Objects.requireNonNull(tipo, "tipo");
		d.modalidad = Objects.requireNonNull(modalidad, "modalidad");
		d.valor = Dinero.normalizar(valor);
		String lista = "," + cuotaIds.stream().distinct().sorted().map(String::valueOf).collect(Collectors.joining(","))
				+ ",";
		if (lista.length() > 500) {
			throw new ReglaNegocioException("Demasiadas cuotas en un solo descuento.");
		}
		d.cuotas = lista;
		d.totalEstimado = Dinero.positivo(totalEstimado, "el total del descuento");
		d.motivo = Motivo.exigir(motivo);
		String limpio = Normalizador.limpiar(sustento);
		if (limpio == null || limpio.length() < 5 || limpio.length() > 200) {
			throw new ReglaNegocioException("Indica el sustento (de 5 a 200 caracteres), por ejemplo «Acta de Dirección "
					+ "N.° 12-2026» o «DNI de los hermanos».");
		}
		d.sustento = TextoSeguro.exigir(limpio, "el sustento");
		d.estado = EstadoDescuento.SOLICITADO;
		return d;
	}

	public List<Long> cuotaIds() {
		return Arrays.stream(cuotas.split(",")).filter(s -> !s.isBlank()).map(Long::valueOf).toList();
	}

	public void aprobar(String por, LocalDateTime ahora) {
		resolver(EstadoDescuento.APROBADO, por, ahora);
	}

	public void rechazar(String por, LocalDateTime ahora) {
		resolver(EstadoDescuento.RECHAZADO, por, ahora);
	}

	private void resolver(EstadoDescuento nuevo, String por, LocalDateTime ahora) {
		if (estado != EstadoDescuento.SOLICITADO) {
			throw new ReglaNegocioException("El descuento ya fue " + estado.etiqueta().toLowerCase() + ".");
		}
		if (por == null || por.equals(getCreadoPor())) {
			throw new AutoaprobacionException("Quien pide el descuento no lo resuelve.");
		}
		estado = nuevo;
		resueltoPor = por;
		resueltoEn = Objects.requireNonNull(ahora, "ahora");
	}

	/** «10 %» o «S/ 50.00 por cuota». */
	public String valorTexto() {
		return modalidad == ModalidadDescuento.PORCENTAJE ? valor.stripTrailingZeros().toPlainString() + " %"
				: Dinero.formatear(valor) + " por cuota";
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Los descuentos no se borran: se rechazan.");
	}

	public Alumno getAlumno() {
		return alumno;
	}

	public AnioEscolar getAnioEscolar() {
		return anioEscolar;
	}

	public TipoDescuento getTipo() {
		return tipo;
	}

	public ModalidadDescuento getModalidad() {
		return modalidad;
	}

	public BigDecimal getValor() {
		return valor;
	}

	public BigDecimal getTotalEstimado() {
		return totalEstimado;
	}

	public String getMotivo() {
		return motivo;
	}

	public String getSustento() {
		return sustento;
	}

	public EstadoDescuento getEstado() {
		return estado;
	}

	public String getResueltoPor() {
		return resueltoPor;
	}

	public LocalDateTime getResueltoEn() {
		return resueltoEn;
	}
}
