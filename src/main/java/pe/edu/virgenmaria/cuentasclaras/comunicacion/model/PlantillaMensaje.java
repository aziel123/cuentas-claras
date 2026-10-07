package pe.edu.virgenmaria.cuentasclaras.comunicacion.model;

import java.util.List;
import java.util.Objects;

/**
 * Plantillas FIJAS de los mensajes (sprint 5, sección 12.2). Ninguna persona escribe texto libre a un apoderado: los
 * parámetros los arma el sistema desde el pago, la anulación o el descuento. El nombre es el de la plantilla aprobada
 * por Meta (categoría «utility»); el texto es el mismo para el correo. Ninguna menciona evaluaciones, notas, exámenes,
 * libretas ni constancias, ni amenaza con consecuencias (INDECOPI; lo revisa {@code PlantillasMensajeTest}).
 */
public enum PlantillaMensaje {

	PAGO_REGISTRADO("cc_pago_registrado", "Registramos su pago", 6, true,
			"Colegio Virgen María: registramos su pago de {{1}} por {{2}}. Comprobante {{3}} del {{4}}, {{5}}, registrado "
					+ "por {{6}}. Si no reconoce este pago, avísenos desde el portal."),
	PAGO_ANULADO("cc_pago_anulado", "Se anuló un pago", 5, true,
			"Colegio Virgen María: se anuló el pago {{1}} de {{2}} ({{3}}). Motivo: {{4}}. Aprobó: {{5}}. Si usted no lo "
					+ "pidió, avísenos desde el portal."),
	DESCUENTO_APROBADO("cc_descuento_aprobado", "Se aprobó un descuento", 3, true,
			"Colegio Virgen María: se aprobó un descuento de {{1}} en {{2}}. Aprobó: {{3}}. Si usted no lo solicitó, "
					+ "avísenos desde el portal."),
	ACTIVACION("cc_activacion", "Activa tu acceso a Cuentas Claras", 1, true,
			"Colegio Virgen María: para activar su acceso a Cuentas Claras use este enlace (vence el {{1}}). Nadie del "
					+ "colegio se lo pedirá."),
	CONTACTO_CAMBIADO("cc_contacto_cambiado", "Cambió su contacto en el colegio", 1, false,
			"Colegio Virgen María: este {{1}} dejó de recibir los avisos de pagos del colegio porque se registró otro. Si "
					+ "usted no lo pidió, avísenos en el colegio o desde el portal."),
	HUELLA("cc_huella", "Huella diaria de la bitácora", 4, false,
			"Cuentas Claras: huella del {{1}}: evento {{2}}, código {{3}}. Bitácora verificada: {{4}}. Guarde este mensaje."),
	// Sprint 5 · tanda 2: renovación de matrícula y respuesta a los avisos de la familia.
	RENOVACION("cc_renovacion", "Confirme si su hijo continúa el próximo año", 4, true,
			"Colegio Virgen María: confirme en el portal si {{1}} continúa en {{2}} el {{3}}. Puede responder hasta el "
					+ "{{4}}. Si no responde, no se genera ningún cobro."),
	RENOVACION_REGISTRADA("cc_renovacion_registrada", "Registramos su respuesta sobre el próximo año", 3, true,
			"Colegio Virgen María: registramos en el colegio que {{1}} {{2}} el {{3}}. Si usted no lo pidió, avísenos "
					+ "desde el portal."),
	AVISO_ATENDIDO("cc_aviso_atendido", "Respondimos su aviso", 1, true,
			"Colegio Virgen María: respondimos el aviso que nos envió el {{1}}. Vea la respuesta en el portal."),
	// Sprint 5 · tanda 3: recordatorios (decisión 44). Sin mencionar lo académico ni amenazar (INDECOPI).
	RECORDATORIO("cc_recordatorio", "Recordatorio de vencimiento", 4, true,
			"Colegio Virgen María: le recordamos que {{1}} vence el {{2}} ({{3}}). Puede pagar en línea, en el banco con "
					+ "el código {{4}} o en caja. Si ya pagó, ignore este mensaje."),
	CUOTA_VENCIDA("cc_cuota_vencida", "Tiene un pago pendiente", 3, true,
			"Colegio Virgen María: {{1}} venció el {{2}} ({{3}}). Puede pagar en línea, en el banco o en caja. Si ya pagó, "
					+ "ignore este mensaje.");

	private final String nombreMeta;

	private final String asunto;

	private final int parametros;

	private final boolean botonPortal;

	private final String texto;

	PlantillaMensaje(String nombreMeta, String asunto, int parametros, boolean botonPortal, String texto) {
		this.nombreMeta = nombreMeta;
		this.asunto = asunto;
		this.parametros = parametros;
		this.botonPortal = botonPortal;
		this.texto = texto;
	}

	/** El nombre de la plantilla aprobada por Meta. */
	public String nombreMeta() {
		return nombreMeta;
	}

	public String asunto() {
		return asunto;
	}

	public int parametros() {
		return parametros;
	}

	/** Lleva el botón con enlace (al portal, o el de activación con su sufijo). */
	public boolean botonPortal() {
		return botonPortal;
	}

	/** El texto base, con {{1}}...{{n}}. */
	public String texto() {
		return texto;
	}

	/** El texto con sus parámetros (para el correo y el historial). */
	public String componer(List<String> valores) {
		Objects.requireNonNull(valores, "valores");
		if (valores.size() != parametros) {
			throw new IllegalArgumentException("La plantilla " + nombreMeta + " lleva " + parametros + " parámetros");
		}
		String resultado = texto;
		for (int i = 0; i < valores.size(); i++) {
			resultado = resultado.replace("{{" + (i + 1) + "}}", valores.get(i));
		}
		return resultado;
	}
}
