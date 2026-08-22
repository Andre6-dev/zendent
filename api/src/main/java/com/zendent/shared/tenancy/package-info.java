/**
 * Clinic-isolation infrastructure. Provides the active {@code TenantContext}
 * and publishes it to PostgreSQL at each transaction boundary. Hibernate tenant
 * resolution fails closed when the context is absent, and request-host
 * classification is centralized for onboarding and the future subdomain
 * filter. Request filters are populated in PKG-2.3.
 */
package com.zendent.shared.tenancy;
