# Las historias clínicas antiguas no se importan como entradas nativas

Se importan pacientes por CSV — nombre, documento, contacto — para que una
clínica pueda arrancar sin teclear su cartera entera. La historia clínica previa
en papel o en el sistema anterior **no** se vuelca: se digitaliza a partir de la
primera visita en Zendenta, y si hace falta conservarla se adjunta escaneada
como Attachment de referencia.

Importar historia antigua parece más generoso y es peor que no ofrecer nada. El
registro clínico es append-only con autoría (ADR 0004) porque su valor es
probatorio; volcar dentro fichas de 2019 crea entradas con autor y fecha
inventados, y eso contamina la fiabilidad de todo el registro, incluido el que
sí es auténtico. Un histórico que no se puede defender no vale más que un
histórico ausente: vale menos, porque no se distingue del bueno.

## Consecuencias

El onboarding de una clínica real depende de que la importación CSV de pacientes
exista, así que no es una funcionalidad posponible indefinidamente.
