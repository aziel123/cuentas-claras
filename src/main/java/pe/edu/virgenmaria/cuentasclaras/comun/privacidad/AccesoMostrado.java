package pe.edu.virgenmaria.cuentasclaras.comun.privacidad;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Lo que mostró la pantalla marcada con {@link RegistraAcceso}: la familia, el alumno (y su familia), varias familias (una
 * fila por cada una, como la llamada de control) o cuántas filas con datos personales (búsquedas y listas, sin el texto
 * buscado) o la solicitud de cambio de contacto que se abrió. Lo anota el controlador en la petición en curso y lo lee el registro de accesos antes de mostrar la página.
 * Sin una petición en curso, no hace nada.
 */
public record AccesoMostrado(Set<Long> familias, Long alumnoId, int cantidad, Long solicitudId) {

	/** Atributo de la petición con lo mostrado. */
	public static final String ATRIBUTO = AccesoMostrado.class.getName();

	public AccesoMostrado {
		familias = Set.copyOf(familias);
		if (cantidad < 0) {
			throw new IllegalArgumentException("cantidad");
		}
	}

	/** La ficha de una familia (o de uno de sus apoderados). */
	public static void familia(Long familiaId) {
		anotar(new AccesoMostrado(Set.of(Objects.requireNonNull(familiaId, "familiaId")), null, 1, null));
	}

	/** La ficha de un alumno de esa familia. */
	public static void alumno(Long alumnoId, Long familiaId) {
		anotar(new AccesoMostrado(familiaId == null ? Set.of() : Set.of(familiaId),
				Objects.requireNonNull(alumnoId, "alumnoId"), 1, null));
	}

	/** Varias familias mostradas a la vez: una fila del registro por cada una. */
	public static void familias(Collection<Long> familiaIds) {
		anotar(new AccesoMostrado(new LinkedHashSet<>(familiaIds), null, familiaIds.size(), null));
	}

	/** Una búsqueda o una lista: solo cuántas filas con datos personales mostró. */
	public static void filas(int cantidad) {
		anotar(new AccesoMostrado(Set.of(), null, cantidad, null));
	}

	/**
	 * El detalle de una solicitud de la bandeja: si es un cambio del celular o el correo de un apoderado, el registro de
	 * accesos busca su familia; si es de otro tipo, no se registra nada.
	 */
	public static void solicitud(Long solicitudId) {
		anotar(new AccesoMostrado(Set.of(), null, 0, Objects.requireNonNull(solicitudId, "solicitudId")));
	}

	private static void anotar(AccesoMostrado mostrado) {
		RequestAttributes atributos = RequestContextHolder.getRequestAttributes();
		if (atributos != null) {
			atributos.setAttribute(ATRIBUTO, mostrado, RequestAttributes.SCOPE_REQUEST);
		}
	}
}
