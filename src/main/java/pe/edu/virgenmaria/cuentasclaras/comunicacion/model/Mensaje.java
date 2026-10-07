package pe.edu.virgenmaria.cuentasclaras.comunicacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Mensaje a una familia o al personal (sprint 5): el OUTBOX. Se crea en la MISMA transacción del pago, la anulación o el
 * descuento (sin mensaje no hay pago) y lo envía después {@code sistema.mensajeria}.
 * <ul>
 *   <li>El destino es una COPIA del contacto registrado del destinatario al crearlo; en MySQL lo exige trg_mensaje_nace y
 *       no cambia (sin GRANT de UPDATE: 1143), igual que el tipo, la plantilla, los parámetros y el destinatario.</li>
 *   <li>Los parámetros los arma el sistema; nunca llevan el token de activación (CHECK) ni el DNI.</li>
 *   <li>Solo cambian el estado, el proveedor, los intentos y las fechas de envío y entrega, y nunca hacia atrás
 *       (trg_mensaje_envio). No se borra.</li>
 * </ul>
 */
@Entity
@Table(name = "mensaje")
public class Mensaje extends BaseEntity {

	/** Separa los parámetros guardados (ninguno lo lleva: se reemplaza por un espacio). */
	static final String SEPARADOR = "\n";

