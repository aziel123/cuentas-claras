package pe.edu.virgenmaria.cuentasclaras.alumnos.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.DatosSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.ManejadorSolicitud;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.util.List;
import java.util.Map;

/**
 * Datos de facturación (RUC y razón social) de un apoderado aprobados por otra persona (correcciones del sprint 3, B2):
 * desde entonces la familia puede pedir factura con ese RUC. El apoderado guarda el id de la solicitud; en MySQL
 * trg_apoderado_facturacion exige que esté aprobada.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ManejadorDatosFacturacion implements ManejadorSolicitud {

	private final ApoderadoRepository apoderados;

	private final RegistroAlumnos registro;

	public ManejadorDatosFacturacion(ApoderadoRepository apoderados, RegistroAlumnos registro) {
		this.apoderados = apoderados;
		this.registro = registro;
	}

	@Override
	public TipoSolicitud tipo() {
		return TipoSolicitud.DATOS_FACTURACION;
	}

	@Override
	public void aplicar(SolicitudCambio solicitud, String aprobador) {
		Apoderado apoderado = apoderados.findById(solicitud.getEntidadId())
				.orElseThrow(() -> new ReglaNegocioException("El apoderado de la solicitud no existe."));
		registro.aplicarDatosFacturacion(apoderado, DatosSolicitud.leer(solicitud.getDatos()), solicitud.getId(),
				solicitud.getMotivo(), solicitud.getSolicitadoPor(), aprobador);
	}

	@Override
	public List<String> detalle(SolicitudCambio solicitud) {
		Map<String, String> datos = DatosSolicitud.leer(solicitud.getDatos());
		String anterior = datos.getOrDefault("rucAnterior", "");
		return List.of("RUC nuevo: " + datos.get("ruc") + " · " + datos.get("razonSocial"),
				"RUC anterior: " + (anterior.isEmpty() ? "ninguno" : anterior),
				"Verifica en la consulta RUC de SUNAT que el RUC exista y sea de esta familia: con él se emitirán facturas.");
	}
}
