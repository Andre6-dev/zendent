# El aislamiento entre clínicas se aplica en dos capas: @TenantId y RLS

`@TenantId` de Hibernate filtra por `clinic_id` en el ORM, y por debajo Postgres
aplica Row-Level Security sobre las mismas tablas. La aplicación se conecta con
un rol que no es propietario de las tablas, las políticas viven en las
migraciones Flyway con `FORCE ROW LEVEL SECURITY`, y un hook de transacción
ejecuta `SET LOCAL app.clinic_id` al inicio de cada una.

No es redundancia: cubren fallos distintos. `@TenantId` da ergonomía pero solo
protege lo que pasa por Hibernate — una consulta nativa, un job de
mantenimiento, un script de migración de datos o una sesión `psql` contra
producción lo esquivan por completo. RLS lo aplica el motor y no admite
excepciones. En un producto de salud para múltiples clínicas, que los datos de
una clínica aparezcan en otra es el peor fallo posible y el más caro de explicar.

## Consecuencias

Toda tabla nueva propiedad de una Clinic debe nacer con su política RLS en la
misma migración que la crea. Una tabla propiedad de una Clinic sin política
queda desprotegida en silencio, y por eso el test de aislamiento entre clínicas
debe cubrir cada una de esas tablas, no una muestra.
