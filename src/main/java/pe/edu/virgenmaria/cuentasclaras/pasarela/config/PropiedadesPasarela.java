package pe.edu.virgenmaria.cuentasclaras.pasarela.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * {@code cuentasclaras.pasarela}: el proveedor (NINGUNA por defecto: sin pago en línea; SIMULADA solo en dev, test y
 * piloto), la vigencia de la orden, el monto máximo por orden y los medios. Los secretos solo por variable de entorno.
 * Un valor imposible impide arrancar.
 */
@ConfigurationProperties("cuentasclaras.pasarela")
public record PropiedadesPasarela(
		@DefaultValue("NINGUNA") String proveedor,
		@DefaultValue("30") int minutosVigenciaOrden,
		@DefaultValue("5000.00") BigDecimal montoMaximo,
		@DefaultValue({ "YAPE", "PLIN", "TARJETA" }) List<MedioPago> medios,
		@DefaultValue("5") int reintentosAviso,
		@DefaultValue("16384") int maximoBytesAviso,
		@DefaultValue("10s") Duration consultaMinima,
		@DefaultValue("20") int alertaAvisosNoAutenticosPorHora,
		@DefaultValue Culqi culqi,
		@DefaultValue Simulada simulada) {

	public record Culqi(String llavePublica, String llaveSecreta, String webhookUsuario, String webhookClave,
			@DefaultValue("https://api.culqi.com") String api) {

		@Override
		public String toString() {
			return "Culqi[api=" + api + "]"; // sin llaves: este texto puede terminar en un log
		}
	}

	/**
	 * Pasarela simulada: el secreto con que firma (HMAC-SHA256) sus avisos y el porcentaje de comisión de sus
	 * liquidaciones simuladas (más el IGV de la comisión).
	 */
	public record Simulada(String secretoAviso, @DefaultValue("3.44") BigDecimal comisionPorcentaje) {

		public Simulada {
			if (comisionPorcentaje == null) {
				comisionPorcentaje = new BigDecimal("3.44");
			}
			if (comisionPorcentaje.signum() < 0 || comisionPorcentaje.compareTo(BigDecimal.TEN) > 0) {
				throw new IllegalArgumentException("cuentasclaras.pasarela.simulada.comision-porcentaje debe estar entre 0 y 10");
			}
		}

		@Override
		public String toString() {
			return "Simulada[]";
		}
	}

	public PropiedadesPasarela {
		if (minutosVigenciaOrden < 5 || minutosVigenciaOrden > 24 * 60) {
			throw new IllegalArgumentException("cuentasclaras.pasarela.minutos-vigencia-orden debe estar entre 5 y 1440");
		}
		if (montoMaximo == null || montoMaximo.signum() <= 0 || montoMaximo.compareTo(new BigDecimal("99999.99")) > 0) {
			throw new IllegalArgumentException("cuentasclaras.pasarela.monto-maximo debe estar entre 0.01 y 99999.99");
		}
		if (medios == null || medios.isEmpty() || medios.stream().anyMatch(m -> m == null || !m.enLinea())) {
			throw new IllegalArgumentException("cuentasclaras.pasarela.medios: solo YAPE, PLIN y TARJETA");
		}
		if (culqi == null) {
			culqi = new Culqi(null, null, null, null, "https://api.culqi.com");
		}
		if (simulada == null) {
			simulada = new Simulada(null, null);
		}
		if (consultaMinima == null || consultaMinima.isNegative()) {
			consultaMinima = Duration.ofSeconds(10);
		}
	}

	/** NINGUNA, SIMULADA, CULQI... en mayúsculas. */
	public String proveedorNormalizado() {
		return proveedor == null || proveedor.isBlank() ? "NINGUNA" : proveedor.strip().toUpperCase(java.util.Locale.ROOT);
	}

	public boolean habilitada() {
		return !"NINGUNA".equals(proveedorNormalizado());
	}
}
