# El acceso clínico es amplio dentro de la clínica, pero queda auditado

El rol STAFF (recepción) no puede abrir el odontograma ni las anotaciones
clínicas: ve al paciente como agenda, contacto y saldo, nada más. Los roles
DENTIST y ADMIN ven **todas** las historias clínicas de su Clinic, no solo las
de los pacientes que cada uno atiende.

Restringir a cada odontólogo a "sus" pacientes suena más seguro y rompe el
trabajo real: cubrir una urgencia, sustituir a un colega de vacaciones, hacer
una interconsulta. La protección correcta aquí no es estrechar el acceso sino
hacerlo rendir cuentas, y eso ya lo da el log de lecturas del ADR 0010: quien
abre una historia que no le corresponde deja rastro con nombre y fecha. La
restricción a STAFF sí se mantiene porque su trabajo nunca requiere el dato
clínico, y no poder verlo también la protege a ella.

## Consecuencias

Cualquier pantalla nueva que muestre dato clínico debe quedar fuera del alcance
de STAFF por defecto, y debe emitir el evento de auditoría de lectura. Una
pantalla que muestre historia clínica sin auditar es un fallo, no una omisión.
