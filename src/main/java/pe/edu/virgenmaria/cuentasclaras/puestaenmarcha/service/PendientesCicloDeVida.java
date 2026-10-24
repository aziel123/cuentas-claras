package pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.MatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.model.EstadoCierre;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.CierreCajaRepository;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.EstadoEnvio;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.repository.ComprobanteRepository;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.EstadoOrden;
import pe.edu.virgenmaria.cuentasclaras.pasarela.repository.OrdenPagoRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Sprint 11, tanda 1 (secciones 3.5 y 3.13): lo que impide o hace esperar un cambio de estado del colegio del contexto,
 * como conteos (nunca nombres ni montos: lo ve el operador). La BAJA no procede con dinero (comprobantes u órdenes, F12) ni
 * con datos (alumnos, apoderados o matrículas, AP-6); la SUSPENSION espera los cierres por revisar u observados y los
 * comprobantes no aceptados (AP-4) y las órdenes en vuelo (H3). Lo mismo que exige la base. Lo usan la plataforma (al
 * proponer y en la bandeja) y {@code sistema.plataforma} (al aplicar), con el colegio en contexto y una transacción.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
public class PendientesCicloDeVida {

	private final ComprobanteRepository comprobantes;

	private final OrdenPagoRepository ordenes;

	private final CierreCajaRepository cierres;

	private final AlumnoRepository alumnos;

	private final ApoderadoRepository apoderados;

	private final MatriculaRepository matriculas;

	private final Clock reloj;

	public PendientesCicloDeVida(ComprobanteRepository comprobantes, OrdenPagoRepository ordenes,
			CierreCajaRepository cierres, AlumnoRepository alumnos, ApoderadoRepository apoderados,
			MatriculaRepository matriculas, Clock reloj) {
		this.comprobantes = comprobantes;
		this.ordenes = ordenes;
		this.cierres = cierres;
		this.alumnos = alumnos;
		this.apoderados = apoderados;
		this.matriculas = matriculas;
		this.reloj = reloj;
	}

	/** Por qué no se da de baja (vacío si se puede). */
	public List<String> impidenLaBaja() {
		List<String> motivos = new ArrayList<>();
		if (comprobantes.existsByIdNotNull() || ordenes.existsByIdNotNull()) {
			motivos.add("el colegio ya emitió comprobantes o tuvo órdenes de pago (ya cobró)");
		}
		long datos = alumnos.count() + apoderados.count() + matriculas.count();
		if (datos > 0) {
			motivos.add("el colegio tiene alumnos, apoderados o matrículas: su baja llega con la exportación de sus datos "
					+ "(tanda 2)");
		}
		return motivos;
	}

	/** Lo que hace esperar la suspensión, para la bandeja y la alerta diaria (vacío si nada). */
	public List<String> esperanLaSuspension() {
		List<String> motivos = new ArrayList<>();
		long porRevisar = cierres.countByEstadoIn(EnumSet.of(EstadoCierre.POR_REVISAR, EstadoCierre.OBSERVADO));
		if (porRevisar > 0) {
			motivos.add(porRevisar + (porRevisar == 1 ? " cierre de caja por revisar" : " cierres de caja por revisar"));
		}
		long sinAceptar = comprobantes.countByEstadoEnvioIn(EnumSet.of(EstadoEnvio.PENDIENTE, EstadoEnvio.ENVIADO,
				EstadoEnvio.RECHAZADO));
		if (sinAceptar > 0) {
			motivos.add(sinAceptar + (sinAceptar == 1 ? " comprobante sin aceptar por SUNAT"
					: " comprobantes sin aceptar por SUNAT"));
		}
		long enVuelo = ordenes.countByEstadoAndVenceEnAfter(EstadoOrden.CREADA, LocalDateTime.now(reloj));
		if (enVuelo > 0) {
			motivos.add(enVuelo + (enVuelo == 1 ? " pago en línea en curso" : " pagos en línea en curso"));
		}
		return motivos;
	}
}
