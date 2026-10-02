package pe.edu.virgenmaria.cuentasclaras.cobranza.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Motivo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Plan de pensiones de un nivel en un año, versionado:
 * <ul>
 *   <li>BORRADOR: lo arma y lo edita Administración;</li>
 *   <li>APROBADO: lo aprueba Promotoría o Dirección, alguien distinto de quien lo creó y de quien lo editó por
 *       última vez (también es CHECK en la base). Desde ahí es inmutable;</li>
 *   <li>REEMPLAZADO: cuando se aprueba una versión nueva. Las cuotas ya generadas no cambian.</li>
 * </ul>
 * Sin setters: cada cambio es un método con su regla.
 */
@Entity
@Table(name = "plan_pension")
public class PlanPension extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "anio_escolar_id", nullable = false, updatable = false)
	private AnioEscolar anioEscolar;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private Nivel nivel;

	@Column(name = "numero_version", nullable = false, updatable = false)
	private int numeroVersion;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoPlan estado;

	@Column
	private Boolean vigente;

	@Column(name = "monto_matricula", nullable = false, precision = 10, scale = 2)
	private BigDecimal montoMatricula;

	@Column(name = "vencimiento_matricula", nullable = false)
	private LocalDate vencimientoMatricula;

	@Column(name = "monto_pension", nullable = false, precision = 10, scale = 2)
	private BigDecimal montoPension;

	@Convert(converter = ListaFechasConverter.class)
	@Column(name = "vencimientos_pension", nullable = false, length = 140)
	private List<LocalDate> vencimientos;

	@Column(name = "cobro_desde")
	private LocalDate cobroDesde;

	@Column(name = "motivo_cambio", length = 500, updatable = false)
	private String motivoCambio;

	@Column(name = "editado_por", nullable = false, length = 60)
	private String editadoPor;

	/** Todos los que editaron alguna vez: «,usuario1,usuario2,». Ninguno puede aprobar (también CHECK en la base). */
	@Column(nullable = false, length = 1000)
	private String editores;

	@Column(name = "enviado_por", length = 60)
	private String enviadoPor;

	@Column(name = "enviado_en")
	private LocalDateTime enviadoEn;

	@Column(name = "devuelto_por", length = 60)
	private String devueltoPor;

	@Column(name = "devuelto_en")
	private LocalDateTime devueltoEn;

	@Column(name = "motivo_devolucion", length = 500)
	private String motivoDevolucion;

	@Column(name = "aprobado_por", length = 60)
	private String aprobadoPor;

	@Column(name = "aprobado_en")
	private LocalDateTime aprobadoEn;

	@Column(name = "cerrado_por", length = 60)
	private String cerradoPor;

	@Column(name = "cerrado_en")
	private LocalDateTime cerradoEn;

	protected PlanPension() {
		// requerido por JPA
	}

	/** Primera versión de un plan. La configuración ya debe venir validada contra el año. */
	public static PlanPension borrador(AnioEscolar anio, Nivel nivel, ConfiguracionPlan configuracion, String editor) {
		PlanPension plan = new PlanPension();
		plan.anioEscolar = Objects.requireNonNull(anio, "anio");
		plan.nivel = Objects.requireNonNull(nivel, "nivel");
		plan.numeroVersion = 1;
		plan.estado = EstadoPlan.BORRADOR;
		plan.aplicar(configuracion);
		plan.editadoPor = Objects.requireNonNull(editor, "editor");
		plan.editores = "," + editor + ",";
		return plan;
	}

	/**
	 * Propuesta nueva cuando las versiones anteriores del nivel se descartaron (nunca hubo una aprobada). Desde la
	 * versión 2 el motivo es obligatorio (también en la base).
	 */
	public static PlanPension borrador(AnioEscolar anio, Nivel nivel, int numero, String motivo,
			ConfiguracionPlan configuracion, String editor) {
		PlanPension plan = borrador(anio, nivel, configuracion, editor);
		if (numero < 1) {
			throw new IllegalArgumentException("La versión empieza en 1");
		}
		plan.numeroVersion = numero;
		plan.motivoCambio = numero == 1 ? null : Motivo.exigir(motivo);
		return plan;
	}

	/**
	 * «Cambiar montos» de un plan aprobado: crea un borrador nuevo con los mismos valores, que luego se edita y lo
	 * aprueba otra persona. Este plan sigue vigente hasta entonces.
	 */
	public PlanPension nuevaVersion(int numero, String motivo, String editor) {
		if (estado != EstadoPlan.APROBADO) {
			throw new ReglaNegocioException("Solo se crea una versión nueva a partir del plan aprobado y vigente.");
		}
		if (numero <= numeroVersion) {
			throw new IllegalArgumentException("La versión nueva debe ser mayor que " + numeroVersion);
		}
		PlanPension nueva = borrador(anioEscolar, nivel, configuracion(), editor);
		nueva.numeroVersion = numero;
		nueva.motivoCambio = Motivo.exigir(motivo);
		return nueva;
	}

	/** Solo en BORRADOR: un plan aprobado no cambia. */
	public void editar(ConfiguracionPlan configuracion, String editor) {
		exigirBorrador("editar");
		aplicar(configuracion);
		editadoPor = Objects.requireNonNull(editor, "editor");
		if (!editores.contains("," + editor + ",")) {
			editores = editores + editor + ",";
		}
	}

	/** Quien lo creó, todos los que lo editaron alguna vez y quien lo envió: ninguno puede aprobarlo. */
	public Set<String> participantes() {
		Set<String> personas = new LinkedHashSet<>();
		if (getCreadoPor() != null) {
			personas.add(getCreadoPor());
		}
		Arrays.stream(editores.split(",")).filter(e -> !e.isBlank()).forEach(personas::add);
		if (enviadoPor != null) {
			personas.add(enviadoPor);
		}
		return Collections.unmodifiableSet(personas);
	}

	/** {@code true} si el usuario participó en el plan (lo creó, lo editó alguna vez o lo envió): no puede aprobarlo. */
	public boolean esAutor(String usuario) {
		return participantes().contains(usuario);
	}

	/** Lo bloquea para que otra persona lo revise: desde aquí nadie lo edita. */
	public void enviar(String por, LocalDateTime ahora) {
		exigirBorrador("enviar");
		estado = EstadoPlan.ENVIADO;
		enviadoPor = Objects.requireNonNull(por, "por");
		enviadoEn = Objects.requireNonNull(ahora, "ahora");
	}

	/** Quien revisa lo devuelve a Administración con un motivo: vuelve a BORRADOR. */
	public void devolver(String por, String motivo, LocalDateTime ahora) {
		exigirEnviado("devolver");
		motivoDevolucion = Motivo.exigir(motivo);
		estado = EstadoPlan.BORRADOR;
		enviadoPor = null;
		enviadoEn = null;
		devueltoPor = por;
		devueltoEn = ahora;
	}

	public void aprobar(String aprobador, LocalDateTime ahora) {
		exigirEnviado("aprobar");
		if (esAutor(aprobador)) {
			throw new AutoaprobacionException("No puedes aprobar un plan que tú creaste o editaste: debe aprobarlo "
					+ "otra persona de Promotoría o Dirección.");
		}
		estado = EstadoPlan.APROBADO;
		vigente = Boolean.TRUE;
		aprobadoPor = aprobador;
		aprobadoEn = Objects.requireNonNull(ahora, "ahora");
	}

	/** La versión aprobada deja de estar vigente porque se aprobó otra. */
	public void reemplazar(String por, LocalDateTime ahora) {
		if (estado != EstadoPlan.APROBADO) {
			throw new ReglaNegocioException("Solo se reemplaza un plan aprobado.");
		}
		estado = EstadoPlan.REEMPLAZADO;
		vigente = null;
		cerradoPor = por;
		cerradoEn = ahora;
	}

	public void descartar(String por, LocalDateTime ahora) {
		exigirBorrador("descartar");
		estado = EstadoPlan.DESCARTADO;
		cerradoPor = por;
		cerradoEn = ahora;
	}

	public ConfiguracionPlan configuracion() {
		return new ConfiguracionPlan(montoMatricula, vencimientoMatricula, montoPension, vencimientos, cobroDesde);
	}

	/** «Plan Primaria 2027 v1». */
	public String nombre() {
		return "Plan " + nivel.etiqueta() + " " + anioEscolar.getAnio() + " v" + numeroVersion;
	}

	public boolean aprobado() {
		return estado == EstadoPlan.APROBADO;
	}

	public boolean borrador() {
		return estado == EstadoPlan.BORRADOR;
	}

	public boolean enviado() {
		return estado == EstadoPlan.ENVIADO;
	}

	/** En preparación o por aprobar: todavía no rige. */
	public boolean pendiente() {
		return estado == EstadoPlan.BORRADOR || estado == EstadoPlan.ENVIADO;
	}

	private void aplicar(ConfiguracionPlan configuracion) {
		Objects.requireNonNull(configuracion, "configuracion");
		montoMatricula = configuracion.montoMatricula();
		vencimientoMatricula = configuracion.vencimientoMatricula();
		montoPension = configuracion.montoPension();
		vencimientos = List.copyOf(configuracion.vencimientos());
		cobroDesde = configuracion.cobroDesde();
	}

	private void exigirEnviado(String accion) {
		if (estado != EstadoPlan.ENVIADO) {
			throw new ReglaNegocioException("Solo se puede " + accion + " un plan enviado (este está "
					+ estado.etiqueta().toLowerCase() + ").");
		}
	}

	private void exigirBorrador(String accion) {
		if (estado != EstadoPlan.BORRADOR) {
			throw new ReglaNegocioException("El plan está " + estado.etiqueta().toLowerCase()
					+ ": no se puede " + accion + ". Para cambiar montos de un plan aprobado usa «Cambiar montos», "
					+ "que crea una versión nueva.");
		}
	}

	public AnioEscolar getAnioEscolar() {
		return anioEscolar;
	}

	public Nivel getNivel() {
		return nivel;
	}

	public int getNumeroVersion() {
		return numeroVersion;
	}

	public EstadoPlan getEstado() {
		return estado;
	}

	public BigDecimal getMontoMatricula() {
		return montoMatricula;
	}

	public LocalDate getVencimientoMatricula() {
		return vencimientoMatricula;
	}

	public BigDecimal getMontoPension() {
		return montoPension;
	}

	public List<LocalDate> getVencimientos() {
		return vencimientos;
	}

	public LocalDate getCobroDesde() {
		return cobroDesde;
	}

	public String getMotivoCambio() {
		return motivoCambio;
	}

	public String getEditadoPor() {
		return editadoPor;
	}

	public String getEditores() {
		return editores;
	}

	public String getEnviadoPor() {
		return enviadoPor;
	}

	public LocalDateTime getEnviadoEn() {
		return enviadoEn;
	}

	public String getDevueltoPor() {
		return devueltoPor;
	}

	public String getMotivoDevolucion() {
		return motivoDevolucion;
	}

	public String getAprobadoPor() {
		return aprobadoPor;
	}

	public LocalDateTime getAprobadoEn() {
		return aprobadoEn;
	}

	public String getCerradoPor() {
		return cerradoPor;
	}

	public LocalDateTime getCerradoEn() {
		return cerradoEn;
	}
}
