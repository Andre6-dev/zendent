# Las citas son mutables, con historial de estados, y se permite sobreagendar

La cita no es historia clínica, así que el ADR 0004 no le aplica: reprogramar
muta la cita en vez de crear una nueva. Pero cada transición de estado
(registrada → confirmada → atendida / cancelada / no-show) se guarda en una
tabla de historial con timestamp y autor. La disponibilidad se modela como
horario semanal por odontólogo más una tabla de excepciones para vacaciones y
bloqueos puntuales. **Agendar dos citas en el mismo hueco está permitido**, con
una advertencia visible, no bloqueado.

Impedir el sobreagendamiento parece obviamente correcto y es un error: las
clínicas lo hacen a propósito para absorber ausencias, y un sistema que se lo
prohíbe es un sistema que dejan de usar. Se documenta aquí precisamente para que
nadie lo "arregle" más adelante. El historial de estados, por su parte, es lo
que permite medir la tasa de ausencias por paciente y por odontólogo — un
reporte que las clínicas valoran — y no se puede reconstruir hacia atrás.

## Consecuencias

Con un solo odontólogo (ADR 0005) el calendario deja de necesitar columnas por
profesional, pero el modelo de disponibilidad es el mismo.
