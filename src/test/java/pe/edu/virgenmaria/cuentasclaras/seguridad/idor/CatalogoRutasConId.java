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
		CIERRE_MENSUAL, LOTE_SALDO, LINEA_SALDO, LOTE_RECAUDACION, LINEA_RECAUDACION, USUARIO, VERSION, PUBLICO
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
