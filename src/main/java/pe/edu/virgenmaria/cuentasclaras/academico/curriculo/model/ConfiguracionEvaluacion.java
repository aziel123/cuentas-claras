package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador;

import java.util.Objects;

/**
 * Sprint 10, tanda 1 (sección 3.3; S1, S3, S9 y S11; H6): cómo evalúa un colegio un año. La guarda SOLO Dirección, con la
 * firma de su sesión ({@code configuracion_evaluacion:{anio}:GUARDADA:{version}}; trg_configuracion_evaluacion_nace y
 * _cambio), y queda en la bitácora con el antes y el después. Desde que abre un periodo del año, la versión del currículo,
 * el tipo de periodo y los días de cierre anticipado no cambian (E11 y M7). El colegio y el año no cambian
 * ({@code updatable = false} y GRANT por columna: 1143).
 * <p>
 * No tiene ningún campo de deuda, pago ni bloqueo (Ley 29571: las evaluaciones no se condicionan al pago; lo revisa
 * {@code ConfiguracionSinDeudaTest}). Nunca se borra.
 */
@Entity
@Table(name = "configuracion_evaluacion")
public class ConfiguracionEvaluacion extends BaseEntity {

	public static final int CONCLUSION_MIN = 100;

	public static final int CONCLUSION_MAX = 1000;

	public static final int CONCLUSION_POR_DEFECTO = 500;

	public static final int DIAS_CIERRE_MAX = 15;

	public static final int DIAS_CIERRE_POR_DEFECTO = 7;

	public static final String LENGUA_POR_DEFECTO = "Castellano";

	@Column(name = "anio_escolar_id", nullable = false, updatable = false)
	private Long anioEscolarId;

	@Column(name = "curriculo_version_id", nullable = false)
	private Long curriculoVersionId;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo_periodo", nullable = false, length = 10)
	private TipoPeriodo tipoPeriodo;

	@Column(name = "numero_periodos", nullable = false)
	private Integer numeroPeriodos;

	@Column(name = "conclusion_max", nullable = false)
	private Integer conclusionMax;

	@Column(name = "conclusion_siempre", nullable = false)
	private Boolean conclusionSiempre;

	@Column(name = "dias_cierre_anticipado", nullable = false)
	private Integer diasCierreAnticipado;

	@Column(name = "lengua_materna", nullable = false, length = 30)
	private String lenguaMaterna;

	@Column(length = 80)
	private String dre;

	@Column(length = 80)
	private String ugel;

	@Column(name = "actualizado_por", nullable = false, length = 60)
	private String actualizadoPor;

	protected ConfiguracionEvaluacion() {
		// requerido por JPA
	}

	/** La primera vez que Dirección guarda la evaluación del año. */
	public static ConfiguracionEvaluacion nueva(Long anioEscolarId, Valores valores, String director) {
		ConfiguracionEvaluacion c = new ConfiguracionEvaluacion();
		c.anioEscolarId = Objects.requireNonNull(anioEscolarId, "anioEscolarId");
		c.aplicar(valores, director);
		return c;
	}

	/** Dirección cambia la configuración (lo fijo con un periodo abierto lo revisa el servicio y la base). */
	public void cambiar(Valores valores, String director) {
		aplicar(valores, director);
	}

	private void aplicar(Valores v, String director) {
		Valores limpios = Objects.requireNonNull(v, "valores").validados();
		curriculoVersionId = limpios.curriculoVersionId();
		tipoPeriodo = limpios.tipoPeriodo();
		numeroPeriodos = limpios.tipoPeriodo().periodos();
		conclusionMax = limpios.conclusionMax();
		conclusionSiempre = limpios.conclusionSiempre();
		diasCierreAnticipado = limpios.diasCierreAnticipado();
		lenguaMaterna = limpios.lenguaMaterna();
		dre = limpios.dre();
		ugel = limpios.ugel();
		actualizadoPor = Objects.requireNonNull(director, "director");
	}

	/** La versión que tendrá la fila al guardarla (0 al crearla): va en la clave de la firma. */
	public long versionAlGuardar() {
		return getId() == null || getVersion() == null ? 0 : getVersion() + 1;
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("La configuración de evaluación no se borra: se cambia con la firma de Dirección.");
	}

	public Valores valores() {
		return new Valores(curriculoVersionId, tipoPeriodo, conclusionMax, Boolean.TRUE.equals(conclusionSiempre),
				diasCierreAnticipado, lenguaMaterna, dre, ugel);
	}

	/**
	 * Lo que elige Dirección, con los rangos de la base. {@code dre} y {@code ugel} son opcionales (la cabecera de la
	 * libreta, tanda 3).
	 */
	public record Valores(Long curriculoVersionId, TipoPeriodo tipoPeriodo, int conclusionMax, boolean conclusionSiempre,
			int diasCierreAnticipado, String lenguaMaterna, String dre, String ugel) {

		/** Los valores con el texto limpio, o {@link ReglaNegocioException} con un mensaje claro. */
		public Valores validados() {
			if (curriculoVersionId == null) {
				throw new ReglaNegocioException("Elige la versión del currículo.");
			}
			if (tipoPeriodo == null) {
				throw new ReglaNegocioException("Elige cómo se divide el año: bimestres, trimestres o semestres.");
			}
			if (conclusionMax < CONCLUSION_MIN || conclusionMax > CONCLUSION_MAX) {
				throw new ReglaNegocioException("La conclusión descriptiva puede tener de " + CONCLUSION_MIN + " a "
						+ CONCLUSION_MAX + " caracteres.");
			}
			if (diasCierreAnticipado < 0 || diasCierreAnticipado > DIAS_CIERRE_MAX) {
				throw new ReglaNegocioException("El cierre anticipado va de 0 a " + DIAS_CIERRE_MAX + " días antes del fin.");
			}
			String lengua = Normalizador.limpiar(lenguaMaterna);
			if (lengua == null || lengua.length() < 3 || lengua.length() > 30) {
				throw new ReglaNegocioException("Escribe la lengua materna de los estudiantes (de 3 a 30 caracteres).");
			}
			return new Valores(curriculoVersionId, tipoPeriodo, conclusionMax, conclusionSiempre, diasCierreAnticipado,
					lengua, opcional(dre, "La DRE"), opcional(ugel, "La UGEL"));
		}

		private static String opcional(String texto, String campo) {
			String limpio = Normalizador.limpiar(texto);
			if (limpio != null && (limpio.length() < 3 || limpio.length() > 80)) {
				throw new ReglaNegocioException(campo + " debe tener de 3 a 80 caracteres (o déjala vacía).");
			}
			return limpio;
		}
	}

	public Long getAnioEscolarId() {
		return anioEscolarId;
	}

	public Long getCurriculoVersionId() {
		return curriculoVersionId;
	}

	public TipoPeriodo getTipoPeriodo() {
		return tipoPeriodo;
	}

	public int getNumeroPeriodos() {
		return numeroPeriodos;
	}

	public int getConclusionMax() {
		return conclusionMax;
	}

	public boolean isConclusionSiempre() {
		return Boolean.TRUE.equals(conclusionSiempre);
	}

	public int getDiasCierreAnticipado() {
		return diasCierreAnticipado;
	}

	public String getLenguaMaterna() {
		return lenguaMaterna;
	}

	public String getDre() {
		return dre;
	}

	public String getUgel() {
		return ugel;
	}

	public String getActualizadoPor() {
		return actualizadoPor;
	}
}
