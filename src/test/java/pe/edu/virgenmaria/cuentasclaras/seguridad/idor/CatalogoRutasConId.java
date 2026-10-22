package pe.edu.virgenmaria.cuentasclaras.seguridad.idor;

import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RutasDeLaAplicacion.Ruta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sprint 7, tanda 3 (A01 de OWASP; E19 a E21): TODA ruta con una variable en la ruta, un parámetro {@code Long} o un campo
 * {@code Long} en su formulario está aquí, con el recurso que nombra cada uno. {@code RutasIdorTest} pide cada recurso de
 * otra familia y de otro colegio con cada rol permitido; una ruta nueva sin clasificar hace fallar la prueba.
 */
final class CatalogoRutasConId {

	/** Qué nombra un id. {@code PUBLICO}: rutas sin sesión con su token o su firma; {@code VERSION}: bloqueo optimista. */
	enum Recurso {
		ALUMNO, FAMILIA, APODERADO, MATRICULA, ANIO, SECCION, PLAN, CUOTA, PAGO, COMPROBANTE, CAJA, DEPOSITO, ANULACION,
		SOLICITUD, AVISO, RENOVACION, ORDEN, REFERENCIA_ORDEN, FERIADO, MENSAJE, EXTRACTO, CUENTA, MOVIMIENTO, PARTIDA,
		CIERRE_MENSUAL, LOTE_SALDO, LINEA_SALDO, LOTE_RECAUDACION, LINEA_RECAUDACION, USUARIO, VERSION, PUBLICO,
		/** Sprint 8, tanda 1: un colegio, en las rutas del operador de la plataforma (ve la ficha de cualquiera: es su función). */
		COLEGIO,
		/** Sprint 8, tanda 2: un conector del colegio (OSE, pasarela, cuenta bancaria o recaudación). */
		CONECTOR,
		/** Sprint 8, tanda 2: el tipo de conector que se propone (no es un registro: la propuesta es del colegio en sesión). */
		TIPO_CONECTOR,
		/** Correcciones del sprint 8 (S8-B4): el logo con que se emitió un comprobante. */
		LOGO,
		/** Correcciones del sprint 8 (S8-A1): el cambio del contacto del titular (rutas del operador). */
		CAMBIO_CONTACTO,
		/** Correcciones del sprint 8 (S8-A2): el cambio del correo de control que aprueba la Promotoría. */
		CAMBIO_CORREO,
		/** Sprint 9, tanda 1: una asignación docente (Docentes y secciones). */
		ASIGNACION,
		/** Sprint 9, tanda 2: una marca de asistencia (corregir, pedir corrección, justificar, revertir). */
		MARCA,
		/** Sprint 9, tanda 3: un comunicado (detalle, fe de erratas, retiro, PDF y el portal de la familia). */
		COMUNICADO
	}

	static final Map<String, Map<String, Recurso>> CATALOGO = new LinkedHashMap<>();

