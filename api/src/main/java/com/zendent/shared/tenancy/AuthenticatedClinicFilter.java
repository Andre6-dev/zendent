package com.zendent.shared.tenancy;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.zendent.shared.domain.ErrorMessages;
import com.zendent.shared.web.ProblemDetailWriter;

/**
 * Applies the Clinic named by the authenticated token, overriding whatever the
 * subdomain resolved to.
 *
 * <p>The claim governs because it is signed; the Host header is not, and a
 * caller controls it freely. Deriving the scope from the token is what makes it
 * trustworthy.
 *
 * <p>When the two disagree the request is refused outright rather than served
 * under either Clinic. A disagreement is a bug or a token aimed at a Clinic that
 * did not issue it, and silently preferring one side would turn it into a leak
 * in whichever direction the precedence rule happened to point.
 *
 * <p>Runs after authentication, so an anonymous request keeps the Clinic the
 * subdomain gave it — which is what lets login find its Membership.
 */
@Component
public final class AuthenticatedClinicFilter extends OncePerRequestFilter {

	static final String CLINIC_CLAIM = "clinic_id";

	private static final ObjectMapper PROBLEM_DETAIL_MAPPER = new ObjectMapper();

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Optional<UUID> tokenClinic = authenticatedClinic();
		if (tokenClinic.isEmpty()) {
			chain.doFilter(request, response);
			return;
		}

		Optional<UUID> subdomainClinic = TenantContext.get();
		if (subdomainClinic.isPresent() && !subdomainClinic.get().equals(tokenClinic.get())) {
			TenantContext.clear();
			ProblemDetailWriter.write(response, PROBLEM_DETAIL_MAPPER, HttpStatus.FORBIDDEN,
					ErrorMessages.CLINIC_MISMATCH);
			return;
		}

		TenantContext.set(tokenClinic.get());
		try {
			chain.doFilter(request, response);
		}
		finally {
			// Restore what the subdomain filter left, so this filter owns the
			// lifetime of only what it set.
			subdomainClinic.ifPresentOrElse(TenantContext::set, TenantContext::clear);
		}
	}

	private static Optional<UUID> authenticatedClinic() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
			return Optional.empty();
		}

		Jwt token = jwtAuthentication.getToken();
		return Optional.ofNullable(token.getClaimAsString(CLINIC_CLAIM)).map(UUID::fromString);
	}

}
