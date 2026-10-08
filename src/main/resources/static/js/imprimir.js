// Impresión del comprobante (CSP: sin scripts en línea). Botón [data-imprimir] y apertura con ?imprimir=true.
(function () {
	'use strict';
	document.addEventListener('click', function (evento) {
		var boton = evento.target.closest('[data-imprimir]');
		if (boton) {
			evento.preventDefault();
			window.print();
		}
	});
	document.addEventListener('DOMContentLoaded', function () {
		if (document.querySelector('[data-imprimir-al-abrir]')) {
			window.print();
		}
	});
}());
