package pe.edu.virgenmaria.cuentasclaras.alumnos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Apoderado: pertenece a una sola familia (no cambia) y debe poder recibir avisos (WhatsApp o correo, también lo
 * exige la base). No se borra: se desactiva, y no si es responsable de pago de un alumno activo.
 */
@Entity
@Table(name = "apoderado")
public class Apoderado extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "familia_id", nullable = false, updatable = false)
	private Familia familia;

	@Embedded
	private DocumentoIdentidad documento;

	@Column(name = "apellido_paterno", nullable = false, length = 60)
	private String apellidoPaterno;

	@Column(name = "apellido_materno", length = 60)
	private String apellidoMaterno;

	@Column(nullable = false, length = 60)
	private String nombres;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Parentesco parentesco;

	@Column(name = "telefono_whatsapp", length = 16)
	private String telefonoWhatsapp;

	@Column(length = 150)
	private String correo;

	@Column(name = "nombre_busqueda", nullable = false, length = 190)
	private String nombreBusqueda;

	@Column(nullable = false)
	private boolean activo = true;

	/** B2: RUC para facturar (con su razón social). Solo con una solicitud DATOS_FACTURACION aprobada (su id). */
	@Column(length = 11)
	private String ruc;

	@Column(name = "razon_social", length = 150)
	private String razonSocial;

	@Column(name = "facturacion_solicitud_id")
	private Long facturacionSolicitudId;

	/**
	 * Sprint 5 (hallazgo 4): la solicitud CAMBIO_CONTACTO_APODERADO aprobada que fijó el celular y el correo actuales. El
	 * apoderado nace sin ella; en MySQL, trg_apoderado_facturacion exige SU solicitud aprobada para cada cambio, y
	 * trg_mensaje_nace solo escribe a un contacto que también es del personal si otra persona lo aprobó.
	 */
	@Column(name = "contacto_solicitud_id")
	private Long contactoSolicitudId;

	/**
	 * Sprint 5, tanda 3 (decisión 45): el apoderado puede apagar los RECORDATORIOS de vencimiento desde el portal. Los avisos
	 * de pago, anulación y descuento no dependen de esto: son el control antifraude y nunca se apagan.
	 */
	@Column(name = "recordatorios_activos", nullable = false)
	private boolean recordatoriosActivos = true;

	/**
	 * Correcciones del sprint 5 (S5-A1): el último celular y el último correo que su titular VERIFICÓ con el enlace de un
	 * solo uso. Un contacto recibe avisos y enlaces solo si es igual al verificado; cambiarlo lo deja pendiente.
	 */
	@Column(name = "telefono_verificado", length = 16)
	private String telefonoVerificado;

	@Column(name = "correo_verificado", length = 150)
	private String correoVerificado;

	/**
	 * S5-M1: el celular y el correo que otra persona APROBÓ en una solicitud de contacto. Un contacto que también es del
	 * personal solo recibe avisos si es exactamente el aprobado para su canal.
	 */
	@Column(name = "contacto_aprobado_telefono", length = 16)
	private String contactoAprobadoTelefono;

	@Column(name = "contacto_aprobado_correo", length = 150)
	private String contactoAprobadoCorreo;

	protected Apoderado() {
		// requerido por JPA
	}

	public static Apoderado nuevo(Familia familia, DatosApoderado datos) {
		Apoderado apoderado = new Apoderado();
		apoderado.familia = Objects.requireNonNull(familia, "familia");
		apoderado.asignar(datos);
		return apoderado;
	}

	/** @return los campos que cambiaron (vacía si no cambió nada) */
	public List<String> actualizar(DatosApoderado datos) {
		List<String> cambios = new ArrayList<>();
		if (!documento.equals(datos.documento())) {
			cambios.add("documento");
		}
		if (!Objects.equals(apellidoPaterno, datos.apellidoPaterno())
				|| !Objects.equals(apellidoMaterno, datos.apellidoMaterno())
				|| !Objects.equals(nombres, datos.nombres())) {
			cambios.add("nombre");
		}
		if (parentesco != datos.parentesco()) {
			cambios.add("parentesco");
		}
		if (!Objects.equals(telefonoWhatsapp, datos.telefonoWhatsapp())) {
			cambios.add("celular");
		}
		if (!Objects.equals(correo, datos.correo())) {
			cambios.add("correo");
		}
		if (!cambios.isEmpty()) {
			asignar(datos);
		}
		return cambios;
	}

	public void desactivar() {
		if (!activo) {
			throw new ReglaNegocioException(nombreCompleto() + " ya está desactivado.");
		}
		activo = false;
	}

	public String nombreCompleto() {
		return nombres + " " + apellidoPaterno + (apellidoMaterno == null ? "" : " " + apellidoMaterno);
	}

	private void asignar(DatosApoderado datos) {
		Objects.requireNonNull(datos, "datos");
		if (datos.telefonoWhatsapp() == null && datos.correo() == null) {
			throw new ReglaNegocioException("El apoderado necesita un celular para WhatsApp o un correo.");
		}
		documento = Objects.requireNonNull(datos.documento(), "documento");
		apellidoPaterno = Objects.requireNonNull(datos.apellidoPaterno(), "apellidoPaterno");
		apellidoMaterno = datos.apellidoMaterno();
		nombres = Objects.requireNonNull(datos.nombres(), "nombres");
		parentesco = Objects.requireNonNull(datos.parentesco(), "parentesco");
		telefonoWhatsapp = datos.telefonoWhatsapp();
		correo = datos.correo();
		nombreBusqueda = Normalizador.paraBusqueda(apellidoPaterno, apellidoMaterno, nombres);
	}

	public Familia getFamilia() {
		return familia;
	}

	public DocumentoIdentidad getDocumento() {
		return documento;
	}

	public String getApellidoPaterno() {
		return apellidoPaterno;
	}

	public String getApellidoMaterno() {
		return apellidoMaterno;
	}

	public String getNombres() {
		return nombres;
	}

	public Parentesco getParentesco() {
		return parentesco;
	}

	public String getTelefonoWhatsapp() {
		return telefonoWhatsapp;
	}

	public String getCorreo() {
		return correo;
	}

	public boolean isActivo() {
		return activo;
	}

	public boolean isRecordatoriosActivos() {
		return recordatoriosActivos;
	}

	/** Solo el propio apoderado, desde el portal (ServicioPreferencias). */
	public void cambiarRecordatorios(boolean activos) {
		this.recordatoriosActivos = activos;
	}

	/**
	 * Registra (o quita, con {@code ruc} nulo) los datos de facturación aprobados en la solicitud {@code solicitudId}.
	 * En MySQL, trg_apoderado_facturacion exige esa solicitud aprobada.
	 */
	public void registrarFacturacion(String nuevoRuc, String nuevaRazonSocial, Long solicitudId) {
		if ((nuevoRuc == null) != (nuevaRazonSocial == null)) {
			throw new IllegalArgumentException("RUC y razón social van juntos");
		}
		ruc = nuevoRuc;
		razonSocial = nuevaRazonSocial;
		facturacionSolicitudId = Objects.requireNonNull(solicitudId, "solicitudId");
	}

	public String getRuc() {
		return ruc;
	}

	public String getRazonSocial() {
		return razonSocial;
	}

	/** El contacto cambió con la solicitud aprobada {@code solicitudId} (en MySQL lo exige trg_apoderado_facturacion). */
	public void registrarSolicitudContacto(Long solicitudId) {
		contactoSolicitudId = Objects.requireNonNull(solicitudId, "solicitudId");
	}

	/** El celular registrado ya lo verificó su titular. */
	public boolean telefonoVerificado() {
		return telefonoWhatsapp != null && telefonoWhatsapp.equals(telefonoVerificado);
	}

	/** El correo registrado ya lo verificó su titular. */
	public boolean correoVerificado() {
		return correo != null && correo.equals(correoVerificado);
	}

	/**
	 * El titular verificó {@code contacto} con su enlace (solo si sigue siendo el registrado para ese canal; en MySQL lo
	 * exige trg_apoderado_facturacion con la verificación usada).
	 *
	 * @return {@code false} si el contacto ya no es el registrado
	 */
	public boolean verificarContacto(boolean whatsapp, String contacto) {
		if (whatsapp && contacto != null && contacto.equals(telefonoWhatsapp)) {
			telefonoVerificado = contacto;
			return true;
		}
		if (!whatsapp && contacto != null && contacto.equals(correo)) {
			correoVerificado = contacto;
			return true;
		}
		return false;
	}

	/** S5-M1: otra persona aprobó el contacto ACTUAL de estos canales (solo los cambiados o los pedidos). */
	public void aprobarContacto(boolean telefono, boolean elCorreo) {
		if (telefono) {
			contactoAprobadoTelefono = telefonoWhatsapp;
		}
		if (elCorreo) {
			contactoAprobadoCorreo = correo;
		}
	}

	public String getTelefonoVerificado() {
		return telefonoVerificado;
	}

	public String getCorreoVerificado() {
		return correoVerificado;
	}

	public String getContactoAprobadoTelefono() {
		return contactoAprobadoTelefono;
	}

	public String getContactoAprobadoCorreo() {
		return contactoAprobadoCorreo;
	}

	public Long getContactoSolicitudId() {
		return contactoSolicitudId;
	}

	public Long getFacturacionSolicitudId() {
		return facturacionSolicitudId;
	}
}
