# Flujo de trabajo multiagente

> Cómo dividir y asignar el trabajo en paquetes (PKG) para ejecutarlo con varios agentes en paralelo. Código en inglés.

## Principios

1. **Contratos primero.** Antes de implementar consumidores, se fija el contrato:
   - **Frontend:** schemas Zod en `features/<feature>/schemas.ts` = forma de los datos.
   - **Backend:** DTOs + OpenAPI (Springdoc). Frontend y backend comparten la misma forma.
2. **Mock → API sin reescritura.** El frontend de Fase 1 usa `features/<feature>/queries.ts` con `queryFn` mock. Migrar a la API real = cambiar solo el `queryFn`. Los componentes no cambian.
3. **Acoplamiento solo por eventos.** Entre módulos backend, el único acoplamiento permitido son los **eventos de dominio** documentados en `shared`. `ApplicationModules.verify()` lo garantiza.
4. **Un paquete = una unidad asignable.** Cada PKG tiene objetivo, archivos, dependencias y DoD claros. Respetar las dependencias para paralelizar sin colisiones.

## Definition of Done (DoD) por paquete

- **Frontend:** `bun run lint`, `bun run check` y `bun run build` verdes; sin errores de hidratación SSR; revisado con `heroui-pro-design-taste` y `vercel-react-best-practices`.
- **Backend:** `./gradlew build` con Testcontainers verde; `ApplicationModules.verify()` verde; endpoints en Swagger; tests de aislamiento de tenant cuando aplique.
- **Ambos:** breve nota de "cómo probarlo" en el PR.

## Cómo asignar a agentes

- **Paralelizar dentro de una fase** según el grafo de dependencias del documento de la fase (p. ej. en Fase 1: 1.1 primero; luego 1.2 y 1.3 en paralelo; luego 1.4; luego 1.5).
- **Un agente por módulo** en Fases 3–4 (cada módulo backend + su feature frontend).
- **Evitar solapamiento de archivos:** dos agentes no deben editar los mismos archivos a la vez. Las carpetas `features/<feature>/`, `components/<feature>/` y los módulos `com.zendenta.<module>` aíslan el trabajo.
- **Orden global recomendado:** Fase 1 (entregable visual) → Fase 2 (fundaciones) → Fase 3 → Fase 4 → Fase 5.

## Convención de ramas y commits (cuando se pida)

- Rama por paquete: `feat/pkg-1.2-app-layout`, `feat/pkg-3.2-patients`, etc.
- Commits y PRs en inglés. No commitear en la rama por defecto sin pedirlo.

## Estado del plan

| Fase | Documento                                            | Paquetes                  |
| ---- | ---------------------------------------------------- | ------------------------- |
| 1    | [phase-1](./phase-1-frontend-layout-reservations.md) | 1.1 → 1.2/1.3 → 1.4 → 1.5 |
| 2    | [phase-2](./phase-2-backend-foundations.md)          | 2.1 → 2.2/2.3             |
| 3    | [phase-3](./phase-3-core-modules.md)                 | 3.1, 3.2, 3.3, 3.4        |
| 4    | [phase-4](./phase-4-finance-assets.md)               | 4.1, 4.2, 4.3, 4.4        |
| 5    | [phase-5](./phase-5-cross-cutting.md)                | 5.1, 5.2, 5.3, 5.4        |
