/**
 * The {@code shared} module: cross-cutting infrastructure (tenancy, domain
 * events, error handling, and shared value objects) that any other module may
 * freely depend on. Marked {@code OPEN} so it never needs an explicit
 * {@code allowedDependencies} entry.
 *
 * This module holds ONLY infrastructure and published contracts — never
 * business logic.
 */
@org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.zendent.shared;
