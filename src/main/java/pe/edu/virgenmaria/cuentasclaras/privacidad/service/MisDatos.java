package pe.edu.virgenmaria.cuentasclaras.privacidad.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.MatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.SesionApoderado;
import pe.edu.virgenmaria.cuentasclaras.privacidad.config.PropiedadesPrivacidad;
import pe.edu.virgenmaria.cuentasclaras.privacidad.dto.VistaMisDatos;

import java.util.List;
import java.util.Objects;

/**
 * «Mis datos» en el portal (sprint 7, tanda 3; Ley 29733, derecho de acceso al instante, sección 8.3). La familia sale
 * SIEMPRE de la cuenta en sesión ({@link SesionApoderado}), nunca de un parámetro: no hay forma de pedir la de otra. Sin
 * deudas ni nada académico (INDECOPI: los datos no se muestran junto a la deuda). Ver los datos propios no se registra en
 * el registro de accesos (es la familia).
 */
@Service
@PreAuthorize("hasRole('APODERADO')")
@Transactional(readOnly = true)
public class MisDatos {

	/** Para qué usa el colegio cada dato (lo confirma el aviso de privacidad aprobado por el asesor legal). */
	static final List<VistaMisDatos.Uso> USOS = List.of(
			new VistaMisDatos.Uso("Nombres, documento y parentesco del apoderado",
					"Identificarte, emitir tus boletas o facturas y saber quién responde por los pagos."),
			new VistaMisDatos.Uso("Nombres, documento y fecha de nacimiento de tus hijos",
					"Matricularlos, generar sus pensiones y registrarlos en el SIAGIE del Ministerio de Educación."),
			new VistaMisDatos.Uso("Celular y correo",
					"Avisarte de cada pago, anulación o descuento al momento, enviarte tus boletas, los recordatorios (si "
							+ "los tienes encendidos) y el enlace para activar tu cuenta."),
			new VistaMisDatos.Uso("RUC y razón social (si los registraste)", "Emitir factura en lugar de boleta."),
			new VistaMisDatos.Uso("Pagos, cuotas y comprobantes",
					"Llevar tu estado de cuenta y cumplir las obligaciones tributarias del colegio."));

	/** A quién se envía cada dato (decisión 100: el flujo transfronterizo lo declara el colegio). */
	static final List<VistaMisDatos.Destinatario> DESTINATARIOS = List.of(
			new VistaMisDatos.Destinatario("El operador de comprobantes electrónicos (OSE) y SUNAT",
					"Tu nombre, documento o RUC y el detalle de cada pago", "Emitir tus boletas y facturas (ley tributaria)."),
			new VistaMisDatos.Destinatario("Meta (WhatsApp)", "Tu celular y el texto de cada aviso",
					"Enviarte los avisos por WhatsApp. Sus servidores están fuera del Perú."),
			new VistaMisDatos.Destinatario("El proveedor de correo del colegio", "Tu correo y el texto de cada aviso",
					"Enviarte los avisos por correo cuando no hay WhatsApp."),
			new VistaMisDatos.Destinatario("El banco de la recaudación",
					"El código y el nombre del alumno, y las cuotas por pagar", "Que puedas pagar en el banco con el código."),
			new VistaMisDatos.Destinatario("El proveedor del servidor (hosting)", "Todos los datos de esta lista",
					"Guardarlos de forma segura. Si está fuera del Perú, el colegio lo declara ante la Autoridad."));

	/** Plazos de conservación (sección 8.4, decisión 98): los confirma el asesor legal. */
	static final List<VistaMisDatos.Plazo> PLAZOS = List.of(
			new VistaMisDatos.Plazo("Pagos, comprobantes, cuotas, anulaciones, descuentos y bitácora",
					"Mientras no prescriban las obligaciones tributarias y contables del colegio (los fija el contador). "
							+ "No se borran: son la evidencia de cada pago."),
			new VistaMisDatos.Plazo("Avisos enviados (WhatsApp y correo)",
					"Como los pagos: son la constancia de cada aviso."),
			new VistaMisDatos.Plazo("Celular y correo, si la familia deja el colegio sin deuda",
					"Hasta 1 año después del retiro o del egreso."),
			new VistaMisDatos.Plazo("Registro de quién del colegio vio tus datos", "2 años."),
			new VistaMisDatos.Plazo("Copias de respaldo", "35 copias diarias y 12 mensuales, cifradas."));

	private final SesionApoderado sesion;

	private final ApoderadoRepository apoderados;

	private final AlumnoRepository alumnos;

	private final MatriculaRepository matriculas;

	private final PropiedadesPrivacidad propiedades;

	public MisDatos(SesionApoderado sesion, ApoderadoRepository apoderados, AlumnoRepository alumnos,
			MatriculaRepository matriculas, PropiedadesPrivacidad propiedades) {
		this.sesion = sesion;
		this.apoderados = apoderados;
		this.alumnos = alumnos;
		this.matriculas = matriculas;
		this.propiedades = propiedades;
	}

	public VistaMisDatos deMiFamilia() {
		Apoderado yo = sesion.apoderado();
		Long familiaId = yo.getFamilia().getId();
		List<Alumno> hijos = alumnos.findByFamiliaIdOrderByFechaNacimientoAsc(familiaId);
		List<VistaMisDatos.Apoderado> suyos = apoderados.findByFamiliaIdOrderByApellidoPaternoAsc(familiaId).stream()
				.filter(Apoderado::isActivo)
				.map(a -> new VistaMisDatos.Apoderado(a.nombreCompleto(), a.getParentesco().etiqueta(),
						a.getDocumento().texto(), a.getTelefonoWhatsapp(), a.telefonoVerificado(), a.getCorreo(),
						a.correoVerificado(), a.getRuc(), a.getRazonSocial(), a.isRecordatoriosActivos(),
						hijos.stream().anyMatch(h -> Objects.equals(h.getResponsablePago().getId(), a.getId()))))
				.toList();
		List<VistaMisDatos.Hijo> suyosHijos = hijos.stream().map(h -> new VistaMisDatos.Hijo(h.nombreCompleto(),
				h.getDocumento().texto(), h.getFechaNacimiento(), h.getEstado().etiqueta(),
				matriculas.findByAlumnoIdOrderByAnioEscolarAnioDesc(h.getId()).stream()
						.map(m -> new VistaMisDatos.Matricula(m.getAnioEscolar().getAnio(),
								m.getSeccion().getGrado().etiqueta(), m.getSeccion().getNombre(), m.getEstado().etiqueta()))
						.toList()))
				.toList();
		return new VistaMisDatos(yo.getFamilia().getNombre(), suyos, suyosHijos, USOS, DESTINATARIOS, PLAZOS,
				propiedades.versionAviso());
	}
}
