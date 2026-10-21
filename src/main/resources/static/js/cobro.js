// Cobro en dos columnas (CSP: sin scripts en línea). Suma en vivo los saldos de las cuotas marcadas en el resumen de la
// derecha. Solo muestra: el total que vale lo calcula el servidor en el paso «Revisar cobro». Suma en céntimos (enteros)
// para no arrastrar errores de punto flotante.
(function () {
	'use strict';
	function centimos(texto) {
		var partes = String(texto || '0').split('.');
		var enteros = parseInt(partes[0], 10) || 0;
		var decimales = parseInt(((partes[1] || '') + '00').substring(0, 2), 10) || 0;
		return enteros * 100 + decimales;
	}
	function formatear(c) {
		var soles = Math.floor(c / 100).toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',');
		var cent = (c % 100).toString();
		return 'S/ ' + soles + '.' + (cent.length < 2 ? '0' + cent : cent);
	}
	function actualizar(formulario) {
		var marcadas = formulario.querySelectorAll('input[name="cuotaIds"]:checked');
		var total = 0;
		marcadas.forEach(function (c) { total += centimos(c.getAttribute('data-saldo')); });
		var salida = formulario.querySelector('[data-total-marcado]');
		var detalle = formulario.querySelector('[data-cuotas-marcadas]');
		if (salida) { salida.textContent = formatear(total); }
		if (detalle) {
			detalle.textContent = marcadas.length === 0 ? 'Marca las cuotas a la izquierda.'
				: marcadas.length === 1 ? '1 cuota marcada' : marcadas.length + ' cuotas marcadas';
		}
	}
	document.addEventListener('DOMContentLoaded', function () {
		document.querySelectorAll('[data-cobro]').forEach(function (formulario) {
			formulario.addEventListener('change', function () { actualizar(formulario); });
			actualizar(formulario);
		});
	});
}());
