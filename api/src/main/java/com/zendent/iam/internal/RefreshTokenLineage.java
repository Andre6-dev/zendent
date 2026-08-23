package com.zendent.iam.internal;

import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ends every session descended from one refresh token.
 *
 * <p>Rotation links each token to the one it replaced, so a lineage is the
 * transitive closure over that link — walk up to the root, then back down over
 * every rotation. Reached only when a spent token is replayed, which means
 * either the token was stolen or the chain is compromised; there is no way to
 * tell which, and no safe way to guess, so the whole lineage goes.
 *
 * <p>Expressed as SQL because the closure is one recursive query rather than a
 * traversal in Java. Row-level security still applies: the statement runs in the
 * request's transaction, where the active Clinic is already set.
 */
@Component
class RefreshTokenLineage {

	private static final String REVOKE_LINEAGE = """
			WITH RECURSIVE ancestors AS (
			    SELECT id, rotated_from FROM refresh_token WHERE id = ?
			    UNION
			    SELECT parent.id, parent.rotated_from
			    FROM refresh_token parent
			    JOIN ancestors child ON child.rotated_from = parent.id
			),
			root AS (
			    SELECT id FROM ancestors WHERE rotated_from IS NULL
			),
			lineage AS (
			    SELECT id FROM root
			    UNION
			    SELECT descendant.id
			    FROM refresh_token descendant
			    JOIN lineage forebear ON descendant.rotated_from = forebear.id
			)
			UPDATE refresh_token
			SET revoked_at = ?
			WHERE id IN (SELECT id FROM lineage) AND revoked_at IS NULL
			""";

	private final JdbcTemplate jdbcTemplate;

	RefreshTokenLineage(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	void revokeAllRelatedTo(UUID tokenId, Instant moment) {
		jdbcTemplate.update(REVOKE_LINEAGE, tokenId, java.sql.Timestamp.from(moment));
	}

}
