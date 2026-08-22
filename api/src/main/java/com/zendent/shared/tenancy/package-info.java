/**
 * Clinic-isolation infrastructure. Provides the active {@code TenantContext}
 * and publishes it to PostgreSQL at each transaction boundary. Hibernate tenant
 * resolution and request filters are populated in PKG-2.3.
 */
package com.zendent.shared.tenancy;
