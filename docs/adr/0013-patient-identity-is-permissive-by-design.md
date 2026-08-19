# La identidad del paciente es deliberadamente permisiva

Un `Patient` pertenece a una `Clinic`: el mismo paciente atendido en dos
clínicas del sistema son dos registros independientes que no se conocen entre
sí. El documento de identidad (DNI, carné de extranjería, pasaporte) es
**opcional**, y único dentro de la clínica solo cuando existe. Los duplicados no
se bloquean: al coincidir el documento, o el nombre con la fecha de nacimiento,
el sistema avisa y deja decidir a quien da el alta. Un paciente menor de edad
lleva un apoderado o tutor legal asociado, que es quien queda registrado
firmando el Consent.

La separación por clínica la impone el aislamiento del ADR 0008. Lo permisivo
del documento es deliberado y va a parecer un descuido: exigirlo rompe el alta
de un niño sin DNI —que el ADR 0001 obliga a soportar al aceptar dentición
temporal— y el alta de una urgencia, que son casos frecuentes, no excepciones.
Bloquear duplicados por sistema falla igual de mal: dos personas comparten
nombre, y la misma persona vuelve años después con otro apellido.

## Consecuencias

Sin el apoderado, el consentimiento informado de un menor no tiene validez, de
modo que el flujo de alta debe pedirlo cuando la fecha de nacimiento indica
minoría de edad.
