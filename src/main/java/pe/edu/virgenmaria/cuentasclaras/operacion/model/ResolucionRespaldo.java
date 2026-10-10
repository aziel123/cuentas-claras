package pe.edu.virgenmaria.cuentasclaras.operacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Motivo;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Fila de {@code resolucion_respaldo} (V27, correcciones del sprint 7, QA-S7-1): Promotoría resolvió con motivo la alerta
 * «Faltan filas» del último respaldo con FALTAN_FILAS (y de los anteriores). Hasta entonces la alerta sigue en el panel,
 * en «Para revisar» y en el vigilante, y cada respaldo nuevo dice FALTAN_FILAS ({@code trg_respaldo_registro}). Como
 * {@link Respaldo}, es técnica y de toda la base: guarda el colegio y la persona que resolvió (FK compuesta), pero no la
 * filtra {@code @TenantId}, porque la alerta es de la base y la leen también el vigilante y las alertas técnicas sin
 * sesión. De solo inserción (GRANT y {@code trg_resolucion_respaldo_registro}, que exige Promotoría y su firma).
 */
@Entity
@Table(name = "resolucion_respaldo")
public class ResolucionRespaldo {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "respaldo_id", nullable = false, updatable = false)
	private Long respaldoId;

	@Column(name = "colegio_id", nullable = false, updatable = false)
	private Long colegioId;

	@Column(name = "usuario_id", nullable = false, updatable = false)
	private Long usuarioId;

	@Column(nullable = false, updatable = false, length = 500)
	private String motivo;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private LocalDateTime creadoEn;

	@Column(name = "creado_por", nullable = false, updatable = false, length = 60)
	private String creadoPor;

	protected ResolucionRespaldo() {
		// requerido por JPA
	}

	/** La resolución de una persona (nunca de un proceso ni de cc_respaldo: lo exigen el CHECK y el trigger). */
	public static ResolucionRespaldo de(Respaldo respaldo, Long colegioId, Long usuarioId, String nombreUsuario,
			String motivo, LocalDateTime ahora) {
		if (respaldo.getComparacion() != ComparacionRespaldo.FALTAN_FILAS) {
			throw new IllegalStateException("Solo se resuelve un respaldo con FALTAN_FILAS");
		}
		ResolucionRespaldo r = new ResolucionRespaldo();
		r.respaldoId = respaldo.getId();
		r.colegioId = Objects.requireNonNull(colegioId, "colegioId");
		r.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId");
		r.creadoPor = Objects.requireNonNull(nombreUsuario, "nombreUsuario");
		if (r.creadoPor.startsWith("sistema") || "cc_respaldo".equals(r.creadoPor)) {
			throw new IllegalStateException("La alerta la resuelve una persona");
		}
		r.motivo = Motivo.exigir(motivo);
		r.creadoEn = Objects.requireNonNull(ahora, "ahora");
		return r;
	}

	@PreUpdate
	@PreRemove
	void impedirCambios() {
		throw new IllegalStateException("La resolución de un respaldo no se edita ni se borra.");
	}

	public Long getId() {
		return id;
	}

	public Long getRespaldoId() {
		return respaldoId;
	}

	public Long getColegioId() {
		return colegioId;
	}

	public Long getUsuarioId() {
		return usuarioId;
	}

	public String getMotivo() {
		return motivo;
	}

	public LocalDateTime getCreadoEn() {
		return creadoEn;
	}

	public String getCreadoPor() {
		return creadoPor;
	}
}