	@Column(nullable = false, updatable = false, length = 120)
	private String clave;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 30)
	private TipoMensaje tipo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 10)
	private CanalMensaje canal;

	@Enumerated(EnumType.STRING)
	@Column(name = "destinatario_tipo", nullable = false, updatable = false, length = 10)
	private DestinatarioTipo destinatarioTipo;

	@Column(name = "apoderado_id", updatable = false)
	private Long apoderadoId;

	@Column(name = "familia_id", updatable = false)
	private Long familiaId;

	@Column(name = "usuario_id", updatable = false)
	private Long usuarioId;

	@Column(nullable = false, updatable = false, length = 150)
	private String destino;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 60)
	private PlantillaMensaje plantilla;

	@Column(nullable = false, updatable = false, length = 1000)
	private String parametros;

	@Column(updatable = false, length = 40)
	private String entidad;

	@Column(name = "entidad_id", updatable = false)
	private Long entidadId;

	@Column(name = "respaldo_de_id", updatable = false)
	private Long respaldoDeId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoMensaje estado = EstadoMensaje.PENDIENTE;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private ProveedorMensajeria proveedor;

	@Column(name = "proveedor_mensaje_id", length = 120)
	private String proveedorMensajeId;

	@Column(nullable = false)
	private int intentos;

	@Column(name = "proximo_intento_en")
	private LocalDateTime proximoIntentoEn;

	@Column(name = "enviado_en")
	private LocalDateTime enviadoEn;

	@Column(name = "entregado_en")
	private LocalDateTime entregadoEn;

	@Column(name = "leido_en")
	private LocalDateTime leidoEn;

	@Column(name = "ultimo_error", length = 250)
	private String ultimoError;

	protected Mensaje() {
		// requerido por JPA
	}

	/** Destino y destinatario de un mensaje nuevo (el contacto registrado, ya resuelto por quien lo crea). */
	public record Destinatario(DestinatarioTipo tipo, Long apoderadoId, Long familiaId, Long usuarioId, CanalMensaje canal,
			String destino) {

		public Destinatario {
			Objects.requireNonNull(tipo, "tipo");
			Objects.requireNonNull(canal, "canal");
			Objects.requireNonNull(destino, "destino");
		}
	}

	public static Mensaje nuevo(String clave, TipoMensaje tipo, PlantillaMensaje plantilla, Destinatario para,
			List<String> parametros, String entidad, Long entidadId, Long respaldoDeId, LocalDateTime proximoIntento) {
		Mensaje m = new Mensaje();
		m.clave = recortar(Objects.requireNonNull(clave, "clave"), 120);
		m.tipo = Objects.requireNonNull(tipo, "tipo");
		m.plantilla = Objects.requireNonNull(plantilla, "plantilla");
		m.canal = para.canal();
		m.destinatarioTipo = para.tipo();
		m.apoderadoId = para.apoderadoId();
		m.familiaId = para.familiaId();
		m.usuarioId = para.usuarioId();
		m.destino = para.destino();
		m.parametros = unir(parametros);
		if (m.parametros.contains("/activar/")) {
			throw new IllegalArgumentException("Los parámetros de un mensaje nunca llevan el enlace de activación");
		}
		m.entidad = entidad;
		m.entidadId = entidadId;
		m.respaldoDeId = respaldoDeId;
		m.proximoIntentoEn = proximoIntento;
		return m;
	}

	/** El proveedor lo aceptó con su id. */
	public void marcarEnviado(ProveedorMensajeria quien, String idProveedor, LocalDateTime ahora) {
		cambiarA(EstadoMensaje.ENVIADO);
		proveedor = Objects.requireNonNull(quien, "proveedor");
		proveedorMensajeId = recortar(Objects.requireNonNull(idProveedor, "idProveedor"), 120);
		enviadoEn = Objects.requireNonNull(ahora, "ahora");
		intentos++;
		proximoIntentoEn = null;
		ultimoError = null;
	}

	/** Error de red o del proveedor: un intento más y el siguiente con espera creciente. */
	public void reintentarEn(String error, LocalDateTime proximo) {
		exigir(EstadoMensaje.PENDIENTE);
		intentos++;
		ultimoError = recortar(error, 250);
		proximoIntentoEn = proximo;
	}

	/** Error definitivo (número inválido, sin WhatsApp, plantilla rechazada) o se agotaron los intentos. */
	public void fallar(String error, boolean contarIntento) {
		cambiarA(EstadoMensaje.FALLIDO);
		if (contarIntento) {
			intentos++;
		}
		ultimoError = recortar(error == null ? "No se pudo enviar." : error, 250);
		proximoIntentoEn = null;
	}

	/** Aviso del proveedor: entregado. Si ya está en un estado posterior, no retrocede. */
	public boolean entregado(LocalDateTime cuando) {
		if (!estado.puedePasarA(EstadoMensaje.ENTREGADO)) {
			return false;
		}
		estado = EstadoMensaje.ENTREGADO;
		entregadoEn = cuando;
		return true;
	}

	/** Aviso del proveedor: leído (puede llegar antes que el de entregado). */
	public boolean leido(LocalDateTime cuando) {
		if (!estado.puedePasarA(EstadoMensaje.LEIDO)) {
			return false;
		}
		estado = EstadoMensaje.LEIDO;
		leidoEn = cuando;
		return true;
	}

	/** Administración adelanta el siguiente intento de un PENDIENTE (nunca edita el destino ni el texto). */
	/**
	 * Tanda 3: un recordatorio fuera de su ventana (lunes a sábado de 08:00 a 20:00, nunca en feriado) espera a la
	 * siguiente. No cuenta como intento ni cambia nada más.
	 */
	public void posponerHasta(LocalDateTime cuando) {
		if (estado != EstadoMensaje.PENDIENTE) {
			throw new IllegalStateException("Solo se pospone un mensaje PENDIENTE");
		}
		proximoIntentoEn = cuando;
	}

	public void adelantar(LocalDateTime ahora) {
		exigir(EstadoMensaje.PENDIENTE);
		proximoIntentoEn = ahora;
	}

	public List<String> parametrosLista() {
		return parametros.isEmpty() ? List.of() : Arrays.asList(parametros.split(SEPARADOR, -1));
	}

	private void cambiarA(EstadoMensaje nuevo) {
		if (!estado.puedePasarA(nuevo)) {
			throw new IllegalStateException("Un mensaje " + estado + " no pasa a " + nuevo);
		}
		estado = nuevo;
	}

	private void exigir(EstadoMensaje esperado) {
		if (estado != esperado) {
			throw new IllegalStateException("El mensaje no está " + esperado);
		}
	}

	private static String unir(List<String> valores) {
		if (valores == null || valores.isEmpty()) {
			return "";
		}
		String texto = String.join(SEPARADOR, valores.stream()
				.map(v -> v == null ? "" : v.replace(SEPARADOR, " ").replace("\r", " ").strip()).toList());
		return recortar(texto, 1000);
	}

	private static String recortar(String texto, int maximo) {
		return texto == null || texto.length() <= maximo ? texto : texto.substring(0, maximo);
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Los mensajes no se borran.");
	}

	public String getClave() {
		return clave;
	}

	public TipoMensaje getTipo() {
		return tipo;
	}

	public CanalMensaje getCanal() {
		return canal;
	}

	public DestinatarioTipo getDestinatarioTipo() {
		return destinatarioTipo;
	}

	public Long getApoderadoId() {
		return apoderadoId;
	}

	public Long getFamiliaId() {
		return familiaId;
	}

	public Long getUsuarioId() {
		return usuarioId;
	}

	public String getDestino() {
		return destino;
	}

	public PlantillaMensaje getPlantilla() {
		return plantilla;
	}

	public String getParametros() {
		return parametros;
	}

	public String getEntidad() {
		return entidad;
	}

	public Long getEntidadId() {
		return entidadId;
	}

	public Long getRespaldoDeId() {
		return respaldoDeId;
	}

	public EstadoMensaje getEstado() {
		return estado;
	}

	public ProveedorMensajeria getProveedor() {
		return proveedor;
	}

	public String getProveedorMensajeId() {
		return proveedorMensajeId;
	}

	public int getIntentos() {
		return intentos;
	}

	public LocalDateTime getProximoIntentoEn() {
		return proximoIntentoEn;
	}

	public LocalDateTime getEnviadoEn() {
		return enviadoEn;
	}

	public LocalDateTime getEntregadoEn() {
		return entregadoEn;
	}

	public LocalDateTime getLeidoEn() {
		return leidoEn;
	}

	public String getUltimoError() {
		return ultimoError;
	}

	@Override
	public String toString() {
		return "Mensaje[id=" + getId() + ", tipo=" + tipo + ", canal=" + canal + ", estado=" + estado + "]";
	}
}
