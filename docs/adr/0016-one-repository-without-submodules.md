# El monorepo es un único repositorio, sin submódulos ni subtrees

`webapp-zendent/` y `api/` viven en un solo repositorio git plano. No hay
submódulos, no hay subtrees, y no los habrá para lo que se añada después.

El plan de `backend-foundations` abrió esto como una decisión pendiente —
absorber el frontend aplanando su historia frente a preservarla con
subtree o submodule — y la marcó como bloqueante: ninguna parte del backend
debía aterrizar hasta cerrarla, porque aplanar una historia es irreversible y
rehacerlo más tarde, con commits encima, es mucho peor que hacerlo al principio.

Resulta que nunca hubo dos historias que reconciliar. El frontend se construyó
dentro de este repositorio desde el commit inicial, así que no existía un `.git`
propio del que preservar nada, ni un backup que hacer. La disyuntiva era teórica
y la única opción real siempre fue la que ya está en el árbol.

## Consecuencias

Lo que se añada al producto —una app móvil, un servicio aparte— entra como un
directorio más de este repositorio, no como submódulo. Un cambio que toque
frontend y backend a la vez es un solo commit y una sola PR, que es lo que hace
legible un cambio de contrato entre ambos.

El precio se paga en el checkout y en CI: quien clone se lleva las dos partes
aunque solo trabaje en una, y los pipelines tienen que filtrar por rutas para no
reconstruirlo todo ante cualquier cambio. Es un coste conocido y aceptado.

Si algún día una parte necesita historia y ciclo de publicación propios, la
salida es extraerla con `git subtree split`, que conserva sus commits. No hay
nada en esta decisión que lo impida más adelante.
