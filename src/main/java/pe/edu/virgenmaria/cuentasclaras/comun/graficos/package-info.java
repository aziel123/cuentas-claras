/**
 * Rediseño de la interfaz (tableros del inicio): gráficos dibujados en el servidor como SVG, sin JavaScript ni estilos en
 * línea (la CSP los prohíbe). La geometría sale en PORCENTAJES para los atributos {@code x}, {@code y}, {@code width} y
 * {@code height}: el SVG se estira al ancho de la tarjeta sin deformar el texto. Cada gráfico trae su resumen en texto y
 * las filas de su tabla de datos equivalente. Aquí no hay consultas: los módulos arman los datos con sus puertos.
 */
package pe.edu.virgenmaria.cuentasclaras.comun.graficos;
