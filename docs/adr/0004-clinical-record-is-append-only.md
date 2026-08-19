# La historia clínica es append-only con autoría

Las anotaciones clínicas y las entradas del odontograma nunca se actualizan ni
se borran. Cada registro lleva autor y timestamp, y una corrección se guarda
como una entrada nueva que supersede a la anterior, quedando ambas visibles.

La historia clínica es un documento legal ante una queja o una demanda, y si un
odontólogo puede reescribir retroactivamente lo que anotó, la historia no prueba
nada. Se descartó el event sourcing completo del módulo clínico por coste
desproporcionado: append-only con supersesión da la misma garantía probatoria
con un modelo relacional normal.

## Consecuencias

El odontograma se lee reconstruyendo el "estado a fecha X" a partir de las
entradas vigentes, lo que además habilita mostrar la evolución del paciente en
el tiempo. Esta regla aplica **solo** al módulo clínico: pacientes, citas,
inventario y facturación son CRUD mutable normal.
