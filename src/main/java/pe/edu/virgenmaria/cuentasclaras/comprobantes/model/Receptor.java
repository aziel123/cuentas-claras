package pe.edu.virgenmaria.cuentasclaras.comprobantes.model;

import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Enmascarar;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Ruc;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.TextoSeguro;

import java.util.Locale;
import java.util.Objects;

/**
 * Quién recibe el comprobante. Una factura exige un RUC válido (módulo 11); una boleta, DNI, CE o pasaporte. Usar
 * {@link #de}: valida el formato y limpia el nombre.
 */
public record Receptor(DocumentoReceptor tipo, String numero, String nombre) {

	public static final int MAX_NOMBRE = 150;

	public Receptor {
		Objects.requireNonNull(tipo, "tipo");
		Objects.requireNonNull(numero, "numero");
		Objects.requireNonNull(nombre, "nombre");
	}

	public static Receptor de(DocumentoReceptor tipo, String numero, String nombre) {
		if (tipo == null) {
			throw new ReglaNegocioException("Indica el tipo de documento de quien recibe el comprobante.");
		}
		String limpio = Normalizador.sinEspacios(numero);
		limpio = limpio == null ? null : limpio.toUpperCase(Locale.ROOT);
		if (tipo == DocumentoReceptor.RUC) {
			if (!Ruc.valido(limpio)) {
				throw new ReglaNegocioException("El RUC no es válido: revisa los 11 dígitos.");
			}
		}
		else if (!tipo.formatoValido(limpio)) {
			throw new ReglaNegocioException("El documento de quien recibe el comprobante no es válido.");
		}
		String nombreLimpio = Normalizador.limpiar(nombre);
		if (nombreLimpio == null || nombreLimpio.length() > MAX_NOMBRE) {
			throw new ReglaNegocioException(tipo == DocumentoReceptor.RUC
					? "Escribe la razón social (hasta " + MAX_NOMBRE + " caracteres)."
					: "Falta el nombre de quien recibe el comprobante.");
		}
		TextoSeguro.exigir(nombreLimpio, tipo == DocumentoReceptor.RUC ? "la razón social" : "el nombre");
		return new Receptor(tipo, limpio, nombreLimpio);
	}

	/** «DNI 45678912» o «RUC 20131312955». Para el comprobante impreso; nunca para la bitácora. */
	public String documentoTexto() {
		return tipo.abreviatura() + " " + numero;
	}

	/** «DNI ****8912»: para la bitácora (un RUC es público, pero se enmascara igual por si es de persona natural). */
	public String documentoEnmascarado() {
		return Enmascarar.documento(tipo.abreviatura(), numero);
	}

	@Override
	public String toString() {
		return documentoEnmascarado();
	}
}
