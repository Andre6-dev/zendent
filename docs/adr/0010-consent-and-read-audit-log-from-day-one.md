# Consentimiento versionado y log de auditoría de lecturas desde el día uno

De las obligaciones que impone tratar datos de salud, dos entran en la primera
versión: el consentimiento informado del paciente, guardado con la versión
exacta del texto que aceptó y la fecha, y un log de auditoría que registra cada
**lectura** de una historia clínica, no solo cada escritura. El cifrado en
reposo y en tránsito se delega al proveedor gestionado y a TLS. La política de
retención queda pendiente de confirmación legal.

Estos dos son los únicos que no se pueden retrofitear. Si el log de lecturas se
añade dentro de un año, no hay forma de reconstruir quién consultó qué historia
durante el primer año — y esa es exactamente la pregunta tras un incidente.
Con el consentimiento pasa igual: hace falta saber qué texto concreto aceptó
cada paciente, no el texto que esté vigente hoy.

## Consecuencias

El derecho de supresión del titular colisiona con el ADR 0004 (append-only) y
con la obligación de conservar la historia clínica. Hasta que un abogado
confirme el plazo de retención y el alcance real de los derechos ARCO sobre
historia clínica en Perú, **el sistema conserva todo y no borra nada**. Esta
decisión está tomada a falta de asesoría legal, no con ella.