	static {
		// Públicas con token o firma (sin sesión): el colegio va en la ruta y el token decide.
		r("GET /activar/{colegio:\\d+}/{token:[A-Za-z0-9_-]+}", "colegio", Recurso.PUBLICO, "token", Recurso.PUBLICO);
		r("POST /activar/{colegio:\\d+}/{token:[A-Za-z0-9_-]+}", "colegio", Recurso.PUBLICO, "token", Recurso.PUBLICO);
		r("GET /verificar/{colegio:\\d+}/{token:[A-Za-z0-9_-]+}", "colegio", Recurso.PUBLICO, "token", Recurso.PUBLICO);
		r("POST /verificar/{colegio:\\d+}/{token:[A-Za-z0-9_-]+}", "colegio", Recurso.PUBLICO, "token", Recurso.PUBLICO);
		r("GET /webhooks/whatsapp/{colegio:\\d{1,12}}", "colegio", Recurso.PUBLICO);
		r("POST /webhooks/whatsapp/{colegio:\\d{1,12}}", "colegio", Recurso.PUBLICO);
		r("POST /webhooks/pasarela/{proveedor:[A-Za-z]{3,20}}/{colegio:\\d{1,12}}", "proveedor", Recurso.PUBLICO,
				"colegio", Recurso.PUBLICO);
		// Sprint 8, tanda 2 (E15): el aviso de la pasarela de UN conector, por su clave pública opaca y su firma.
		r("POST /webhooks/pasarela/{clavePublica:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}}",
				"clavePublica", Recurso.PUBLICO);

		// Alumnos y familias.
		r("GET /alumnos", "anio", Recurso.ANIO, "seccion", Recurso.SECCION);
		r("GET /alumnos/nuevo", "familia", Recurso.FAMILIA);
		r("POST /alumnos/nuevo", "familia", Recurso.FAMILIA, "apoderadoExistenteId", Recurso.APODERADO, "seccionId",
				Recurso.SECCION);
		r("GET /alumnos/{id:\\d+}", "id", Recurso.ALUMNO);
		r("GET /alumnos/{id:\\d+}/editar", "id", Recurso.ALUMNO);
		r("POST /alumnos/{id:\\d+}/editar", "id", Recurso.ALUMNO);
		r("POST /alumnos/{id:\\d+}/matricula", "id", Recurso.ALUMNO, "seccionId", Recurso.SECCION);
		r("POST /alumnos/{id:\\d+}/responsable", "id", Recurso.ALUMNO, "apoderadoId", Recurso.APODERADO);
		r("POST /alumnos/{id:\\d+}/retirar", "id", Recurso.ALUMNO);
		r("POST /alumnos/matriculas/{id:\\d+}/seccion", "id", Recurso.MATRICULA, "alumnoId", Recurso.ALUMNO, "seccionId",
				Recurso.SECCION);
		r("GET /alumnos/{id:\\d+}/cronograma", "id", Recurso.ALUMNO);
		r("GET /alumnos/{id:\\d+}/estado-cuenta", "id", Recurso.ALUMNO);
		r("GET /alumnos/familias/{id:\\d+}", "id", Recurso.FAMILIA);
		r("POST /alumnos/familias/{id:\\d+}/apoderados", "id", Recurso.FAMILIA);
		r("POST /alumnos/familias/{id:\\d+}/nombre", "id", Recurso.FAMILIA);
		r("GET /alumnos/apoderados/{id:\\d+}", "id", Recurso.APODERADO);
		r("POST /alumnos/apoderados/{id:\\d+}", "id", Recurso.APODERADO);
		r("POST /alumnos/apoderados/{id:\\d+}/acceso", "id", Recurso.APODERADO);
		r("POST /alumnos/apoderados/{id:\\d+}/acceso/quitar", "id", Recurso.APODERADO);
		r("POST /alumnos/apoderados/{id:\\d+}/acceso/restablecer", "id", Recurso.APODERADO, "familiaId",
				Recurso.FAMILIA);
		r("POST /alumnos/apoderados/{id:\\d+}/desactivar", "id", Recurso.APODERADO, "familiaId", Recurso.FAMILIA);
		r("POST /alumnos/apoderados/{id:\\d+}/facturacion", "id", Recurso.APODERADO);
		r("POST /alumnos/apoderados/{id:\\d+}/verificacion", "id", Recurso.APODERADO, "familiaId", Recurso.FAMILIA);
		r("POST /alumnos/importar", "anioId", Recurso.ANIO);
		r("GET /alumnos/comprobantes/{id:\\d+}", "id", Recurso.COMPROBANTE, "alumno", Recurso.ALUMNO);
		r("GET /alumnos/pagos/{id:\\d+}/correccion", "id", Recurso.PAGO, "familia", Recurso.FAMILIA);
		r("POST /alumnos/pagos/{id:\\d+}/correccion", "id", Recurso.PAGO, "familiaId", Recurso.FAMILIA, "cuotaIds",
				Recurso.CUOTA);
		r("POST /alumnos/pagos/{id:\\d+}/devolucion", "id", Recurso.PAGO, "alumno", Recurso.ALUMNO);

		// Aprobaciones, avisos y usuarios.
		r("GET /aprobaciones/{id:\\d+}", "id", Recurso.SOLICITUD);
		r("POST /aprobaciones/{id:\\d+}/aprobar", "id", Recurso.SOLICITUD);
		r("POST /aprobaciones/{id:\\d+}/rechazar", "id", Recurso.SOLICITUD);
		r("GET /aprobaciones/cajas/{id:\\d+}", "id", Recurso.CAJA);
		r("POST /avisos-familias/{id:\\d+}/atender", "id", Recurso.AVISO);
		r("GET /usuarios/{id}", "id", Recurso.USUARIO);
		r("POST /usuarios/{id}/desactivar", "id", Recurso.USUARIO);
		r("POST /usuarios/{id}/desbloquear", "id", Recurso.USUARIO);
		r("POST /usuarios/{id}/reactivar", "id", Recurso.USUARIO);
		r("POST /usuarios/{id}/restablecer-clave", "id", Recurso.USUARIO);
		r("POST /usuarios/{id}/roles", "id", Recurso.USUARIO);
		r("POST /usuarios/{id:\\d+}/contacto", "id", Recurso.USUARIO);
		r("GET /auditoria/accesos", "usuarioId", Recurso.USUARIO);

		// Caja, comprobantes y verificación bancaria.
		r("GET /caja/familias/{id:\\d+}", "id", Recurso.FAMILIA);
		r("POST /caja/familias/{id:\\d+}/revisar", "id", Recurso.FAMILIA, "cuotaIds", Recurso.CUOTA);
		r("POST /caja/pagos", "familiaId", Recurso.FAMILIA, "cuotaIds", Recurso.CUOTA, "receptorApoderadoId",
				Recurso.APODERADO);
		r("GET /caja/pagos/{id:\\d+}", "id", Recurso.PAGO);
		r("GET /caja/pagos/{id:\\d+}/comprobante", "id", Recurso.PAGO);
		r("GET /caja/pagos/{id:\\d+}/correccion", "id", Recurso.PAGO, "familia", Recurso.FAMILIA);
		r("POST /caja/pagos/{id:\\d+}/correccion", "id", Recurso.PAGO, "familiaId", Recurso.FAMILIA, "cuotaIds",
				Recurso.CUOTA);
		r("POST /caja/pagos/{id:\\d+}/devolucion", "id", Recurso.PAGO);
		r("POST /caja/cierre/deposito", "cajaId", Recurso.CAJA);
		r("GET /comprobantes/{id:\\d+}", "id", Recurso.COMPROBANTE);
		r("POST /comprobantes/{id:\\d+}/reemitir", "id", Recurso.COMPROBANTE);
		r("POST /comprobantes/{id:\\d+}/reintentar", "id", Recurso.COMPROBANTE);
		r("POST /conciliacion/pagos/{id:\\d+}", "id", Recurso.PAGO);
		r("POST /conciliacion/depositos/{id:\\d+}", "id", Recurso.DEPOSITO);
		r("POST /conciliacion/devoluciones/{id:\\d+}", "id", Recurso.ANULACION);
		r("POST /conciliacion/devoluciones/{id:\\d+}/pasarela", "id", Recurso.ANULACION);

		// Pensiones, descuentos y saldo inicial.
		r("GET /pensiones", "anio", Recurso.ANIO);
		r("GET /pensiones/cronogramas", "anio", Recurso.ANIO);
		r("POST /pensiones/cronogramas/generar", "anio", Recurso.ANIO);
		r("GET /pensiones/planes/nuevo", "anio", Recurso.ANIO);
		r("POST /pensiones/planes/nuevo", "anio", Recurso.ANIO);
		r("GET /pensiones/planes/{id:\\d+}", "id", Recurso.PLAN);
		r("GET /pensiones/planes/{id:\\d+}/editar", "id", Recurso.PLAN);
		r("POST /pensiones/planes/{id:\\d+}", "id", Recurso.PLAN);
		r("POST /pensiones/planes/{id:\\d+}/aprobar", "id", Recurso.PLAN, "version", Recurso.VERSION);
		r("POST /pensiones/planes/{id:\\d+}/descartar", "id", Recurso.PLAN);
		r("POST /pensiones/planes/{id:\\d+}/devolver", "id", Recurso.PLAN, "version", Recurso.VERSION);
		r("POST /pensiones/planes/{id:\\d+}/enviar", "id", Recurso.PLAN);
		r("POST /pensiones/planes/{id:\\d+}/nueva-version", "id", Recurso.PLAN);
		r("POST /pensiones/saldo-inicial", "anioId", Recurso.ANIO);
		r("GET /pensiones/saldo-inicial/{id:\\d+}", "id", Recurso.LOTE_SALDO);
		r("POST /pensiones/saldo-inicial/{id:\\d+}/confirmar", "id", Recurso.LOTE_SALDO, "version", Recurso.VERSION);
		r("POST /pensiones/saldo-inicial/{id:\\d+}/descartar", "id", Recurso.LOTE_SALDO);
		r("POST /pensiones/saldo-inicial/{id:\\d+}/devolver", "id", Recurso.LOTE_SALDO, "version", Recurso.VERSION);
		r("POST /pensiones/saldo-inicial/{id:\\d+}/enviar", "id", Recurso.LOTE_SALDO);
		r("POST /pensiones/saldo-inicial/{id:\\d+}/lineas", "id", Recurso.LOTE_SALDO);
		r("POST /pensiones/saldo-inicial/{id:\\d+}/lineas/{lineaId:\\d+}/quitar", "id", Recurso.LOTE_SALDO, "lineaId",
				Recurso.LINEA_SALDO);
		r("POST /descuentos", "alumnoId", Recurso.ALUMNO, "cuotaIds", Recurso.CUOTA);
		r("POST /descuentos/revisar", "alumnoId", Recurso.ALUMNO, "cuotaIds", Recurso.CUOTA);

		// Colegio, feriados, mensajes y matrícula 2027.
		r("GET /colegio/anios/{id:\\d+}", "id", Recurso.ANIO);
		r("POST /colegio/anios/{id:\\d+}/secciones", "id", Recurso.ANIO);
		r("POST /colegio/secciones/{id:\\d+}/desactivar", "id", Recurso.SECCION, "anioId", Recurso.ANIO);
		r("POST /feriados/{id:\\d+}/anular", "id", Recurso.FERIADO);
		r("POST /feriados/{id:\\d+}/aprobar", "id", Recurso.FERIADO);
		r("POST /mensajes/{id:\\d+}/reintentar", "id", Recurso.MENSAJE);
		r("POST /matricula-2027/abrir", "anioId", Recurso.ANIO);
		r("POST /matricula-2027/{id:\\d+}/destino", "id", Recurso.RENOVACION, "seccionId", Recurso.SECCION);
		r("POST /matricula-2027/{id:\\d+}/presencial", "id", Recurso.RENOVACION);

		// Conciliación.
		r("GET /conciliacion/extractos/{id:\\d+}", "id", Recurso.EXTRACTO);
		r("POST /conciliacion/extractos/{id:\\d+}/descartar", "id", Recurso.EXTRACTO);
		r("GET /conciliacion/cuentas/{id:\\d+}/confirmar", "id", Recurso.CUENTA);
		r("POST /conciliacion/cuentas/{id:\\d+}/confirmar", "id", Recurso.CUENTA, "extracto", Recurso.EXTRACTO,
				"version", Recurso.VERSION);
		r("POST /conciliacion/cuentas/{id:\\d+}/desactivar", "id", Recurso.CUENTA);
		r("POST /conciliacion/movimientos/{id:\\d+}/emparejar", "id", Recurso.MOVIMIENTO);
		r("POST /conciliacion/movimientos/{id:\\d+}/explicar", "id", Recurso.MOVIMIENTO);
		r("POST /conciliacion/partidas/{id:\\d+}/confirmar", "id", Recurso.PARTIDA);
		r("POST /conciliacion/partidas/{id:\\d+}/descartar", "id", Recurso.PARTIDA);
		r("GET /conciliacion/cierres-mensuales/{id:\\d+}", "id", Recurso.CIERRE_MENSUAL);
		r("POST /conciliacion/cierres-mensuales/{id:\\d+}", "id", Recurso.CIERRE_MENSUAL, "version", Recurso.VERSION);

		// Pagos en línea y recaudación.
		r("GET /pagos-en-linea/{id:\\d+}", "id", Recurso.ORDEN);
		r("POST /pagos-en-linea/{id:\\d+}/aplicar", "id", Recurso.ORDEN, "familiaId", Recurso.FAMILIA, "cuotaIds",
				Recurso.CUOTA);
		r("POST /pagos-en-linea/{id:\\d+}/devolucion", "id", Recurso.ORDEN);
		r("POST /pagos-en-linea/{id:\\d+}/devolver", "id", Recurso.ORDEN);
		r("GET /recaudacion/lotes/{id:\\d+}", "id", Recurso.LOTE_RECAUDACION);
		r("GET /recaudacion/lotes/{id:\\d+}/archivo", "id", Recurso.LOTE_RECAUDACION);
		r("GET /recaudacion/lotes/{id:\\d+}/confirmar", "id", Recurso.LOTE_RECAUDACION);
		r("POST /recaudacion/lotes/{id:\\d+}/confirmar", "id", Recurso.LOTE_RECAUDACION, "version", Recurso.VERSION);
		r("POST /recaudacion/lotes/{id:\\d+}/descartar", "id", Recurso.LOTE_RECAUDACION);
		r("GET /recaudacion/lineas/{id:\\d+}", "id", Recurso.LINEA_RECAUDACION);
		r("POST /recaudacion/lineas/{id:\\d+}/aplicar", "id", Recurso.LINEA_RECAUDACION, "familiaId", Recurso.FAMILIA,
				"cuotaIds", Recurso.CUOTA);
		r("POST /recaudacion/lineas/{id:\\d+}/devolucion", "id", Recurso.LINEA_RECAUDACION);
		r("POST /recaudacion/lineas/{id:\\d+}/devolver", "id", Recurso.LINEA_RECAUDACION);

		// Panel.
		r("GET /panel/reportes/morosidad", "anio", Recurso.ANIO);
		r("POST /panel/reportes/morosidad.xlsx", "anio", Recurso.ANIO);
		r("POST /panel/llamadas/{familiaId:\\d+}", "familiaId", Recurso.FAMILIA);

		// Portal de familias (la familia sale de la sesión; los ids de otra familia: 404).
		r("GET /familia/comprobantes/{id:\\d+}", "id", Recurso.COMPROBANTE);
		r("GET /familia/matricula/{id:\\d+}", "id", Recurso.RENOVACION);
		r("POST /familia/matricula/{id:\\d+}", "id", Recurso.RENOVACION);
		r("GET /familia/pagos/{referencia:[0-9a-f-]{36}}", "referencia", Recurso.REFERENCIA_ORDEN);
		r("GET /familia/pasarela-simulada/{referencia:[0-9a-f-]{36}}", "referencia", Recurso.REFERENCIA_ORDEN);
		r("POST /familia/pasarela-simulada/{referencia:[0-9a-f-]{36}}/{accion:[A-Z_]{4,20}}", "referencia",
				Recurso.REFERENCIA_ORDEN, "accion", Recurso.PUBLICO);
		r("POST /familia/algo-no-cuadra", "pagoId", Recurso.PAGO, "cuotaId", Recurso.CUOTA);
		r("POST /familia/pagar", "cuotaIds", Recurso.CUOTA);
		r("POST /familia/pagar/revisar", "cuotaIds", Recurso.CUOTA);

		// Sprint 8, tanda 1: la plataforma (solo el operador; ve la ficha y la lista de cualquier colegio, nunca sus datos:
		// lo prueba OperadorSinDatosTest).
		r("GET /plataforma/colegios/{id:\\d+}", "id", Recurso.COLEGIO);
		r("POST /plataforma/colegios/{id:\\d+}/ficha", "id", Recurso.COLEGIO);
		r("POST /plataforma/colegios/{id:\\d+}/completar", "id", Recurso.COLEGIO);
		r("POST /plataforma/colegios/{id:\\d+}/titular", "id", Recurso.COLEGIO);
		// Correcciones del sprint 8 (S8-A1): reenviar el enlace y resolver el cambio del contacto del titular (operador).
		r("POST /plataforma/colegios/{id:\\d+}/titular/reenviar", "id", Recurso.COLEGIO);
		r("POST /plataforma/colegios/{id:\\d+}/titular/{cambio:\\d+}/aplicar", "id", Recurso.COLEGIO, "cambio",
				Recurso.CAMBIO_CONTACTO);
		r("POST /plataforma/colegios/{id:\\d+}/titular/{cambio:\\d+}/rechazar", "id", Recurso.COLEGIO, "cambio",
				Recurso.CAMBIO_CONTACTO);
		// Correcciones del sprint 8 (S8-A2 y S8-B4): el correo de control que aprueba la Promotoría y el logo emitido.
		r("POST /puesta-en-marcha/correo-control/{id}/aprobar", "id", Recurso.CAMBIO_CORREO);
		r("POST /puesta-en-marcha/correo-control/{id}/rechazar", "id", Recurso.CAMBIO_CORREO);
		r("GET /marca/logo/{id:[0-9]+}", "id", Recurso.LOGO);
		r("POST /plataforma/colegios/{id:\\d+}/correo-control", "id", Recurso.COLEGIO);
		// Sprint 8, tanda 2: los conectores del colegio (proponer un tipo y probar uno propuesto).
		r("POST /conectores/{tipo:[a-z-]{3,20}}", "tipo", Recurso.TIPO_CONECTOR);
		r("POST /conectores/{id:\\d{1,12}}/probar", "id", Recurso.CONECTOR);
		// Sprint 9, tanda 1: la lista de una sección (Dirección o un docente con asignación vigente) y Docentes y secciones.
		r("GET /academico/secciones/{id:\\d+}", "id", Recurso.SECCION);
		r("GET /academico/asignaciones", "anio", Recurso.ANIO);
		r("POST /academico/asignaciones", "anio", Recurso.ANIO, "seccionId", Recurso.SECCION, "docenteId", Recurso.USUARIO);
		r("POST /academico/asignaciones/copiar-anio-anterior", "anioId", Recurso.ANIO);
		r("POST /academico/asignaciones/{id:\\d+}/terminar", "id", Recurso.ASIGNACION, "anio", Recurso.ANIO);
		// Sprint 9, tanda 2: la asistencia (hoja, correcciones, registro tardío, reversión y reporte con ?seccion=, A11),
		// la justificación de la familia y el consentimiento desde la ficha (?apoderado=).
		r("GET /academico/asistencia/{seccionId:\\d+}/hoy", "seccionId", Recurso.SECCION);
		r("POST /academico/asistencia/{seccionId:\\d+}/hoy", "seccionId", Recurso.SECCION);
		r("GET /academico/asistencia/{seccionId:\\d+}/tardio/{solicitudId:\\d+}", "seccionId", Recurso.SECCION, "solicitudId", Recurso.SOLICITUD);
		r("POST /academico/asistencia/{seccionId:\\d+}/tardio/{solicitudId:\\d+}", "seccionId", Recurso.SECCION, "solicitudId", Recurso.SOLICITUD);
		r("POST /academico/asistencia/marcas/{id:\\d+}/corregir-hoy", "id", Recurso.MARCA, "seccion", Recurso.SECCION);
		r("POST /academico/asistencia/marcas/{id:\\d+}/pedir-correccion", "id", Recurso.MARCA, "seccion", Recurso.SECCION);
		r("POST /academico/asistencia/{seccionId:\\d+}/pedir-registro-tardio", "seccionId", Recurso.SECCION);
		r("POST /academico/asistencia/marcas/{id:\\d+}/revertir-justificacion", "id", Recurso.MARCA);
		r("GET /academico/asistencia/reporte", "seccion", Recurso.SECCION);
		r("POST /familia/asistencia/marcas/{id:\\d+}/justificar", "id", Recurso.MARCA);
		r("GET /alumnos/consentimientos", "apoderado", Recurso.APODERADO);
		r("POST /alumnos/consentimientos", "apoderadoId", Recurso.APODERADO);
		// Sprint 9, tanda 3: los comunicados del personal (de otro colegio u otro docente: 404), los del portal (de otra
		// familia: 404), la nómina del SIAGIE por sección (?seccion=, A11) y los datos del SIAGIE de un alumno.
		r("POST /comunicados/nuevo", "seccionId", Recurso.SECCION);
		r("GET /comunicados/{id:\\d+}", "id", Recurso.COMUNICADO);
		r("GET /comunicados/{id:\\d+}/fe-de-erratas", "id", Recurso.COMUNICADO);
		r("POST /comunicados/{id:\\d+}/fe-de-erratas", "id", Recurso.COMUNICADO, "seccionId", Recurso.SECCION);
		r("POST /comunicados/{id:\\d+}/retirar", "id", Recurso.COMUNICADO);
		r("GET /comunicados/{id:\\d+}/adjunto", "id", Recurso.COMUNICADO);
		r("GET /familia/comunicados/{id:\\d+}", "id", Recurso.COMUNICADO);
		r("POST /familia/comunicados/{id:\\d+}/confirmar", "id", Recurso.COMUNICADO);
		r("GET /familia/comunicados/{id:\\d+}/adjunto", "id", Recurso.COMUNICADO);
		r("GET /academico/siagie/nomina", "seccion", Recurso.SECCION);
		r("POST /alumnos/{id:\\d+}/datos-siagie", "id", Recurso.ALUMNO);
	}

