package com.zendent.shared.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClinicHostClassifierTest {

	private final ClinicHostClassifier classifier = new ClinicHostClassifier("zendent.app");

	@Test
	void readsTheClinicSlugFromASubdomain() {
		assertThat(classifier.clinicSlug("acme.zendent.app")).contains("acme");
	}

	@Test
	void readsNoSlugFromTheApexOrAReservedLabel() {
		assertThat(classifier.clinicSlug("zendent.app")).isEmpty();
		assertThat(classifier.clinicSlug("app.zendent.app")).isEmpty();
		assertThat(classifier.clinicSlug("www.zendent.app")).isEmpty();
		assertThat(classifier.clinicSlug("api.zendent.app")).isEmpty();
	}

	@Test
	void readsNoSlugFromAHostOutsideTheBaseDomain() {
		assertThat(classifier.clinicSlug("acme.example.com")).isEmpty();
		assertThat(classifier.clinicSlug("deep.acme.zendent.app")).isEmpty();
	}

	@Test
	void readsTheSlugCaseInsensitively() {
		assertThat(classifier.clinicSlug("ACME.Zendent.App")).contains("acme");
	}

}
