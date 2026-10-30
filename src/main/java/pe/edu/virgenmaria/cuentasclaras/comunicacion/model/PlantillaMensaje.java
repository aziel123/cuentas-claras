package pe.edu.virgenmaria.cuentasclaras.comunicacion.model;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plantillas FIJAS de los mensajes (sprint 5, sección 12.2). Ninguna persona escribe texto libre a un apoderado: los
 * parámetros los arma el sistema desde el pago, la anulación o el descuento. El nombre es el de la plantilla aprobada
 * por Meta (categoría «utility»); el texto es el mismo para el correo. Ninguna menciona evaluaciones, notas, exámenes,
 * libretas ni constancias, ni amenaza con consecuencias (INDECOPI; lo revisa {@code PlantillasMensajeTest}).
 * <p>
 * Sprint 8, tanda 1 (sección 3.7, decisiones 120 a 122): las plantillas {@code _v2} no llevan la marca ni el nombre de
 * ningún colegio fijos. TODAS empiezan con {@code {{1}}} = el nombre del colegio que escribe (para el colegio de
 * plataforma, la marca del producto). Ese primer parámetro lo antepone SIEMPRE {@code CreadorMensajes} al guardar el
 * mensaje: los servicios que piden un mensaje arman solo los parámetros propios ({@code {{2}}} en adelante). La marca
 * del producto va en el nombre visible del número de WhatsApp Business y en el remitente del correo, nunca en el texto.
 */
public enum PlantillaMensaje {

