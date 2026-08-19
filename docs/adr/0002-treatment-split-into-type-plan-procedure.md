# "Treatment" se descompone en TreatmentType, TreatmentPlan y Procedure

El mockup y el plan inicial usaban "Treatment" para tres cosas distintas a la
vez: el servicio del catálogo con su precio, el conjunto de trabajos
presupuestados a un paciente, y el trabajo concreto ejecutado sobre un diente.
Las separamos en tres entidades con nombres propios.

Tienen ciclos de vida incompatibles. Un TreatmentType cambia de precio sin que
eso deba alterar presupuestos ya emitidos. Un TreatmentPlan se acepta, se
rechaza o queda a medias, y debe conservar el precio vigente cuando se propuso.
Un Procedure ocurre una vez, en un Appointment concreto, y es el único de los
tres que modifica el odontograma y genera un cargo. Mantenerlos fusionados
obliga a que una sola tabla sirva a tres relojes distintos, y hace imposible
responder "¿qué se le presupuestó y qué se le llegó a hacer?" — que es
exactamente la pregunta que se le hace a una historia clínica en una disputa.

## Consecuencias

El TreatmentPlan copia (no referencia) el precio y la descripción del
TreatmentType al momento de emitirse. La UI puede seguir rotulando la sección
del sidebar como "Treatments"; el desglose vive por debajo.
