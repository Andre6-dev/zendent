# Las fotos clínicas viven en object storage y solo se sirven con URL firmada

Los adjuntos clínicos se guardan en Cloudflare R2, con la clave del objeto
prefijada por `clinic_id`, y se sirven exclusivamente mediante URLs firmadas de
vida corta. Nunca hay una URL pública, ni siquiera una "no adivinable".

R2 se eligió sobre S3 por no cobrar egress, que es justamente el patrón de coste
de servir fotos intraorales de forma repetida. La regla de URL firmada no es una
preferencia de diseño sino una consecuencia de que una foto intraoral es un dato
de salud identificable bajo la Ley 29733: una URL pública es una filtración
aunque nadie llegue a adivinar la ruta.

## Consecuencias

Una foto se adjunta al `Appointment` — es el momento en que se tomó — y se
etiqueta opcionalmente contra uno o más dientes, de modo que la ficha de cada
pieza pueda mostrar su historial fotográfico.