	PAGO_REGISTRADO("cc_pago_registrado_v2", "Registramos su pago", 7, true,
			"{{1}}: registramos su pago de {{2}} por {{3}}. Comprobante {{4}} del {{5}}, {{6}}, registrado "
					+ "por {{7}}. Si no reconoce este pago, avísenos desde el portal."),
	PAGO_ANULADO("cc_pago_anulado_v2", "Se anuló un pago", 6, true,
			"{{1}}: se anuló el pago {{2}} de {{3}} ({{4}}). Motivo: {{5}}. Aprobó: {{6}}. Si usted no lo "
					+ "pidió, avísenos desde el portal."),
	DESCUENTO_APROBADO("cc_descuento_aprobado_v2", "Se aprobó un descuento", 4, true,
			"{{1}}: se aprobó un descuento de {{2}} en {{3}}. Aprobó: {{4}}. Si usted no lo solicitó, "
					+ "avísenos desde el portal."),
	ACTIVACION("cc_activacion_v2", "Activa tu acceso", 2, true,
			"{{1}}: para activar su acceso al portal use este enlace (vence el {{2}}). Nadie del "
					+ "colegio se lo pedirá."),
	CONTACTO_CAMBIADO("cc_contacto_cambiado_v2", "Cambió su contacto en el colegio", 2, false,
			"{{1}}: este {{2}} dejó de recibir los avisos de pagos del colegio porque se registró otro. Si "
					+ "usted no lo pidió, avísenos en el colegio o desde el portal."),
	HUELLA("cc_huella_v2", "Huella diaria de la bitácora", 6, false,
			"{{1}}: huella del {{2}}: evento {{3}}, código {{4}}. Huella anterior: {{5}}. Bitácora verificada: {{6}}. "
					+ "Guarde este mensaje y compárelo con el de ayer."),
	// Sprint 5 · tanda 2: renovación de matrícula y respuesta a los avisos de la familia.
	RENOVACION("cc_renovacion_v2", "Confirme si su hijo continúa el próximo año", 5, true,
			"{{1}}: confirme en el portal si {{2}} continúa en {{3}} el {{4}}. Puede responder hasta el "
					+ "{{5}}. Si no responde, no se genera ningún cobro."),
	RENOVACION_REGISTRADA("cc_renovacion_registrada_v2", "Registramos su respuesta sobre el próximo año", 4, true,
			"{{1}}: registramos en el colegio que {{2}} {{3}} el {{4}}. Si usted no lo pidió, avísenos "
					+ "desde el portal."),
	AVISO_ATENDIDO("cc_aviso_atendido_v2", "Respondimos su aviso", 2, true,
			"{{1}}: respondimos el aviso que nos envió el {{2}}. Vea la respuesta en el portal."),
	// Sprint 5 · tanda 3: recordatorios (decisión 44). Sin mencionar lo académico ni amenazar (INDECOPI).
	RECORDATORIO("cc_recordatorio_v2", "Recordatorio de vencimiento", 5, true,
			"{{1}}: le recordamos que {{2}} vence el {{3}} ({{4}}). Puede pagar en línea, en el banco con "
					+ "el código {{5}} o en caja. Si ya pagó, ignore este mensaje."),
	CUOTA_VENCIDA("cc_cuota_vencida_v2", "Tiene un pago pendiente", 4, true,
			"{{1}}: {{2}} venció el {{3}} ({{4}}). Puede pagar en línea, en el banco o en caja. Si ya pagó, "
					+ "ignore este mensaje."),
	// Correcciones del sprint 5 (S5-A1 y S5-M3).
	VERIFICACION("cc_verificacion_v2", "Confirme su contacto en el colegio", 3, true,
			"{{1}}: confirme que este {{2}} es suyo para recibir los avisos de pagos del colegio. Use este "
					+ "enlace (vence el {{3}}). Si usted no lo pidió, no lo use y avísenos."),
	APODERADO_AGREGADO("cc_apoderado_agregado_v2", "Se agregó un apoderado a su familia", 3, true,
			"{{1}}: se registró a {{2}} como apoderado(a) de su familia, con el contacto {{3}}. Si no lo "
					+ "reconoce, avísenos desde el portal."),
	CONTACTO_POR_VERIFICAR("cc_contacto_por_verificar_v2", "Se registró un contacto nuevo en su familia", 4, true,
			"{{1}}: se registró el {{2}} {{3}} para {{4}}. Recibirá los avisos cuando lo confirme. Si no lo "
					+ "reconoce, avísenos desde el portal."),
	FERIADO_PROPUESTO("cc_feriado_propuesto_v2", "Día no laborable por aprobar", 4, false,
			"{{1}}: {{2}} propuso el {{3}} como día no laborable ({{4}}). No cuenta hasta que lo apruebe otra "
					+ "persona de Promotoría o Dirección."),
	// Sprint 6 · tanda 2 (sección 12.2): al personal, SOLO cifras, tipos fijos y la huella; nunca nombres de familias, de
	// alumnos ni de cajeras, ni texto escrito por una persona. Sin enlaces que aprueben (P9): sin botón.
	// Correcciones del sprint 6: {{7}} dice también los cierres con diferencia desde el resumen anterior (S6-M3) y {{13}}
	// los resultados de las llamadas de control de la semana (S6-M2). La foto resumen_diario.parametros guarda solo los 12
	// propios ({{2}} a {{13}}); el mensaje lleva delante el nombre del colegio (trg_mensaje_nace lo compara así).
	RESUMEN_DIARIO("cc_resumen_diario_v2", "Resumen del día", 13, false,
			"{{1}} · {{2}}. Cobrado hoy: {{3}} en {{4}} pagos ({{5}} digital). Efectivo: {{6}}. Cajas: {{7}}. "
					+ "Cobrado en el mes: {{8}}. Vencido por cobrar: {{9}}. Por aprobar: {{10}}. Alertas críticas: {{11}}. "
					+ "Huella 19:00: {{12}}. Llamadas de control: {{13}}. Si algo no te cuadra, revisa el panel."),
	ALERTA_PROMOTORIA("cc_alerta_promotoria_v2", "Alerta del colegio", 3, false,
			"{{1}}: {{2}} ({{3}}). Revísalo en el sistema."),
	ALERTA_MAS("cc_alerta_mas_v2", "Más alertas del colegio", 2, false,
			"{{1}}: hoy hay {{2}} alertas más. Revísalas en el panel."),
	// P6: aviso al contacto ANTERIOR de alguien del personal (tipo CONTACTO_CAMBIADO).
	CONTACTO_PERSONAL_CAMBIADO("cc_contacto_personal_cambiado_v2", "Cambió el contacto de tu cuenta", 2, false,
			"{{1}}: este {{2}} dejó de recibir los mensajes de tu cuenta porque se registró otro. Si no lo "
					+ "pediste, avisa hoy a Promotoría."),
	// Sprint 8 · tanda 2 (sección 3.6, E12): cambió adónde entra el dinero (o quién emite las boletas). A TODA la Promotoría
	// y al correo externo de control. Sin secretos ni el número completo de la cuenta: solo su terminación.
	CONECTOR_VIGENTE("cc_conector_vigente_v2", "Cambió un conector del colegio", 6, false,
			"{{1}}: cambió {{2}} a {{3}} ({{4}}). Lo propuso {{5}} y lo aprobó {{6}}. Si no lo reconoces, avisa hoy "
					+ "al contador y revisa la conciliación."),
	// Sprint 8 · tanda 2 (sección 3.4): el colegio pasó a producción (desde ahora se registran pagos).
	COLEGIO_EN_PRODUCCION("cc_colegio_en_produccion_v2", "El colegio empezó a cobrar", 3, false,
			"{{1}}: el colegio pasó a producción el {{2}} con la firma de {{3}}. Desde ahora se registran los pagos y "
					+ "salen los avisos a las familias."),
	// Correcciones del sprint 8 (S8-A1, S8-A2 y S8-M5): lo que hace el operador de la plataforma en un colegio. Van al
	// titular, al correo institucional, al correo externo de control y a la Promotoría (tipos AVISO_ALTA y CORREO_CONTROL).
	ENLACE_TITULAR_REENVIADO("cc_enlace_titular_reenviado_v2", "Se reenvió el enlace del titular", 3, false,
			"{{1}}: el operador {{2}} de la plataforma reenvió el enlace para activar la cuenta del titular a {{3}}. Si el "
					+ "titular no lo pidió, avisa hoy al proveedor."),
	CONTACTO_TITULAR_CORREGIDO("cc_contacto_titular_corregido_v2", "Cambió el contacto del titular", 4, false,
			"{{1}}: los operadores {{2}} y {{3}} de la plataforma cambiaron el contacto del titular antes de que active su "
					+ "cuenta: su enlace va ahora a {{4}}. Si el titular no lo pidió, avisa hoy al proveedor."),
	FICHA_COLEGIO_CORREGIDA("cc_ficha_colegio_corregida_v2", "Se corrigió la ficha del colegio", 3, false,
			"{{1}}: el operador {{2}} de la plataforma corrigió la ficha del colegio ({{3}}). Si no lo reconoces, avisa hoy "
					+ "al proveedor."),
	CORREO_CONTROL_PROPUESTO("cc_correo_control_propuesto_v2", "Se propuso cambiar el correo de control", 3, false,
			"{{1}}: el operador {{2}} de la plataforma propuso que el correo externo de control sea {{3}}. No cambia hasta "
					+ "que lo apruebe una persona de Promotoría del colegio. Si no lo reconoces, avisa hoy a la Promotoría."),
	CORREO_CONTROL_CAMBIADO("cc_correo_control_cambiado_v2", "Cambió el correo de control", 3, false,
			"{{1}}: el correo externo de control del colegio ahora es {{2}} (lo cambió {{3}}). Desde hoy recibe la huella "
					+ "de la bitácora y el resumen diario. Si no lo reconoces, avisa hoy a la Promotoría y al proveedor."),
	// Sprint 9, tanda 2 (sección 11): asistencia. Solo el primer nombre del alumno (minimización), estados fijos y horas;
	// nunca montos ni palabras de cobranza (lo exigen ContenidoSinCobranza y trg_mensaje_nace). El botón lleva a
	// /familia/asistencia, nunca al estado de cuenta.
	ASISTENCIA("cc_asistencia_v2", "Asistencia de hoy", 3, true,
			"{{1}}: asistencia del {{2}}. {{3}}. Si hay un error o desea justificar una falta, ingrese al portal."),
	ASISTENCIA_CORREGIDA("cc_asistencia_corregida_v2", "Se corrigió la asistencia", 6, true,
			"{{1}}: se corrigió la asistencia de {{2}} del {{3}}: ahora figura {{4}} (antes: {{5}}). Lo registró {{6}}. "
					+ "Si no lo reconoce, avísenos desde el portal."),
	// Sprint 9, tanda 3 (sección 11): comunicados. El resumen no lleva títulos (texto libre de una persona: Meta lo
	// clasificaría como marketing); el urgente, el título que escribió Dirección o Promotoría (pasó por el filtro). Sin
	// montos ni palabras de cobranza; el pie fijo («nunca le pedirá pagos») va en el portal y en el correo, no aquí. El
	// botón lleva a /familia/comunicados.
	COMUNICADOS("cc_comunicados_v2", "Comunicados del colegio", 3, true,
			"{{1}}: tiene {{2}} en el portal, de {{3}}. Léalos y confirme su lectura allí."),
	COMUNICADO_URGENTE("cc_comunicado_urgente_v2", "Comunicado urgente", 3, true,
			"{{1}}: {{2}} le envió un comunicado urgente: «{{3}}». Léalo en el portal."),
	// Sprint 10, tanda 3 (sección 5 del diseño): libretas. El periodo y los primeros nombres; nunca notas, conclusiones,
	// montos ni palabras de cobranza (INDECOPI: la libreta no se retiene por deuda). El botón lleva a /familia/libretas.
	LIBRETA("cc_libreta_v1", "Libreta disponible", 3, true,
			"{{1}}: la libreta del {{2}} de {{3}} ya está en el portal."),
	LIBRETA_CORREGIDA("cc_libreta_corregida_v1", "Se corrigió la libreta", 3, true,
			"{{1}}: se corrigió la libreta del {{2}} de {{3}}. Revise el detalle en el portal."),
	// Sprint 11, tanda 1 (sección 9): el ciclo de vida del colegio y la facultad del titular. Van al personal (titular,
	// Promotoría y operadores) y al correo institucional y al de control; nunca a las familias (ven el aviso en el portal,
	// decisión 195). Qué cambia va como parámetro ({{3}} o {{2}}): la plantilla no lleva palabras de cobranza ni amenazas.
	CAMBIO_ESTADO_PROPUESTO("cc_cambio_estado_propuesto_v2", "Cambio propuesto en el acceso del colegio", 4, false,
			"{{1}}: el operador {{2}} de la plataforma propone {{3}}. Motivo: {{4}}. Todavía no cambia nada: lo debe "
					+ "aprobar otro operador. Si cree que es un error, escriba hoy al proveedor."),
	CAMBIO_ESTADO_APROBADO("cc_cambio_estado_aprobado_v2", "Cambio aprobado en el acceso del colegio", 4, false,
			"{{1}}: los operadores {{4}} de la plataforma aprobaron {{2}}, que rige desde el {{3}}. Hasta esa fecha todo "
					+ "funciona. Si cree que es un error, escriba hoy al proveedor."),
	CAMBIO_ESTADO_RESUELTO("cc_cambio_estado_resuelto_v2", "Se dejó sin efecto un cambio en el colegio", 4, false,
			"{{1}}: el operador {{4}} de la plataforma {{3}} el cambio propuesto ({{2}}). No cambia nada."),
	CAMBIO_ESTADO_APLICADO("cc_cambio_estado_aplicado_v2", "Cambió el acceso del colegio", 3, false,
			"{{1}}: desde el {{3}}, {{2}}. Para cualquier consulta, escriba al proveedor."),
	CAMBIO_ESTADO_EN_ESPERA("cc_cambio_estado_en_espera_v2", "Un cambio del colegio espera", 3, false,
			"{{1}}: {{2}} espera que el colegio resuelva lo siguiente: {{3}}. Mientras tanto no se aplica."),
	CAMBIO_ESTADO_PROXIMO("cc_cambio_estado_proximo_v2", "Recordatorio de un cambio en el colegio", 3, false,
			"{{1}}: recuerde que {{2}} rige desde el {{3}}. Si cree que es un error, escriba hoy al proveedor."),
	OBJECION_TITULAR("cc_objecion_titular_v2", "El titular objetó un cambio del colegio", 3, false,
			"{{1}}: el titular {{2}} objetó {{3}}. No se aplica hasta que otro operador responda la objeción."),
	OBJECION_RESPONDIDA("cc_objecion_respondida_v2", "Se respondió la objeción del titular", 4, false,
			"{{1}}: el operador {{2}} de la plataforma respondió la objeción sobre {{3}}: {{4}}"),
	FACULTAD_TITULAR_USADA("cc_facultad_titular_usada_v2", "El titular cambió Promotoría o Dirección", 5, false,
			"{{1}}: el titular {{2}} {{3}} {{4}} a la cuenta {{5}} sin segunda aprobación (facultad del titular). Si no "
					+ "lo reconoce, escriba hoy al proveedor y a la Promotoría."),
	CODIGO_FACULTAD("cc_codigo_facultad_v2", "Su código para dar o quitar Promotoría o Dirección", 3, false,
			"{{1}}: su código para dar o quitar Promotoría o Dirección es {{2}}. Vence a las {{3}}. Si usted no lo pidió, "
					+ "escriba hoy al proveedor."),
	// Sprint 11, tanda 1c (secciones 3.16 y 3.16b): la Promotoría aplica directo.
	CODIGO_PROMOTORIA("cc_codigo_promotoria_v2", "Su código para una acción en el colegio", 4, false,
			"{{1}}: código {{2}} para: {{3}}. Vence a las {{4}}. Si usted no lo pidió, no lo comparta y escriba hoy al proveedor."),
	CODIGO_CONTACTO("cc_codigo_contacto_v2", "Confirme el cambio de su celular o correo", 4, false,
			"{{1}}: código {{2}} para confirmar: {{3}}. Vence a las {{4}}. Si usted no lo pidió, no lo comparta y avise hoy al colegio."),
	ACCION_DIRECTA("cc_accion_directa_v2", "Una Promotoría aplicó un cambio directo", 3, false,
			"{{1}}: {{2}} aplicó directo: {{3}}. Si no lo reconoce, cambie su clave y avise hoy al colegio y al proveedor."),
	DIRECTA_EN_ESPERA("cc_directa_en_espera_v2", "Un cambio espera 24 horas", 4, false,
			"{{1}}: {{2}} pidió {{3}}. Se aplica el {{4}} si nadie lo frena. Si no lo reconoce, frénelo desde el sistema o escriba hoy al proveedor."),
	DIRECTA_FRENADA("cc_directa_frenada_v2", "Se frenó un cambio en espera", 4, false,
			"{{1}}: {{2}} frenó {{3}}. Motivo: {{4}}. No se aplica: se puede volver a pedir."),
	CONCILIACION_BLOQUEADA("cc_conciliacion_bloqueada_v2", "Alerta: confirmación rechazada", 3, false,
			"{{1}}: {{2}} intentó confirmar {{3}} después de cambiar la cuenta del colegio. Se rechazó: la confirma otra persona. Revíselo hoy."),
	// Sprint 11, tanda 2 (secciones 3.9 a 3.11): la exportación, la conservación y el acta de supresión.
	EXPORTACION_LISTA("cc_exportacion_lista_v2", "Los datos del colegio están listos", 4, false,
			"{{1}}: la exportación de los datos del colegio ({{2}}) está lista. Huella del ZIP: {{3}}. Huella del manifiesto: {{4}}. Guárdelas para comprobar el archivo."),
	EXPORTACION_ENLACE("cc_exportacion_enlace_v2", "Descargue los datos del colegio", 2, false,
			"{{1}}: los datos del colegio están listos para descargar hasta el {{2}}. Abra el enlace de este correo, escriba su DNI y el código que le llegará por WhatsApp."),
	CODIGO_EXPORTACION("cc_codigo_exportacion_v2", "Su código para descargar los datos del colegio", 3, false,
			"{{1}}: código {{2}} para descargar los datos del colegio. Vence a las {{3}}. Si usted no lo pidió, no lo comparta y escriba hoy al proveedor."),
	EXPORTACION_MOVIMIENTO("cc_exportacion_movimiento_v2", "Movimiento en la exportación de los datos", 3, false,
			"{{1}}: en la exportación de los datos del colegio: {{2}} ({{3}}). Si no lo reconoce, escriba hoy al proveedor."),
	EXPORTACION_FALLIDA("cc_exportacion_fallida_v2", "Alerta: la exportación de los datos falló", 2, false,
			"{{1}}: la exportación de los datos del colegio no se entregó: {{2}}. Se revisa hoy y se genera otra."),
	CONSERVACION("cc_conservacion_v2", "Conservación de los datos del colegio", 3, false,
			"{{1}}: {{2}}: {{3}}. Si no lo reconoce, escriba hoy al proveedor."),
	ACTA_SUPRESION("cc_acta_supresion_v2", "Acta de supresión de los datos del colegio", 3, false,
			"{{1}}: {{2}} el acta de supresión de los datos del colegio ({{3}}).");

