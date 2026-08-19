# Despliegue en región americana, con Postgres gestionado aparte

La aplicación corre en un VPS con Docker Compose y la base de datos va en un
Postgres gestionado con point-in-time recovery, ambos en región americana
(US-East o São Paulo). Se descarta explícitamente alojar en Europa, aunque sea
la opción más barata.

Los usuarios están en Perú. Desde Lima, un servidor en Alemania son unos 200ms
de latencia por petición frente a 60–80ms en Virginia o ~50ms en São Paulo, y
con una UI que hace muchas peticiones pequeñas esa diferencia es perceptible y
no se recupera después. El VPS se eligió sobre un PaaS porque el apetito de RAM
de la JVM sale gratis ahí y caro en un servicio que cobra por tramos. La base de
datos se separa y se paga gestionada porque es donde vive el riesgo legal:
con datos de salud, "tengo backups" y "he restaurado un backup con éxito" son
afirmaciones distintas y solo la segunda sirve ante un incidente.

## Consecuencias

El monorepo (`api/`, `webapp-zendent/`, `openspec/`) se mantiene como un único
repositorio.
