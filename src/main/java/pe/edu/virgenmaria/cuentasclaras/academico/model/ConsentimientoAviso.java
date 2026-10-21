package pe.edu.virgenmaria.cuentasclaras.academico.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador;

import java.util.Objects;

/**
 * Sprint 9, tanda 2 (sección 3.6, H2, decisión 152): el consentimiento de un apoderado para una finalidad (asistencia o
 * comunicados) y un canal (WhatsApp o correo). SOLO INSERCIÓN: vale la última fila de cada apoderado, finalidad y canal.
 * WhatsApp exige un «sí» explícito (opt-in de Meta); el correo sale salvo oposición. Lo da el propio apoderado en el
 * portal, o Administración o Dirección lo registra desde la ficha de matrícula firmada, con su código como evidencia
 * (trg_consentimiento_aviso_nace). Ninguna columna se actualiza (1142).
 */
@Entity
@Table(name = "consentimiento_aviso")
public class ConsentimientoAviso extends BaseEntity {

	public static final int MAX_EVIDENCIA = 40;

	@Column(name = "familia_id", nullable = false, updatable = false)
	private Long familiaId;

	@Column(name = "apoderado_id", nullable = false, updatable = false)
	private Long apoderadoId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 12)
	private Finalidad finalidad;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 10)
	private Canal canal;

	@Column(nullable = false, updatable = false)
	private Boolean otorgado;

	@Column(name = "contacto_principal", nullable = false, updatable = false)
	private Boolean contactoPrincipal;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 8)
	private OrigenConsentimiento origen;

	@Column(updatable = false, length = MAX_EVIDENCIA)
	private String evidencia;

	@Column(name = "aviso_privacidad_version", nullable = false, updatable = false, length = 20)
	private String avisoPrivacidadVersion;

	@Column(name = "sesion_id", nullable = false, updatable = false)
	private Long sesionId;

	protected ConsentimientoAviso() {
		// requerido por JPA
	}

	/** Lo da (o lo retira) el propio apoderado en el portal. */
	public static ConsentimientoAviso enPortal(Long familiaId, Long apoderadoId, Finalidad finalidad, Canal canal,
			boolean otorga, boolean principal, String versionAviso, Long sesionId) {
		return nuevo(familiaId, apoderadoId, finalidad, canal, otorga, principal, OrigenConsentimiento.PORTAL, null,
				versionAviso, sesionId);
	}

	/** Lo registra Administración o Dirección desde la ficha de matrícula firmada (el código es la evidencia). */
	public static ConsentimientoAviso desdeFicha(Long familiaId, Long apoderadoId, Finalidad finalidad, Canal canal,
			String codigoFicha, String versionAviso, Long sesionId) {
		String codigo = Normalizador.limpiar(codigoFicha);
		if (codigo == null || codigo.length() < 3 || codigo.length() > MAX_EVIDENCIA) {
			throw new ReglaNegocioException("Escribe el código de la ficha de matrícula firmada (de 3 a " + MAX_EVIDENCIA
					+ " caracteres): es la evidencia del consentimiento.");
		}
		return nuevo(familiaId, apoderadoId, finalidad, canal, true, false, OrigenConsentimiento.FICHA, codigo,
				versionAviso, sesionId);
	}

	private static ConsentimientoAviso nuevo(Long familiaId, Long apoderadoId, Finalidad finalidad, Canal canal,
			boolean otorga, boolean principal, OrigenConsentimiento origen, String evidencia, String versionAviso,
			Long sesionId) {
		ConsentimientoAviso c = new ConsentimientoAviso();
		c.familiaId = Objects.requireNonNull(familiaId, "familiaId");
		c.apoderadoId = Objects.requireNonNull(apoderadoId, "apoderadoId");
		c.finalidad = Objects.requireNonNull(finalidad, "finalidad");
		c.canal = Objects.requireNonNull(canal, "canal");
		c.otorgado = otorga;
		if (principal && !(finalidad == Finalidad.COMUNICADOS && canal == Canal.WHATSAPP && otorga)) {
			throw new ReglaNegocioException("El contacto principal es el que recibe el resumen de comunicados por WhatsApp.");
		}
		c.contactoPrincipal = principal;
		c.origen = origen;
		c.evidencia = evidencia;
		c.avisoPrivacidadVersion = Objects.requireNonNull(versionAviso, "versionAviso");
		c.sesionId = Objects.requireNonNull(sesionId, "sesionId");
		return c;
	}

	@PreUpdate
	void impedirCambio() {
		throw new IllegalStateException("El consentimiento es de solo inserción: vale la última fila.");
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("El consentimiento es de solo inserción: no se borra.");
	}

	public Long getFamiliaId() {
		return familiaId;
	}

	public Long getApoderadoId() {
		return apoderadoId;
	}

	public Finalidad getFinalidad() {
		return finalidad;
	}

	public Canal getCanal() {
		return canal;
	}

	public boolean isOtorgado() {
		return Boolean.TRUE.equals(otorgado);
	}

	public boolean isContactoPrincipal() {
		return Boolean.TRUE.equals(contactoPrincipal);
	}

	public OrigenConsentimiento getOrigen() {
		return origen;
	}

	public String getEvidencia() {
		return evidencia;
	}

	public String getAvisoPrivacidadVersion() {
		return avisoPrivacidadVersion;
	}

	public Long getSesionId() {
		return sesionId;
	}
}
