# Un consultorio individual es una Clinic con un solo miembro

No existe un segundo tipo de tenant para el odontólogo que trabaja solo. Es una
`Clinic` con un único `Membership` que acumula los roles de administrador y
odontólogo; lo que cambia es la UI, no el modelo.

Los consultorios de una sola persona son aproximadamente la mitad del mercado
objetivo, y modelarlos aparte obligaría a duplicar cada funcionalidad futura
para los dos tipos, para siempre. Con un único modelo, el día que ese
odontólogo contrate a alguien se convierte en clínica sin ninguna migración.

## Consecuencias

La UI se adapta al número de miembros: con un solo odontólogo el calendario
abandona la vista resource-timeline (columnas por profesional) y pasa a vista
de día simple, y se ocultan Staff List y la gestión de roles.