	/** Sprint 10, tanda 3: las plantillas de libretas (botón a {@code /familia/libretas}). */
	public boolean deLibretas() {
		return this == LIBRETA || this == LIBRETA_CORREGIDA;
	}

	/** Sprint 9, tanda 2: las plantillas de asistencia (botón a {@code /familia/asistencia}). */
	public boolean deAsistencia() {
		return this == ASISTENCIA || this == ASISTENCIA_CORREGIDA;
	}

	/** Sprint 9, tanda 3: las plantillas de comunicados (botón a {@code /familia/comunicados}). */
	public boolean deComunicados() {
		return this == COMUNICADOS || this == COMUNICADO_URGENTE;
	}

	private static final Pattern MARCADOR = Pattern.compile("\\{\\{(\\d+)}}");

	/** El marcador con el que empieza toda plantilla: el nombre del colegio. */
	public static final String MARCADOR_COLEGIO = "{{1}}";

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

	/** Cuántos parámetros lleva, contando el primero (el nombre del colegio). */
	public int parametros() {
		return parametros;
	}

	/** Los parámetros propios de la plantilla, sin el nombre del colegio que antepone {@code CreadorMensajes}. */
	public int parametrosPropios() {
		return parametros - 1;
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
		// QA-S5-5: UNA sola pasada; un «{{n}}» dentro de un parámetro (el motivo lo escribe una persona) queda tal cual.
		Matcher marcador = MARCADOR.matcher(texto);
		StringBuilder resultado = new StringBuilder();
		while (marcador.find()) {
			int indice = Integer.parseInt(marcador.group(1)) - 1;
			marcador.appendReplacement(resultado, Matcher.quoteReplacement(valores.get(indice)));
		}
		marcador.appendTail(resultado);
		return resultado.toString();
	}
}
