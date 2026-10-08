package pe.edu.virgenmaria.cuentasclaras.alumnos.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.FamiliaParaLlamada;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Familia;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.FamiliaRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Puerto de {@code alumnos} para la llamada de control del panel (sprint 6, tanda 3; decisión 77). Solo lectura, solo
 * Promotoría y Dirección (quienes llaman). Por familia: su nombre, sus apoderados ACTIVOS con el celular registrado y si
 * alguno usa el portal (cuenta en línea activa que ya entró al menos una vez). Las familias de otro colegio no existen
 * ({@code @TenantId}).
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
public class FamiliasParaLlamada {

	private final FamiliaRepository familias;

	private final ApoderadoRepository apoderados;

	private final UsuarioRepository usuarios;

	public FamiliasParaLlamada(FamiliaRepository familias, ApoderadoRepository apoderados, UsuarioRepository usuarios) {
		this.familias = familias;
		this.apoderados = apoderados;
		this.usuarios = usuarios;
	}

	/** Las familias pedidas que existen en el colegio actual, en el orden pedido. */
	public List<FamiliaParaLlamada> de(Collection<Long> familiaIds) {
		if (familiaIds.isEmpty()) {
			return List.of();
		}
		Map<Long, Familia> porId = familias.findAllById(familiaIds).stream()
				.collect(Collectors.toMap(Familia::getId, f -> f));
		Map<Long, List<Apoderado>> activos = new LinkedHashMap<>();
		for (Apoderado a : apoderados.findByFamiliaIdInAndActivoTrueOrderByIdAsc(familiaIds)) {
			activos.computeIfAbsent(a.getFamilia().getId(), id -> new java.util.ArrayList<>()).add(a);
		}
		Set<Long> idsApoderados = activos.values().stream().flatMap(List::stream).map(Apoderado::getId)
				.collect(Collectors.toSet());
		Set<Long> conPortal = idsApoderados.isEmpty() ? Set.of() : usuarios.findByApoderadoIdIn(idsApoderados).stream()
				.filter(u -> u.isActivo() && u.getUltimoIngresoEn() != null).map(Usuario::getApoderadoId)
				.collect(Collectors.toSet());
		return familiaIds.stream().distinct().filter(porId::containsKey).map(id -> {
			List<Apoderado> deLaFamilia = activos.getOrDefault(id, List.of());
			return new FamiliaParaLlamada(id, porId.get(id).getNombre(), deLaFamilia.size(),
					deLaFamilia.stream().anyMatch(a -> conPortal.contains(a.getId())),
					deLaFamilia.stream().map(a -> new FamiliaParaLlamada.Contacto(a.nombreCompleto(),
							a.getParentesco() == null ? null : a.getParentesco().etiqueta(), a.getTelefonoWhatsapp()))
							.toList());
		}).toList();
	}
}
