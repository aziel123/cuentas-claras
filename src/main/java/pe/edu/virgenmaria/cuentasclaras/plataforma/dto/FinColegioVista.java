package pe.edu.virgenmaria.cuentasclaras.plataforma.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Sprint 11, tanda 2 (3.9 a 3.11): el fin de un colegio en la ficha del operador: sus exportaciones, la conservación, la
 * retención legal y el acta de supresión. Nunca lleva la clave, el enlace ni el código de una entrega.
 */
public record FinColegioVista(Long colegioId, String colegio, String estado, LocalDate conservarHasta,
		boolean conservacionAutorizada, String retencionMotivo, String retencionPor, LocalDateTime retencionDesde,
		boolean puedeQuitarRetencion, List<Exportacion> exportaciones, boolean puedePedirExportacion,
		boolean puedeExportarParaCierre, Acta acta, boolean puedeProponerActa) {

	public record Exportacion(Long id, String motivo, String estado, LocalDateTime generadaEn, LocalDateTime venceEn,
			Long filas, Long bytes, String sha256Zip, String sha256Manifiesto, int descargas, String detalleFallo,
			boolean puedeReenviar) {
	}

	public record Acta(Long id, String tipo, String estado, String motivo, String propuestoPor, String aprobadoPor,
			LocalDateTime inicioPlazo, LocalDate respaldosVencenEl, LocalDateTime cadenaVerificadaEn, String huellaEventos,
			String ejecutadoPor, LocalDateTime ejecutadaEn, Integer eventosTachados, boolean puedeAprobar,
			boolean puedeVerificarCadena) {
	}
}
