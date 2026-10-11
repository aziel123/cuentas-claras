package pe.edu.virgenmaria.cuentasclaras.conectores.service;

import pe.edu.virgenmaria.cuentasclaras.comun.config.VerificadorConfiguracion;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.ConectorVigente;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.ConfiguracionConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.PropuestaConectorRequest;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ModoConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ProveedorConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.TipoConector;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Sprint 8, tanda 2 (sección 3.6): arma y valida la configuración y los secretos de una propuesta, por tipo. Sin estado:
 * lo usa {@link PropuestaConector}. Los mensajes son para la persona que llena el formulario (nunca repiten un secreto).
 */
final class ValidacionConector {

	/** Dominios a los que puede apuntar el OSE (lista cerrada, como el entorno: nadie apunta el OSE a un servidor propio). */
	static final String DOMINIOS_NUBEFACT = "api.nubefact.com";

	static final int MINIMO_SECRETO = 16;

	private static final Set<String> MEDIOS = Set.of("YAPE", "PLIN", "TARJETA");

	private ValidacionConector() {
	}

	/** La configuración (sin secretos) de la propuesta. {@code rucColegio}: el RUC del colegio (el OSE emite con él). */
	static ConfiguracionConector configuracion(TipoConector tipo, PropuestaConectorRequest p, String rucColegio) {
		if (!tipo.proveedores().contains(p.proveedor())) {
			throw new ReglaNegocioException(p.proveedor().etiqueta() + " no es un proveedor de " + tipo.etiqueta() + ".");
		}
		if ((p.proveedor() == ProveedorConector.SIMULADO) != (p.modo() == ModoConector.SIMULADO)) {
			throw new ReglaNegocioException("El modo simulado va solo con el proveedor simulado (y el proveedor simulado, "
					+ "solo en modo simulado).");
		}
		if (!tipo.conSecreto() && p.modo() != ModoConector.REAL) {
			throw new ReglaNegocioException(tipo.etiqueta() + " es siempre real: el dinero entra de verdad.");
		}
		return switch (tipo) {
			case OSE -> ose(p, rucColegio);
			case PASARELA -> pasarela(p);
			case CUENTA_BANCARIA -> cuentaBancaria(p);
			case RECAUDACION -> recaudacion(p);
		};
	}

	/** Los secretos con nombre de la propuesta (vacío si el tipo o el proveedor no los tienen). */
	static Map<String, String> secretos(TipoConector tipo, PropuestaConectorRequest p) {
		Map<String, String> secretos = new LinkedHashMap<>();
		switch (tipo) {
			case OSE -> {
				if (p.proveedor() != ProveedorConector.SIMULADO) {
					secretos.put(ConectorVigente.TOKEN, exigirSecreto(p.secreto(), "el token del OSE"));
				}
			}
			case PASARELA -> {
				if (p.proveedor() != ProveedorConector.SIMULADO) {
					secretos.put(ConectorVigente.LLAVE_SECRETA, exigirSecreto(p.secreto(), "la llave secreta de la pasarela"));
				}
				secretos.put(ConectorVigente.SECRETO_AVISO, exigirSecreto(p.secretoAviso(),
						"el secreto con que la pasarela firma sus avisos"));
			}
			case CUENTA_BANCARIA, RECAUDACION -> {
				if (!vacio(p.secreto()) || !vacio(p.secretoAviso())) {
					throw new ReglaNegocioException(tipo.etiqueta() + " no guarda secretos: deja vacíos esos campos.");
				}
			}
		}
		return secretos;
	}

	/** El secreto principal (del que se guarda la pista). */
	static String principal(Map<String, String> secretos) {
		if (secretos.containsKey(ConectorVigente.TOKEN)) {
			return secretos.get(ConectorVigente.TOKEN);
		}
		if (secretos.containsKey(ConectorVigente.LLAVE_SECRETA)) {
			return secretos.get(ConectorVigente.LLAVE_SECRETA);
		}
		return secretos.get(ConectorVigente.SECRETO_AVISO);
	}

	private static String exigirSecreto(String secreto, String que) {
		if (vacio(secreto)) {
			throw new ReglaNegocioException("Escribe " + que + " completo: nadie lo vuelve a ver, por eso se pide entero en "
					+ "cada cambio.");
		}
		String limpio = secreto.strip();
		if (limpio.length() < MINIMO_SECRETO || limpio.length() > 400 || !limpio.matches("[\\x21-\\x7E]+")) {
			throw new ReglaNegocioException("Revisa " + que + ": tiene de " + MINIMO_SECRETO + " a 400 caracteres, sin "
					+ "espacios ni tildes.");
		}
		return limpio;
	}

