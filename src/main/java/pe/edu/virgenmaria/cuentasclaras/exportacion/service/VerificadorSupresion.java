package pe.edu.virgenmaria.cuentasclaras.exportacion.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sprint 11, tanda 2 (AP2-3 y AP2-11) y correcciones (A1 y B3): al arrancar, la base no tiene nada de un colegio suprimido
 * y tiene aplicada cada acta ejecutada que vive FUERA de ella ({@link FuenteActasExternas}). En prod además son
 * obligatorios {@code CC_DIR_ACTAS} y {@code CC_DIR_EXPORTACIONES}: sin ellos (o vacíos) la aplicación NO arranca. Un respaldo
 * restaurado sin {@code reaplicar.sh} «resucita» los datos de un colegio: en prod y en el piloto la aplicación NO arranca; en
 * dev y en las pruebas, avisa en el log.
 */
@Component
public class VerificadorSupresion implements InitializingBean {

	/** Las tablas de la exportación que NO se suprimen (la evidencia: exclusiones.txt). */
	static final Set<String> NO_SE_SUPRIMEN = Set.of("evento_auditoria", "huella_bitacora", "huella_hora",
			"cambio_estado_colegio", "exportacion_colegio", "descarga_exportacion", "acta_supresion");

	private static final Logger LOG = LoggerFactory.getLogger(VerificadorSupresion.class);

	private final ColegioRepository colegios;

	private final LectorTablas lector;

	private final Environment entorno;

	private final FuenteActasExternas fuente;

	private final String directorioExportaciones;

	public VerificadorSupresion(ColegioRepository colegios, LectorTablas lector, Environment entorno,
			FuenteActasExternas fuente,
			@Value("${cuentasclaras.exportacion.directorio:}") String directorioExportaciones) {
		this.colegios = colegios;
		this.lector = lector;
		this.entorno = entorno;
		this.fuente = fuente;
		this.directorioExportaciones = directorioExportaciones == null ? "" : directorioExportaciones.strip();
	}

	@Override
	public void afterPropertiesSet() {
		if (entorno.acceptsProfiles(Profiles.of("prod"))) {
			exigirConfiguracion();
		}
		exigir(problemas());
	}

	/** A1 y B3: en prod, los dos directorios son obligatorios (sin valor por defecto). */
	void exigirConfiguracion() {
		List<String> faltan = new ArrayList<>();
		if (fuente.origen() == null || fuente.origen().isBlank()) {
			faltan.add("CC_DIR_ACTAS (cuentasclaras.supresion.actas-dir: la copia local de las actas del depósito)");
		}
		if (directorioExportaciones.isEmpty()) {
			faltan.add("CC_DIR_EXPORTACIONES (cuentasclaras.exportacion.directorio: los ZIP de las exportaciones)");
		}
		if (!faltan.isEmpty()) {
			throw new IllegalStateException("En prod son obligatorios: " + String.join("; ", faltan)
					+ ". Revisa docs/operacion/fin-de-un-colegio.md.");
		}
	}

	/** Lo que no está en orden (vacío si todo). */
	public List<String> problemas() {
		List<String> problemas = new ArrayList<>();
		Map<Long, Colegio> porId = colegios.findByTipoOrderByIdAsc(TipoColegio.COLEGIO).stream()
				.collect(Collectors.toMap(Colegio::getId, Function.identity()));
		for (FuenteActasExternas.ActaExterna acta : fuente.actas()) {
			Colegio c = porId.get(acta.colegioId());
			if (c == null || c.getEstado() != EstadoColegio.SUPRIMIDO
					|| !Objects.equals(c.getActaSupresionId(), acta.actaId())) {
				problemas.add("El acta de supresión " + acta.actaId() + " del colegio " + acta.colegioId() + " está fuera de "
						+ "la base y la base no la tiene aplicada: corre reaplicar.sh antes de abrir la aplicación.");
			}
		}
		List<TablasExportacion.Tabla> tablas = TablasExportacion.cargar().stream()
				.filter(t -> !NO_SE_SUPRIMEN.contains(t.nombre())).toList();
		for (Colegio c : porId.values()) {
			if (c.getEstado() != EstadoColegio.SUPRIMIDO) {
				continue;
			}
			for (TablasExportacion.Tabla t : tablas) {
				long n = lector.contar(t, c.getId());
				if (n > 0) {
					problemas.add("El colegio suprimido " + c.getId() + " tiene " + n + " filas en " + t.nombre() + ".");
				}
			}
		}
		return problemas;
	}

	public void exigir(List<String> problemas) {
		if (problemas.isEmpty()) {
			LOG.info("Supresiones verificadas: ningún colegio suprimido tiene datos y las actas externas están aplicadas.");
			return;
		}
		String mensaje = String.join(" ", problemas);
		if (entorno.acceptsProfiles(Profiles.of("prod", "piloto"))) {
			throw new IllegalStateException(mensaje);
		}
		LOG.warn("{} (en prod la aplicación no arrancaría).", mensaje);
	}
}
