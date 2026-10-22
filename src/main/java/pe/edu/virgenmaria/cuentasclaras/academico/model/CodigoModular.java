package pe.edu.virgenmaria.cuentasclaras.academico.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.util.Objects;

/**
 * Sprint 9, tanda 3 (sección 3.11): el código modular del colegio para un nivel (7 dígitos) con su anexo (1 dígito), como
 * se ve en ESCALE. Uno por nivel. Lo registra Dirección; el nivel no cambia (GRANT por columna: código, anexo y quién).
 */
@Entity
@Table(name = "codigo_modular")
public class CodigoModular extends BaseEntity {

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 12)
	private Nivel nivel;

	@Column(nullable = false, length = 7)
	private String codigo;

	@Column(nullable = false, length = 1)
	private String anexo;

	@Column(name = "actualizado_por", nullable = false, length = 60)
	private String actualizadoPor;

	protected CodigoModular() {
		// requerido por JPA
	}

	public static CodigoModular nuevo(Nivel nivel, String codigo, String anexo, String por) {
		CodigoModular c = new CodigoModular();
		c.nivel = Objects.requireNonNull(nivel, "nivel");
		c.cambiar(codigo, anexo, por);
		return c;
	}

	public void cambiar(String nuevoCodigo, String nuevoAnexo, String por) {
		String codigoLimpio = nuevoCodigo == null ? "" : nuevoCodigo.strip();
		String anexoLimpio = nuevoAnexo == null || nuevoAnexo.isBlank() ? "0" : nuevoAnexo.strip();
		if (!codigoLimpio.matches("[0-9]{7}") || !anexoLimpio.matches("[0-9]")) {
			throw new ReglaNegocioException("El código modular tiene 7 dígitos y el anexo, 1 (búscalos en ESCALE).");
		}
		this.codigo = codigoLimpio;
		this.anexo = anexoLimpio;
		this.actualizadoPor = Objects.requireNonNull(por, "por");
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Un código modular no se borra: se corrige.");
	}

	public Nivel getNivel() {
		return nivel;
	}

	public String getCodigo() {
		return codigo;
	}

	public String getAnexo() {
		return anexo;
	}

	public String getActualizadoPor() {
		return actualizadoPor;
	}
}
