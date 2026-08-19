# La suscripción SaaS se gestiona fuera de la aplicación

El cobro de Zendenta a cada clínica se hace manualmente —se factura aparte y se
activa el tenant a mano— sin pasarela de pago integrada ni entidades de
facturación SaaS en el modelo de dominio.

Por debajo de una quincena de clínicas, cobrar a mano es más rápido que integrar
Culqi, Mercado Pago o Stripe, y sobre todo mantiene el modelo limpio mientras el
producto aún se está validando. El riesgo real que se evita no es el del
esfuerzo sino el de la confusión: hay dos flujos de dinero distintos en este
producto y mezclarlos vuelve el módulo de finanzas inentendible.

## Consecuencias

El vocabulario los separa de forma permanente: `Subscription` es lo que la
Clinic paga a Zendenta; `Charge` es lo que el Patient paga a la Clinic. Estos
dos términos no deben cruzarse nunca en el mismo módulo.