	private CatalogoRutasConId() {
	}

	private static void r(String clave, Object... pares) {
		Map<String, Recurso> nombres = new LinkedHashMap<>();
		for (int i = 0; i < pares.length; i += 2) {
			nombres.put((String) pares[i], (Recurso) pares[i + 1]);
		}
		if (CATALOGO.put(clave, nombres) != null) {
			throw new IllegalStateException("Ruta repetida en el catálogo: " + clave);
		}
	}

	/** Todo lo que puede nombrar un registro en la ruta: variables, parámetros Long y campos Long del formulario. */
	static Set<String> nombresConId(Ruta ruta) {
		Set<String> nombres = new LinkedHashSet<>(ruta.variables());
		nombres.addAll(ruta.parametrosConId());
		nombres.addAll(ruta.camposConId());
		return nombres;
	}

	/** Las rutas con algún id que el catálogo no clasifica (o con un id sin su recurso). */
	static List<String> sinClasificar(List<Ruta> rutas) {
		List<String> faltan = new ArrayList<>();
		for (Ruta ruta : rutas) {
			Set<String> nombres = nombresConId(ruta);
			if (nombres.isEmpty()) {
				continue;
			}
			Map<String, Recurso> clasificada = CATALOGO.get(ruta.clave());
			if (clasificada == null) {
				faltan.add(ruta.clave() + " " + nombres);
				continue;
			}
			nombres.stream().filter(n -> !clasificada.containsKey(n))
					.forEach(n -> faltan.add(ruta.clave() + " sin clasificar: " + n));
		}
		return faltan;
	}
}
