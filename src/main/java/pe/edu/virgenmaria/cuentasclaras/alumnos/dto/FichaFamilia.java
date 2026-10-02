package pe.edu.virgenmaria.cuentasclaras.alumnos.dto;

import java.util.List;

/** Familia: sus apoderados (con de quién son responsables de pago) y sus alumnos (hermanos). */
public record FichaFamilia(Long id, String nombre, List<ApoderadoVista> apoderados, List<HermanoVista> alumnos) {
}