	private static ConfiguracionConector.Ose ose(PropuestaConectorRequest p, String rucColegio) {
		if (rucColegio == null || rucColegio.isBlank()) {
			throw new ReglaNegocioException("Primero completa el RUC del colegio (Marca del colegio): el comprobante se "
					+ "emite con él.");
		}
		String ruta = null;
		if (p.proveedor() == ProveedorConector.NUBEFACT) {
			ruta = vacio(p.ruta()) ? null : p.ruta().strip();
			if (!VerificadorConfiguracion.rutaPermitida(ruta, DOMINIOS_NUBEFACT)) {
				throw new ReglaNegocioException("La ruta de Nubefact debe ser https y de " + DOMINIOS_NUBEFACT + " (la que "
						+ "te dio Nubefact para tu RUC).");
			}
		}
		String boleta = serie(p.serieBoleta(), 'B', "la serie de boletas");
		String factura = serie(p.serieFactura(), 'F', "la serie de facturas");
		String notaBoleta = serie(p.serieNotaBoleta(), 'B', "la serie de notas de crédito de boletas");
		String notaFactura = serie(p.serieNotaFactura(), 'F', "la serie de notas de crédito de facturas");
		if (new LinkedHashSet<>(List.of(boleta, factura, notaBoleta, notaFactura)).size() != 4) {
			throw new ReglaNegocioException("Las cuatro series deben ser distintas.");
		}
		String afectacion = vacio(p.afectacionIgv()) ? "INAFECTO" : p.afectacionIgv().strip().toUpperCase(Locale.ROOT);
		if (!afectacion.equals("INAFECTO") && !afectacion.equals("EXONERADO")) {
			throw new ReglaNegocioException("La afectación al IGV es INAFECTO o EXONERADO (lo confirma tu contador).");
		}
		return new ConfiguracionConector.Ose(ruta, rucColegio, boleta, factura, notaBoleta, notaFactura, afectacion);
	}

	private static String serie(String serie, char letra, String que) {
		String limpia = vacio(serie) ? "" : serie.strip().toUpperCase(Locale.ROOT);
		if (!limpia.matches("[BF][A-Z0-9]{3}") || limpia.charAt(0) != letra) {
			throw new ReglaNegocioException("Revisa " + que + ": 4 caracteres que empiezan con " + letra
					+ " (por ejemplo " + letra + "002).");
		}
		return limpia;
	}

	private static ConfiguracionConector.Pasarela pasarela(PropuestaConectorRequest p) {
		String llave = vacio(p.llavePublica()) ? null : p.llavePublica().strip();
		if (p.proveedor() != ProveedorConector.SIMULADO && (llave == null || !llave.matches("[\\x21-\\x7E]{8,120}"))) {
			throw new ReglaNegocioException("Escribe la llave pública de la pasarela (sin espacios).");
		}
		List<String> medios = new ArrayList<>();
		for (String medio : p.medios() == null ? List.<String>of() : p.medios()) {
			String m = medio == null ? "" : medio.strip().toUpperCase(Locale.ROOT);
			if (!MEDIOS.contains(m)) {
				throw new ReglaNegocioException("Los medios del pago en línea son Yape, Plin y tarjeta.");
			}
			if (!medios.contains(m)) {
				medios.add(m);
			}
		}
		if (medios.isEmpty()) {
			throw new ReglaNegocioException("Elige al menos un medio de pago en línea.");
		}
		BigDecimal maximo;
		try {
			maximo = new BigDecimal(vacio(p.montoMaximo()) ? "" : p.montoMaximo().strip().replace(",", ""))
					.setScale(2, RoundingMode.HALF_UP);
		}
		catch (NumberFormatException | ArithmeticException e) {
			throw new ReglaNegocioException("Escribe el monto máximo por pago en línea (por ejemplo 5000.00).");
		}
		if (maximo.signum() <= 0 || maximo.compareTo(new BigDecimal("99999.99")) > 0) {
			throw new ReglaNegocioException("El monto máximo por pago en línea va de S/ 0.01 a S/ 99,999.99.");
		}
		return new ConfiguracionConector.Pasarela(llave, List.copyOf(medios), maximo);
	}

	private static ConfiguracionConector.CuentaBancaria cuentaBancaria(PropuestaConectorRequest p) {
		String numero = digitos(p.numeroCuenta());
		if (!numero.matches("[0-9]{10,20}")) {
			throw new ReglaNegocioException("Escribe el número de la cuenta (de 10 a 20 dígitos; los guiones no cuentan).");
		}
		String cci = digitos(p.cci());
		if (!cci.matches("[0-9]{20}")) {
			throw new ReglaNegocioException("El CCI tiene 20 dígitos.");
		}
		return new ConfiguracionConector.CuentaBancaria(numero, cci, "PEN");
	}

	private static ConfiguracionConector.Recaudacion recaudacion(PropuestaConectorRequest p) {
		String modalidad = vacio(p.modalidad()) ? "CON_BASE_DE_DEUDAS" : p.modalidad().strip().toUpperCase(Locale.ROOT);
		if (!modalidad.equals("CON_BASE_DE_DEUDAS") && !modalidad.equals("SIN_BASE")) {
			throw new ReglaNegocioException("La modalidad es «con base de deudas» o «sin base de deudas».");
		}
		String abono = vacio(p.abono()) ? "POR_LOTE" : p.abono().strip().toUpperCase(Locale.ROOT);
		if (!abono.equals("POR_LOTE") && !abono.equals("POR_PAGO")) {
			throw new ReglaNegocioException("El banco abona la recaudación por lote o pago por pago.");
		}
		String glosa = vacio(p.patronAbono()) ? "" : p.patronAbono().strip();
		if (!glosa.isEmpty() && !glosa.matches("[A-Za-z0-9 .\\-/]{3,60}")) {
			throw new ReglaNegocioException("La glosa del abono lleva letras, números, espacios, puntos, guiones o barras.");
		}
		return new ConfiguracionConector.Recaudacion(modalidad, glosa, abono);
	}

	private static String digitos(String texto) {
		return texto == null ? "" : texto.replaceAll("[\\s-]", "").replaceAll("[^0-9]", "x");
	}

	private static boolean vacio(String texto) {
		return texto == null || texto.isBlank();
	}
}
