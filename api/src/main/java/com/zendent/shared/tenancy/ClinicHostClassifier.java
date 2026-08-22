package com.zendent.shared.tenancy;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for classifying request hosts before a Clinic can be
 * resolved. The onboarding endpoint and the subdomain resolution filter share
 * this policy so they can never disagree about what a host means.
 */
@Component
public final class ClinicHostClassifier {

	private static final Set<String> RESERVED_LABELS = Set.of("app", "www", "api");

	private final String baseDomain;

	ClinicHostClassifier(@Value("${zendent.tenant.base-domain}") String baseDomain) {
		this.baseDomain = baseDomain.toLowerCase(Locale.ROOT);
	}

	public HostKind classify(String serverName) {
		return label(serverName)
			.map(label -> RESERVED_LABELS.contains(label) ? HostKind.APEX_OR_RESERVED : HostKind.CLINIC)
			.orElseGet(() -> normalize(serverName).equals(baseDomain) ? HostKind.APEX_OR_RESERVED : HostKind.INVALID);
	}

	/**
	 * The Clinic slug this host names, or empty when the host names no Clinic —
	 * the apex, a reserved label, or anything outside the base domain.
	 */
	public Optional<String> clinicSlug(String serverName) {
		return label(serverName).filter(label -> !RESERVED_LABELS.contains(label));
	}

	/**
	 * The single label directly beneath the base domain, or empty when the host
	 * is the apex itself, sits outside the base domain, or nests deeper than one
	 * label — {@code deep.acme.zendent.app} names no Clinic.
	 */
	private Optional<String> label(String serverName) {
		String host = normalize(serverName);
		String suffix = "." + baseDomain;
		if (!host.endsWith(suffix)) {
			return Optional.empty();
		}

		String label = host.substring(0, host.length() - suffix.length());
		return label.contains(".") || label.isEmpty() ? Optional.empty() : Optional.of(label);
	}

	private static String normalize(String serverName) {
		return serverName.toLowerCase(Locale.ROOT);
	}

	public enum HostKind {
		APEX_OR_RESERVED,
		CLINIC,
		INVALID
	}

}
