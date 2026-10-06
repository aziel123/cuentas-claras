package pe.edu.virgenmaria.cuentasclaras.conciliacion.service;

/** Una persona confirmó una partida (sugerida, manual): el sistema deja las verificaciones después del commit. */
public record PartidaConfirmada(Long colegioId, Long partidaId) {
}
