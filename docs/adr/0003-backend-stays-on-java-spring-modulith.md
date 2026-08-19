# El backend se queda en Java + Spring Modulith, no migra a Go

Se evaluó reescribir el backend en Go y se decidió no hacerlo. La evaluación
era razonable: el backend Java son ~760 LOC de andamiaje sin lógica de dominio,
por lo que descartarlo costaba días, y existe un backend Go propio de 25k LOC
(`lume-app/backend`, Gin + pgx) con exactamente esta forma de problema.

Pesó en contra el motivo que originó la pregunta — el coste — porque no
resistía los números: la diferencia real de RAM entre una JVM y un binario Go
es de unos $10–25/mes en un PaaS por tramos, y de cero en un VPS, mientras que
el gasto dominante de este producto son Postgres gestionado y el
almacenamiento de las fotos clínicas. A favor de Java pesaron el `@TenantId` de
Hibernate, que filtra por `clinic_id` sin que cada consulta tenga que
acordarse — en una app de salud multi-tenant la fuga entre clínicas es el peor
fallo posible — y la verificación de límites de módulo de Spring Modulith en
tiempo de build.

## Consecuencias

El aislamiento entre clínicas depende de que todo acceso a datos pase por
Hibernate. Cualquier consulta nativa, script de mantenimiento o job que
esquive el ORM queda fuera de esa protección y debe filtrar a mano.
