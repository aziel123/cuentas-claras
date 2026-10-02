package pe.edu.virgenmaria.cuentasclaras.cobranza.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Motivo;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Lote de saldo inicial (D7): las deudas previas al sistema, validadas por el contador.
 * <ul>
 *   <li>Administración lo arma (BORRADOR) con la referencia al informe del contador y el <b>total declarado</b>;</li>
 *   <li>se envía solo si la suma de las líneas no quitadas es exactamente igual al total declarado;</li>
 *   <li>lo confirma Promotoría o Dirección, alguien distinto de quien lo creó, lo envió o le agregó líneas (para el
 *       creador y quien lo envió también es CHECK en la base). Al confirmarse se crean las cuotas;</li>
 *   <li>enviado no admite cambios: se devuelve con motivo y vuelve a BORRADOR.</li>
 * </ul>
 */
@Entity
@Table(name = "lote_saldo_inicial")
public class LoteSaldoInicial extends BaseEntity {

	public static final int MAX_DOCUMENTO = 150;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "anio_escolar_id", nullable = false, updatable = false)
	private AnioEscolar anioEscolar;

	@Column(name = "fecha_corte", nullable = false, updatable = false)
	private LocalDate fechaCorte;

	@Column(name = "documento_referencia", nullable = false, updatable = false, length = MAX_DOCUMENTO)
	private String documentoReferencia;

	@Column(name = "total_declarado", nullable = false, updatable = false, precision = 10, scale = 2)
	private BigDecimal totalDeclarado;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoLote estado;

	@Column(name = "enviado_por", length = 60)
	private String enviadoPor;

	@Column(name = "enviado_en")
	private LocalDateTime enviadoEn;

	@Column(name = "confirmado_por", length = 60)
	private String confirmadoPor;

	@Column(name = "confirmado_en")
	private LocalDateTime confirmadoEn;

	@Column(name = "devuelto_por", length = 60)
	private String devueltoPor;

	@Column(name = "devuelto_en")
	private LocalDateTime devueltoEn;

	@Column(name = "motivo_devolucion", length = 500)
	private String motivoDevolucion;

	@Column(name = "descartado_por", length = 60)
	private String descartadoPor;

	@Column(name = "descartado_en")
	private LocalDateTime descartadoEn;

	@Column(name = "motivo_descarte", length = 500)
	private String motivoDescarte;

	@OneToMany(mappedBy = "lote", cascade = CascadeType.PERSIST)
	@OrderBy("id")
	private List<LineaSaldoInicial> lineas = new ArrayList<>();

	protected LoteSaldoInicial() {
		// requerido por JPA
	}

	/**
	 * @param hoy fecha de Lima: el corte no puede ser futuro
	 */
	public static LoteSaldoInicial nuevo(AnioEscolar anio, LocalDate fechaCorte, String documento,
			BigDecimal totalDeclarado, LocalDate hoy) {
		Objects.requireNonNull(anio, "anio");
		if (fechaCorte == null || fechaCorte.isAfter(hoy)) {
			throw new ReglaNegocioException("La fecha de corte no puede ser futura.");
		}
		String referencia = Normalizador.limpiar(documento);
		if (referencia == null || referencia.length() > MAX_DOCUMENTO) {
			throw new ReglaNegocioException("Escribe la referencia al informe del contador (hasta " + MAX_DOCUMENTO
					+ " caracteres).");
		}
		LoteSaldoInicial lote = new LoteSaldoInicial();
		lote.anioEscolar = anio;
		lote.fechaCorte = fechaCorte;
		lote.documentoReferencia = referencia;
		lote.totalDeclarado = Dinero.enRango(totalDeclarado, new BigDecimal("0.01"), Dinero.MAXIMO_TOTAL,
				"el total declarado");
		lote.estado = EstadoLote.BORRADOR;
		return lote;
	}

	/**
	 * Agrega una deuda. Una PENSION del mes m vence por defecto el último día del mes y se describe
	 * «Pensión {mes} {año}». El vencimiento debe estar dentro del año del lote o del anterior.
	 */
	public LineaSaldoInicial agregarLinea(Alumno alumno, ConceptoSaldo concepto, Integer mes, String descripcion,
			BigDecimal monto, LocalDate vencimiento) {
		exigirBorrador();
		Objects.requireNonNull(concepto, "concepto");
		int anio = anioEscolar.getAnio();
		Integer mesLinea = null;
		if (concepto == ConceptoSaldo.PENSION) {
			if (mes == null || mes < 1 || mes > 12) {
				throw new ReglaNegocioException("Indica el mes de la pensión (1 a 12).");
			}
			mesLinea = mes;
		}
		String texto = Normalizador.limpiar(descripcion);
		if (texto == null) {
			texto = switch (concepto) {
				case PENSION -> CalculadoraCronograma.descripcionPension(anio, mesLinea);
				case MATRICULA -> "Matrícula " + anio;
				case OTRO -> throw new ReglaNegocioException("Describe la deuda (por ejemplo «Taller de verano 2026»).");
			};
		}
		if (texto.length() > 80) {
			throw new ReglaNegocioException("La descripción admite hasta 80 caracteres.");
		}
		LocalDate vence = vencimiento;
		if (vence == null && concepto == ConceptoSaldo.PENSION) {
			vence = Calendario.ultimoDiaDelMes(anio, mesLinea);
		}
		if (vence == null) {
			throw new ReglaNegocioException("Indica el vencimiento de la deuda.");
		}
		if (vence.getYear() != anio && vence.getYear() != anio - 1) {
			throw new ReglaNegocioException("El vencimiento debe estar en " + (anio - 1) + " o " + anio + ".");
		}
		LineaSaldoInicial linea = LineaSaldoInicial.nueva(this, alumno, concepto, mesLinea, texto,
				Dinero.positivo(monto, "el monto de la deuda"), vence);
		String obligacion = linea.obligacion();
		if (obligacion != null && lineasVigentes().stream()
				.anyMatch(l -> l.getAlumno().getId().equals(alumno.getId()) && obligacion.equals(l.obligacion()))) {
			throw new ReglaNegocioException("Este alumno ya tiene en el lote la deuda «" + linea.getDescripcion() + "».");
		}
		lineas.add(linea);
		return linea;
	}

	public LineaSaldoInicial quitarLinea(Long lineaId) {
		exigirBorrador();
		LineaSaldoInicial linea = lineas.stream().filter(l -> l.getId().equals(lineaId)).findFirst()
				.orElseThrow(() -> new RecursoNoEncontradoException("Línea no encontrada"));
		if (linea.isQuitada()) {
			throw new ReglaNegocioException("La línea ya estaba quitada.");
		}
		linea.quitar();
		return linea;
	}

	public List<LineaSaldoInicial> lineasVigentes() {
		return lineas.stream().filter(l -> !l.isQuitada()).toList();
	}

	/** Suma exacta (BigDecimal) de las líneas no quitadas. */
	public BigDecimal totalLineas() {
		return Dinero.sumar(lineasVigentes().stream().map(LineaSaldoInicial::getMonto).toList());
	}

	public boolean cuadra() {
		return totalLineas().compareTo(totalDeclarado) == 0;
	}

	/** Quien lo creó, lo envió o le agregó líneas (incluidas las quitadas): ninguno puede confirmarlo. */
	public Set<String> participantes() {
		Set<String> personas = new LinkedHashSet<>();
		personas.add(getCreadoPor());
		if (enviadoPor != null) {
			personas.add(enviadoPor);
		}
		lineas.forEach(l -> personas.add(l.getCreadoPor()));
		personas.remove(null);
		return Collections.unmodifiableSet(personas);
	}

	public void enviar(String por, LocalDateTime ahora) {
		exigirBorrador();
		if (lineasVigentes().isEmpty()) {
			throw new ReglaNegocioException("El lote no tiene líneas: agrega las deudas antes de enviarlo.");
		}
		if (!cuadra()) {
			throw new ReglaNegocioException("No cuadra: la suma de las líneas es " + Dinero.formatear(totalLineas())
					+ " y el total declarado es " + Dinero.formatear(totalDeclarado)
					+ ". Revisa las líneas contra el informe del contador.");
		}
		estado = EstadoLote.ENVIADO;
		enviadoPor = Objects.requireNonNull(por, "por");
		enviadoEn = Objects.requireNonNull(ahora, "ahora");
	}

	public void confirmar(String por, LocalDateTime ahora) {
		if (estado != EstadoLote.ENVIADO) {
			throw new ReglaNegocioException("Solo se confirma un lote enviado (está " + estado.etiqueta().toLowerCase()
					+ ").");
		}
		if (participantes().contains(por)) {
			throw new AutoaprobacionException("No puedes confirmar un lote que tú creaste, enviaste o al que le "
					+ "agregaste líneas: debe confirmarlo otra persona de Promotoría o Dirección.");
		}
		estado = EstadoLote.CONFIRMADO;
		confirmadoPor = por;
		confirmadoEn = Objects.requireNonNull(ahora, "ahora");
	}

	public void devolver(String por, String motivo, LocalDateTime ahora) {
		if (estado != EstadoLote.ENVIADO) {
			throw new ReglaNegocioException("Solo se devuelve un lote enviado.");
		}
		motivoDevolucion = Motivo.exigir(motivo);
		estado = EstadoLote.BORRADOR;
		devueltoPor = por;
		devueltoEn = ahora;
	}

	public void descartar(String por, String motivo, LocalDateTime ahora) {
		exigirBorrador();
		motivoDescarte = Motivo.exigir(motivo);
		estado = EstadoLote.DESCARTADO;
		descartadoPor = por;
		descartadoEn = ahora;
	}

	private void exigirBorrador() {
		if (estado != EstadoLote.BORRADOR) {
			throw new ReglaNegocioException("El lote está " + estado.etiqueta().toLowerCase()
					+ ": no admite cambios. Si hay que corregirlo, Promotoría o Dirección deben devolverlo.");
		}
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Los lotes de saldo inicial no se borran: se descartan con motivo.");
	}

	public AnioEscolar getAnioEscolar() {
		return anioEscolar;
	}

	public LocalDate getFechaCorte() {
		return fechaCorte;
	}

	public String getDocumentoReferencia() {
		return documentoReferencia;
	}

	public BigDecimal getTotalDeclarado() {
		return totalDeclarado;
	}

	public EstadoLote getEstado() {
		return estado;
	}

	public String getEnviadoPor() {
		return enviadoPor;
	}

	public LocalDateTime getEnviadoEn() {
		return enviadoEn;
	}

	public String getConfirmadoPor() {
		return confirmadoPor;
	}

	public LocalDateTime getConfirmadoEn() {
		return confirmadoEn;
	}

	public String getDevueltoPor() {
		return devueltoPor;
	}

	public LocalDateTime getDevueltoEn() {
		return devueltoEn;
	}

	public String getMotivoDevolucion() {
		return motivoDevolucion;
	}

	public String getDescartadoPor() {
		return descartadoPor;
	}

	public String getMotivoDescarte() {
		return motivoDescarte;
	}

	public List<LineaSaldoInicial> getLineas() {
		return Collections.unmodifiableList(lineas);
	}
}
