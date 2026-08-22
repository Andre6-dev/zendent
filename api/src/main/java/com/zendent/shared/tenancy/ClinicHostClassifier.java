package com.zendent.shared.tenancy;

import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for classifying request hosts before a Clinic can be
 * resolved. The onboarding endpoint and the future subdomain resolution filter
 * share this policy.
 */
@Component
public final class ClinicHostClassifier {

	private static final Set<String> RESERVED_LABELS = Set.of("app", "www", "api");

	private final String baseDomain;

	ClinicHostClassifier(@Value("${zendent.tenant.base-domain}") String baseDomain) {
		this.baseDomain = baseDomain.toLowerCase(Locale.ROOT);
	}

	public HostKind classify(String serverName) {
		String host = serverName.toLowerCase(Locale.ROOT);
		if (host.equals(baseDomain)) {
			return HostKind.APEX_OR_RESERVED;
		}

		String baseDomainSuffix = "." + baseDomain;
		if (!host.endsWith(baseDomainSuffix)) {
			return HostKind.INVALID;
		}

		String label = host.substring(0, host.length() - baseDomainSuffix.length());
		if (label.contains(".")) {
			return HostKind.INVALID;
		}
		return RESERVED_LABELS.contains(label) ? HostKind.APEX_OR_RESERVED : HostKind.CLINIC;
	}

	public enum HostKind {
		APEX_OR_RESERVED,
		CLINIC,
		INVALID
	}

}
