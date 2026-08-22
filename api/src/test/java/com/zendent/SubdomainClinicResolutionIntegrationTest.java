package com.zendent;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zendent.shared.tenancy.SubdomainClinicResolutionFilter;

import com.jayway.jsonpath.JsonPath;
import com.zendent.shared.tenancy.TenantContext;

/**
 * Issue #20: a request on a Clinic's subdomain activates that Clinic before
 * authentication runs. The probe endpoint below exists only in this test
 * context — until the member listing (#23) lands there is no production
 * surface that reads the active Clinic back out.
 */
@Import({ TestcontainersConfiguration.class, SubdomainClinicResolutionIntegrationTest.ActiveClinicProbe.class })
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SubdomainClinicResolutionIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@BeforeEach
	void clearClinic() {
		TenantContext.clear();
	}

	@Test
	void aRequestOnAClinicSubdomainActivatesThatClinic() throws Exception {
		String slug = registerClinic();
		UUID clinicId = UUID.fromString(JsonPath.read(lastRegistration, "$.clinicId"));

		mockMvc.perform(get("/__probe/active-clinic").with(host(slug + ".localhost")))
			.andExpect(status().isOk())
			.andExpect(content().string(clinicId.toString()));
	}

	@Test
	void aRequestOnTheApexActivatesNoClinic() throws Exception {
		mockMvc.perform(get("/__probe/active-clinic").with(host("localhost")))
			.andExpect(status().isOk())
			.andExpect(content().string("none"));
	}

	@Test
	void aRequestOnAReservedLabelActivatesNoClinic() throws Exception {
		for (String reserved : new String[] { "app", "www", "api" }) {
			mockMvc.perform(get("/__probe/active-clinic").with(host(reserved + ".localhost")))
				.andExpect(status().isOk())
				.andExpect(content().string("none"));
		}
	}

	@Test
	void onboardingStillSucceedsOnTheApexWithNoClinicActivated() throws Exception {
		mockMvc.perform(post("/auth/register").with(host("localhost"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(registrationBody(uniqueSlug())))
			.andExpect(status().isCreated());
	}

	@Test
	void aSubdomainMatchingNoClinicIsNotFound() throws Exception {
		mockMvc.perform(get("/__probe/active-clinic").with(host("nosuchclinic.localhost")))
			.andExpect(status().isNotFound());
	}

	@Test
	void aHostOutsideTheBaseDomainIsNotFound() throws Exception {
		mockMvc.perform(get("/__probe/active-clinic").with(host("acme.example.com")))
			.andExpect(status().isNotFound());
	}

	@Test
	void theDevelopmentOverrideNamesAClinicWithoutRealDns() throws Exception {
		String slug = registerClinic();
		UUID clinicId = UUID.fromString(JsonPath.read(lastRegistration, "$.clinicId"));

		mockMvc.perform(get("/__probe/active-clinic").with(host("localhost")).header("X-Clinic-Slug", slug))
			.andExpect(status().isOk())
			.andExpect(content().string(clinicId.toString()));
	}

	@Test
	void theDevelopmentOverrideCannotBeUsedToRegisterIntoAnExistingClinic() throws Exception {
		String existing = registerClinic();

		mockMvc.perform(post("/auth/register").with(host("localhost")).header("X-Clinic-Slug", existing)
				.contentType(MediaType.APPLICATION_JSON)
				.content(registrationBody(uniqueSlug())))
			.andExpect(status().isForbidden());
	}

	@Test
	void theActivatedClinicDoesNotLeakToTheNextRequestOnTheSameConnection() throws Exception {
		String slug = registerClinic();

		mockMvc.perform(get("/__probe/active-clinic").with(host(slug + ".localhost")))
			.andExpect(status().isOk());
		mockMvc.perform(get("/__probe/active-clinic").with(host("localhost")))
			.andExpect(status().isOk())
			.andExpect(content().string("none"));
	}

	private String lastRegistration;

	private String registerClinic() throws Exception {
		String slug = uniqueSlug();
		lastRegistration = mockMvc.perform(post("/auth/register").with(host("localhost"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(registrationBody(slug)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return slug;
	}

	private static String uniqueSlug() {
		return "clinic-" + UUID.randomUUID();
	}

	private static String registrationBody(String slug) {
		return """
				{
				  "clinicName": "Clinic %s",
				  "slug": "%s",
				  "adminEmail": "admin-%s@example.com",
				  "adminPassword": "correct-horse-battery-staple",
				  "adminFullName": "Ada Admin"
				}
				""".formatted(slug, slug, slug);
	}

	private static org.springframework.test.web.servlet.request.RequestPostProcessor host(String serverName) {
		return request -> {
			request.setServerName(serverName);
			return request;
		};
	}

	@TestConfiguration
	@RestController
	static class ActiveClinicProbe {

		@GetMapping("/__probe/active-clinic")
		String activeClinic() {
			return TenantContext.get().map(UUID::toString).orElse("none");
		}

		/**
		 * The application requires a session everywhere but onboarding and login
		 * (#22). This probe reads the Clinic the subdomain resolved to, before
		 * any session exists, so it declares its own anonymous chain rather than
		 * asking production config to make room for a test path.
		 */
		@Bean
		@Order(0)
		SecurityFilterChain probeFilterChain(HttpSecurity http,
				SubdomainClinicResolutionFilter subdomainClinicResolutionFilter) throws Exception {
			http.securityMatcher("/__probe/**")
				.csrf(AbstractHttpConfigurer::disable)
				.addFilterBefore(subdomainClinicResolutionFilter, AuthorizationFilter.class)
				.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
			return http.build();
		}

	}

}
