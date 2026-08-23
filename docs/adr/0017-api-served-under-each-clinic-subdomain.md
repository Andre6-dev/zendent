# La API se sirve bajo el subdominio de cada clínica, no en un host `api.`

El frontend y la API viven detrás del mismo host — `avicena.zendent.app` sirve
la aplicación y `avicena.zendent.app/api/*` la API — en lugar del reparto
habitual entre `app.zendent.app` y `api.zendent.app`. El servidor de TanStack
Start hace de BFF: recibe el login, guarda los tokens en cookies `httpOnly` y el
navegador nunca ve un token.

Lo obliga el mecanismo de tenancy. `ClinicHostClassifier` deduce la Clinic del
Host de la petición, y `api` es uno de sus `RESERVED_LABELS`: una llamada a
`api.zendent.app` no nombra ninguna Clinic y responde 404. El único escape,
la cabecera `X-Clinic-Slug`, solo se honra donde `dev-header-override-enabled`
está activo — local y tests — precisamente para que en producción nadie pueda
elegir su propia Clinic. Habilitarla en producción convertiría el aislamiento
entre clínicas en un valor que el llamante controla, que es justo lo que el ADR
0008 existe para impedir.

## Consideradas y descartadas

Tokens en el navegador con la API en host propio: el refresh token vive 30 días
(`refresh-token-ttl: P30D`), y guardar una llave de 30 días a una clínica en
`localStorage` la deja al alcance de cualquier XSS, en un producto que custodia
historia clínica. Mantenerlos solo en memoria evita eso pero tira la sesión en
cada recarga de página.

## Consecuencias

El despliegue tiene que resolver DNS con comodín y un certificado comodín sobre
el dominio base, y enrutar por path bajo cada subdominio de clínica. La API deja
de ser desplegable de forma independiente del frontend: comparten host, luego
comparten entrada. Y todo `loader` de SSR debe reenviar las cookies de la
petición entrante, porque en servidor no hay navegador que las adjunte solo.
