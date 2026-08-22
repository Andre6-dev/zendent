package com.zendent.shared.tenancy;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves a Clinic slug to its identifier, before any Clinic is active.
 *
 * <p>The Clinic registry is globally scoped rather than tenant-owned precisely
 * so this lookup can succeed with no Clinic set — it is the bootstrap that lets
 * a request discover which Clinic it belongs to in the first place.
 *
 * <p>Declared here rather than in the module that owns the Clinic so tenancy
 * infrastructure stays independent of it: {@code shared} is depended upon, and
 * never depends back.
 */
public interface ClinicDirectory {

	Optional<UUID> findIdBySlug(String slug);

}
