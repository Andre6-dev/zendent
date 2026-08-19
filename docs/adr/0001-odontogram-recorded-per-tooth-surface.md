# El odontograma se registra por superficie dental, no por diente

El mockup de referencia pinta el diente entero de un color, lo que sugiere un
estado único por pieza. Registramos en cambio una condición por cada una de las
cinco superficies (mesial, distal, vestibular, lingual/palatina, oclusal), con
notación FDI y soporte tanto de dentición permanente (11-48) como temporal
(51-85) para pacientes pediátricos y dentición mixta.

Un odontograma que no distingue caras no describe la clínica real: una caries
oclusal, una restauración mesial y una corona completa son hallazgos distintos
sobre la misma pieza, y un odontólogo no adoptaría una herramienta que no los
separa. La alternativa simple (un estado por diente, como el mockup) obligaría a
migrar todo el histórico clínico acumulado el día que se necesiten superficies —
y en una historia clínica ese histórico es un documento legal que no se puede
reinterpretar retroactivamente.

## Consecuencias

La UI del mockup se conserva como vista por defecto: el color del diente se
deriva agregando el estado de sus superficies. El modelo de datos es más fino
que lo que la vista muestra, y la vista detallada por superficie se abre al
seleccionar la pieza.
