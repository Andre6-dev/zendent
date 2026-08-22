package com.zendent.shared.tenancy;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.zendent.shared.domain.ErrorMessages;
import com.zendent.shared.web.ProblemDetailWriter;

/**
 * Activates the Clinic a request belongs to from the host it arrived on, before
 * authentication runs — the subdomain is the only Clinic signal available to a
 * caller who has not logged in yet.
 *
 * <p>A host naming no Clinic (the apex, a reserved label) passes through with
 * none activated, which is what lets Clinic onboarding work. A host naming a
 * Clinic that does not exist is a wrong address and answers not-found: passing
 * it through unresolved would render an empty database under fail-closed
 * policies, leaving the caller unable to tell a typo from a Clinic with no data.
 *
 * <p>The Clinic never outlives the request, so a pooled connection carries no
 * residue of the previous caller's Clinic into the next one.
 */
@Component
public final class SubdomainClinicResolutionFilter extends OncePerRequestFilter {

	/**
	 * Names a Clinic where real wildcard DNS is unavailable — local development
	 * and the test suite. Honoured only where the profile enables it, so it can
	 * never become a way to choose your own Clinic in production.
	 */
	static final String DEV_OVERRIDE_HEADER = "X-Clinic-Slug";

	private static final ObjectMapper PROBLEM_DETAIL_MAPPER = new ObjectMapper();

	private final ClinicHostClassifier hostClassifier;
	private final ClinicDirectory clinicDirectory;
	private final boolean devHeaderOverrideEnabled;

	SubdomainClinicResolutionFilter(ClinicHostClassifier hostClassifier, ClinicDirectory clinicDirectory,
			@Value("${zendent.tenant.dev-header-override-enabled:false}") boolean devHeaderOverrideEnabled) {
		this.hostClassifier = hostClassifier;
		this.clinicDirectory = clinicDirectory;
		this.devHeaderOverrideEnabled = devHeaderOverrideEnabled;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (hostClassifier.classify(request.getServerName()) == ClinicHostClassifier.HostKind.INVALID) {
			notFound(response);
			return;
		}

		Optional<String> slug = requestedSlug(request);
		if (slug.isEmpty()) {
			chain.doFilter(request, response);
			return;
		}

		Optional<UUID> clinicId = clinicDirectory.findIdBySlug(slug.get());
		if (clinicId.isEmpty()) {
			notFound(response);
			return;
		}

		TenantContext.set(clinicId.get());
		try {
			chain.doFilter(request, response);
		}
		finally {
			TenantContext.clear();
		}
	}

	private Optional<String> requestedSlug(HttpServletRequest request) {
		Optional<String> fromHost = hostClassifier.clinicSlug(request.getServerName());
		if (fromHost.isPresent() || !devHeaderOverrideEnabled) {
			return fromHost;
		}
		return Optional.ofNullable(request.getHeader(DEV_OVERRIDE_HEADER))
			.map(String::trim)
			.filter(slug -> !slug.isEmpty());
	}

	private static void notFound(HttpServletResponse response) throws IOException {
		ProblemDetailWriter.write(response, PROBLEM_DETAIL_MAPPER, HttpStatus.NOT_FOUND, ErrorMessages.UNKNOWN_CLINIC_ADDRESS);
	}

}
