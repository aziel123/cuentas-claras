package pe.edu.virgenmaria.cuentasclaras.plataforma.service;

import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.CambioCorreoControl;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.repository.CambioCorreoControlRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.identidad.AltaTitular;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.ConsultaAuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.LogoColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Enmascarar;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.ConfiguracionColegio;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.repository.ConfiguracionColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.FichaPlataforma;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.ResumenColegio;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto.PuntoLista;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.EstadoPuestaEnMarcha;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.PrimeraPromotoriaRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.util.List;
import java.util.Optional;

/**
 * Sprint 8, tanda 1 (decisión 113): lo que el operador ve de los colegios de su instalación: la ficha (nombre, RUC, logo,
 * niveles, estado, titular y contrato) y la lista de verificación como sí/no y conteos. Nunca nombres, documentos,
 * contactos ni montos de las familias: los conteos salen de {@link EstadoPuestaEnMarcha} y los datos del titular van
 * enmascarados.
 */
@Service
@PreAuthorize("hasRole('OPERADOR_PLATAFORMA')")
public class ColegiosDeLaPlataforma {

	private final ColegioRepository colegios;

	private final UsuarioRepository usuarios;

	private final PrimeraPromotoriaRepository primeras;

	private final LogoColegioRepository logos;

	private final ConfiguracionColegioRepository configuracion;

	private final EstadoPuestaEnMarcha estado;

	private final ConsultaAuditoriaService bitacora;

	private final AltaTitular titulares;

	private final CambioCorreoControlRepository cambiosCorreo;

	private final TransactionTemplate lectura;

	public ColegiosDeLaPlataforma(ColegioRepository colegios, UsuarioRepository usuarios,
			PrimeraPromotoriaRepository primeras, LogoColegioRepository logos, ConfiguracionColegioRepository configuracion,
			EstadoPuestaEnMarcha estado, ConsultaAuditoriaService bitacora, AltaTitular titulares,
			CambioCorreoControlRepository cambiosCorreo, PlatformTransactionManager transacciones) {
		this.titulares = titulares;
		this.cambiosCorreo = cambiosCorreo;
		this.colegios = colegios;
		this.usuarios = usuarios;
		this.primeras = primeras;
		this.logos = logos;
		this.configuracion = configuracion;
		this.estado = estado;
		this.bitacora = bitacora;
		this.lectura = new TransactionTemplate(transacciones);
		this.lectura.setReadOnly(true);
	}

	public List<ResumenColegio> todos() {
		return todos(false);
	}

	/**
	 * Sprint 11, tanda 1: la lista principal (sin los dados de baja) o, con {@code dadosDeBaja}, solo los dados de baja
	 * (el filtro «Dados de baja»).
	 */
	public List<ResumenColegio> todos(boolean dadosDeBaja) {
		List<Colegio> lista = lectura.execute(t -> colegios.findByTipoOrderByIdAsc(TipoColegio.COLEGIO)).stream()
				.filter(c -> (c.getEstado() == pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoColegio.BAJA)
						== dadosDeBaja).toList();
		return lista.stream().map(c -> ContextoColegio.en(c.getId(), () -> lectura.execute(t -> {
			Optional<Usuario> titular = titular();
			return new ResumenColegio(c.getId(), c.getNombre(), c.getRuc(), c.getEstado(), titular.isPresent(),
					titular.map(u -> titulares.cuentaActivada(u.getId())).orElse(false), usuarios.count(),
					bitacora.ultimaActividadDe(c.getId()).orElse(null));
		}))).toList();
	}

	public FichaPlataforma ficha(long colegioId) {
		Colegio colegio = lectura.execute(t -> colegios.findById(colegioId).filter(c -> !c.esPlataforma()).orElse(null));
		if (colegio == null) {
			throw new RecursoNoEncontradoException("Colegio no encontrado");
		}
		List<PuntoLista> lista = estado.paraOperador(colegioId).puntos();
		AltaTitular.ContactoPorAplicar porAplicar = titulares.contactoPorAplicar(colegioId).orElse(null);
		FichaPlataforma ficha = ContextoColegio.en(colegioId, () -> lectura.execute(t -> {
			Optional<Usuario> titular = titular();
			String correoControl = configuracion.findByColegioIdAndClave(colegioId, ConfiguracionColegio.HUELLA_CORREO_EXTERNO)
					.map(ConfiguracionColegio::getValor).map(Enmascarar::correo).orElse(null);
			return new FichaPlataforma(colegio.getId(), colegio.getNombre(), colegio.getRazonSocial(), colegio.getRuc(),
					colegio.getDireccion(), colegio.getCorreoInstitucional(), colegio.getNiveles(), colegio.getEstado(),
					colegio.getContratoCodigo(), colegio.getTitularNombre(),
					colegio.getTitularDocumento() == null ? null : Enmascarar.documento("DNI", colegio.getTitularDocumento()),
					titular.map(Usuario::getNombreUsuario).orElse(null), titular.map(ColegiosDeLaPlataforma::destino).orElse(null),
					titular.map(u -> titulares.cuentaActivada(u.getId())).orElse(false), logos.existsByVigenteTrue(),
					correoControl, colegio.getCreadoEn(), colegio.getCreadoPor(), colegio.getProduccionDesde(),
					lista.stream().map(p -> new FichaPlataforma.PuntoLista(p.paso(), p.hecho(), p.obligatorio(), p.conteo()))
							.toList(), null,
					cambiosCorreo.findFirstByEstado(CambioCorreoControl.Estado.PROPUESTO)
							.map(c -> Enmascarar.correo(c.getCorreoNuevo())).orElse(null));
		}));
		return new FichaPlataforma(ficha.id(), ficha.nombre(), ficha.razonSocial(), ficha.ruc(), ficha.direccion(),
				ficha.correoInstitucional(), ficha.niveles(), ficha.estado(), ficha.contratoCodigo(), ficha.titularNombre(),
				ficha.titularDocumentoEnmascarado(), ficha.titularUsuario(), ficha.titularDestino(), ficha.titularActivado(),
				ficha.conLogo(), ficha.correoControl(), ficha.creadoEn(), ficha.creadoPor(), ficha.produccionDesde(),
				ficha.lista(), porAplicar, ficha.correoControlPorAprobar());
	}

	private Optional<Usuario> titular() {
		return primeras.findFirstByIdNotNull().flatMap(p -> usuarios.findById(p.getUsuarioId()));
	}

	private static String destino(Usuario u) {
		return u.getTelefonoWhatsapp() != null ? "WhatsApp " + Enmascarar.telefono(u.getTelefonoWhatsapp())
				: u.getCorreo() != null ? "correo " + Enmascarar.correo(u.getCorreo()) : "sin contacto";
	}
}
