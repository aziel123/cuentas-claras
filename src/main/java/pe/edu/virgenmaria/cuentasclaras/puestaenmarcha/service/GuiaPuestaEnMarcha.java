package pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service;

import org.springframework.stereotype.Service;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto.FilaPaso;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto.GuiaVista;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto.GuiaVista.PasoGuia;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto.ListaVerificacion;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto.PuntoLista;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.model.PasoPuestaEnMarcha;
import pe.edu.virgenmaria.cuentasclaras.seguridad.config.ModuloApp;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Sprint 11, tanda 1b (sección 3.14): el siguiente paso de la puesta en marcha para quien está en sesión. Todo sale de la
 * lista que ya calcula {@link EstadoPuestaEnMarcha} (una vez por pedido, sin caché entre pedidos: un paso hecho se ve al
 * volver). Nunca devuelve un enlace a una ruta que daría 403 a ese rol ({@link ModuloApp#puedeEntrar}).
 */
@Service
public class GuiaPuestaEnMarcha {

	/** Los roles del personal que no ven cifras de cobranza (decisión 141). */
	private static final Set<Rol> CON_CONTEOS = EnumSet.of(Rol.PROMOTOR, Rol.DIRECTOR, Rol.ADMINISTRACION, Rol.CAJA);

	private final EstadoPuestaEnMarcha estado;

	public GuiaPuestaEnMarcha(EstadoPuestaEnMarcha estado) {
		this.estado = estado;
	}

	/** La guía del colegio en contexto, para estos roles. */
	public GuiaVista guiaPara(Set<Rol> roles) {
		return guia(estado.actual(), roles);
	}

	/** El cálculo, sobre una lista ya leída (la Puesta en marcha y los estados vacíos la reutilizan). */
	public static GuiaVista guia(ListaVerificacion lista, Set<Rol> roles) {
		boolean conConteos = roles.stream().anyMatch(CON_CONTEOS::contains);
		List<PuntoLista> pendientes = lista.puntos().stream().filter(p -> !p.hecho()).toList();
		Predicate<PuntoLista> delColegio = p -> p.definicion().loHaceElColegio();
		Predicate<PuntoLista> libre = p -> p.bloqueadoPor() == null;
		Predicate<PuntoLista> obligatorio = p -> p.definicion().obligatorio();
		Predicate<PuntoLista> leToca = p -> p.definicion().loHace(roles);

		PuntoLista siguiente = pendientes.stream().filter(obligatorio.and(delColegio).and(libre)).findFirst().orElse(null);
		PuntoLista tuyo = null;
		if (siguiente == null || !leToca.test(siguiente)) {
			// Primero los obligatorios; después los opcionales y los avisos que le tocan.
			tuyo = pendientes.stream().filter(delColegio.and(libre).and(leToca))
					.filter(p -> vista(p, lista, roles, conConteos).enlace() != null)
					.sorted(Comparator.comparing((PuntoLista p) -> p.definicion().tipo()))
					.findFirst().orElse(null);
		}
		PuntoLista elSiguiente = siguiente;
		List<PasoGuia> proximos = pendientes.stream().filter(delColegio).filter(p -> p != elSiguiente)
				.filter(p -> p.definicion().tipo() != PasoPuestaEnMarcha.Tipo.AVISO)
				.sorted(Comparator.comparing((PuntoLista p) -> p.definicion().tipo()))
				.limit(3).map(p -> vista(p, lista, roles, conConteos)).toList();
		List<PasoGuia> plataforma = pendientes.stream().filter(obligatorio.and(delColegio.negate()))
				.map(p -> vista(p, lista, roles, conConteos)).toList();

		long obligatoriosPendientes = pendientes.stream().filter(obligatorio).count();
		long hechos = lista.puntos().stream().filter(obligatorio).filter(PuntoLista::hecho).count();
		long opcionales = pendientes.stream().filter(p -> p.definicion().opcional()).count();
		return new GuiaVista(obligatoriosPendientes, PasoPuestaEnMarcha.TOTAL, hechos, opcionales,
				siguiente == null ? null : vista(siguiente, lista, roles, conConteos),
				tuyo == null ? null : vista(tuyo, lista, roles, conConteos), proximos, plataforma, conConteos,
				ModuloApp.puedeEntrar(roles, "/puesta-en-marcha"));
	}

	/** Las filas de la Puesta en marcha (los 15 pasos y el aviso de las áreas), con el estado en texto. */
	public static List<FilaPaso> filas(ListaVerificacion lista, Set<Rol> roles) {
		GuiaVista guia = guia(lista, roles);
		PasoPuestaEnMarcha siguiente = guia.siguiente() == null ? null : guia.siguiente().paso();
		return lista.puntos().stream().map(p -> {
			PasoPuestaEnMarcha paso = p.definicion();
			String estado;
			String variante;
			if (p.hecho()) {
				estado = "Hecho";
				variante = "exito";
			}
			else if (!paso.loHaceElColegio()) {
				estado = "Lo hace la plataforma";
				variante = "info";
			}
			else if (p.bloqueado()) {
				estado = "Bloqueado: primero el paso " + p.bloqueadoPor().numero();
				variante = "neutro";
			}
			else if (!paso.obligatorio()) {
				estado = "Opcional";
				variante = "neutro";
			}
			else {
				estado = "Pendiente";
				variante = "alerta";
			}
			return new FilaPaso(p, vista(p, lista, roles, true), paso == siguiente, estado, variante);
		}).toList();
	}

	/** Un punto como paso de la guía: la acción con el año y el enlace solo si el rol hace el paso y puede entrar. */
	public static PasoGuia vista(PuntoLista punto, ListaVerificacion lista, Set<Rol> roles, boolean conConteos) {
		PasoPuestaEnMarcha paso = punto.definicion();
		String ruta = Stream.of(punto.enlace(), paso.ruta()).filter(r -> r != null && !r.isBlank()).findFirst()
				.orElse(null);
		boolean loHace = paso.loHace(roles);
		String enlace = ruta != null && punto.bloqueadoPor() == null && ModuloApp.puedeEntrar(roles, ruta) ? ruta : null;
		return new PasoGuia(paso, paso.accion(lista.anio()), enlace, conConteos ? punto.conteo() : null, loHace);
	}
}
