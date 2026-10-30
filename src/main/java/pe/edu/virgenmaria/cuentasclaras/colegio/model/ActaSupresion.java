package pe.edu.virgenmaria.cuentasclaras.colegio.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Sprint 11, tanda 2 (sección 3.11; AP-14, AP2-5, AP2-8, AP2-10 y AP2-12): el acta de supresión de un colegio (una por
 * colegio). La PROPONE un operador y la APRUEBA otro; {@code sistema.auditoria} anota la verificación completa de la cadena;
 * el DBA la ejecuta con {@code scripts/fin-de-colegio/suprimir.sql} (EJECUTANDO y EJECUTADA no las escribe la aplicación).
 * La aplicación solo lee las columnas que pone la base y el DBA.
 */
@Entity
@Table(name = "acta_supresion")
@DynamicUpdate
public class ActaSupresion extends BaseEntity {

	public enum Tipo {
		BAJA, CIERRE
	}

	public enum Estado {
		PROPUESTA, APROBADA, EJECUTANDO, EJECUTADA
	}

	/** AP2-12: los respaldos con datos del colegio vencen a más tardar a los 24 meses del inicio del plazo. */
	public static final int MESES_RESPALDOS = 24;

	@Column(name = "plataforma_id", nullable = false, updatable = false)
	private Long plataformaId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 8)
	private Tipo tipo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Estado estado = Estado.PROPUESTA;

	@Column(nullable = false, updatable = false, length = 500)
	private String motivo;

	@Column(name = "propuesto_por", nullable = false, updatable = false, length = 60)
	private String propuestoPor;

	@Column(name = "operador_id", nullable = false, updatable = false)
	private Long operadorId;

	@Column(name = "aprobado_por", length = 60)
	private String aprobadoPor;

	@Column(name = "aprobador_id")
	private Long aprobadorId;

	@Column(name = "aprobado_en")
	private LocalDateTime aprobadoEn;

	/** El aplicado_en de la SUSPENSION o la BAJA: en MySQL lo pone la base; en H2, la aplicación. */
	@Column(name = "inicio_plazo", nullable = false, updatable = false)
	private LocalDateTime inicioPlazo;

	@Column(name = "respaldos_vencen_el", nullable = false, updatable = false)
	private LocalDate respaldosVencenEl;

	@Column(name = "cadena_verificada_en")
	private LocalDateTime cadenaVerificadaEn;

	@Column(name = "ejecutado_por", insertable = false, updatable = false, length = 60)
	private String ejecutadoPor;

	@Column(name = "conexion_id", insertable = false, updatable = false)
	private Long conexionId;

	@Column(name = "ejecutando_desde", insertable = false, updatable = false)
	private LocalDateTime ejecutandoDesde;

	@Column(name = "ejecutando_vence_en", insertable = false, updatable = false)
	private LocalDateTime ejecutandoVenceEn;

	@Column(name = "huella_eventos", insertable = false, updatable = false, length = 64)
	private String huellaEventos;

	@Column(name = "ultima_secuencia", insertable = false, updatable = false)
	private Long ultimaSecuencia;

	@Column(name = "filas_suprimidas", insertable = false, updatable = false, length = 8000)
	private String filasSuprimidas;

	@Column(name = "eventos_tachados", insertable = false, updatable = false)
	private Integer eventosTachados;

	@Column(name = "ejecutada_en", insertable = false, updatable = false)
	private LocalDateTime ejecutadaEn;

	protected ActaSupresion() {
		// requerido por JPA
	}

	public static ActaSupresion propuesta(Tipo tipo, String motivo, Long plataformaId, Long operadorId, String operador,
			LocalDateTime inicioPlazo, LocalDate respaldosVencenEl) {
		String texto = motivo == null ? "" : motivo.strip();
		if (texto.length() < 20 || texto.length() > 500) {
			throw new ReglaNegocioException("Escribe el motivo del acta (de 20 a 500 caracteres).");
		}
		Objects.requireNonNull(inicioPlazo, "inicioPlazo");
		if (respaldosVencenEl == null || respaldosVencenEl.isAfter(inicioPlazo.toLocalDate().plusMonths(MESES_RESPALDOS))) {
			throw new ReglaNegocioException("Los respaldos con datos del colegio no pueden vencer después de "
					+ MESES_RESPALDOS + " meses desde el inicio del plazo (AP2-12).");
		}
		ActaSupresion a = new ActaSupresion();
		a.tipo = Objects.requireNonNull(tipo, "tipo");
		a.motivo = texto;
		a.plataformaId = Objects.requireNonNull(plataformaId, "plataformaId");
		a.operadorId = Objects.requireNonNull(operadorId, "operadorId");
		a.propuestoPor = Objects.requireNonNull(operador, "operador");
		a.inicioPlazo = inicioPlazo;
		a.respaldosVencenEl = respaldosVencenEl;
		return a;
	}

	public void aprobar(String operador, Long aprobadorId, LocalDateTime ahora) {
		if (estado != Estado.PROPUESTA) {
			throw new ReglaNegocioException("El acta ya no está por aprobar.");
		}
		if (Objects.equals(operador, propuestoPor)) {
			throw new ReglaNegocioException("Tú propusiste esta acta: la aprueba otro operador, con su propia sesión.");
		}
		this.estado = Estado.APROBADA;
		this.aprobadoPor = operador;
		this.aprobadorId = Objects.requireNonNull(aprobadorId, "aprobadorId");
		this.aprobadoEn = ahora;
	}

	/** AP2-8: sistema.auditoria corrió la verificación completa de la cadena y salió íntegra. */
	public void cadenaVerificada(LocalDateTime ahora) {
		if (estado != Estado.PROPUESTA && estado != Estado.APROBADA) {
			throw new ReglaNegocioException("El acta ya se está ejecutando.");
		}
		this.cadenaVerificadaEn = ahora;
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Las actas de supresión no se borran.");
	}

	public Long getPlataformaId() {
		return plataformaId;
	}

	public Tipo getTipo() {
		return tipo;
	}

	public Estado getEstado() {
		return estado;
	}

	public String getMotivo() {
		return motivo;
	}

	public String getPropuestoPor() {
		return propuestoPor;
	}

	public String getAprobadoPor() {
		return aprobadoPor;
	}

	public LocalDateTime getAprobadoEn() {
		return aprobadoEn;
	}

	public LocalDateTime getInicioPlazo() {
		return inicioPlazo;
	}

	public LocalDate getRespaldosVencenEl() {
		return respaldosVencenEl;
	}

	public LocalDateTime getCadenaVerificadaEn() {
		return cadenaVerificadaEn;
	}

	public String getEjecutadoPor() {
		return ejecutadoPor;
	}

	public String getHuellaEventos() {
		return huellaEventos;
	}

	public Long getUltimaSecuencia() {
		return ultimaSecuencia;
	}

	public String getFilasSuprimidas() {
		return filasSuprimidas;
	}

	public Integer getEventosTachados() {
		return eventosTachados;
	}

	public LocalDateTime getEjecutadaEn() {
		return ejecutadaEn;
	}
}
