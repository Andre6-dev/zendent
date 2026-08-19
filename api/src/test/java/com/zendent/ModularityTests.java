package com.zendent;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Architecture test verifying Spring Modulith module boundaries and generating
 * module documentation. Plain JUnit test — no Spring context needed, so it is
 * fast and always runs as part of {@code ./mvnw test}.
 */
class ModularityTests {

	private final ApplicationModules modules = ApplicationModules.of(ApiApplication.class);

	@Test
	void verifiesModuleStructure() {
		modules.verify();
	}

	@Test
	void writesDocumentation() {
		new Documenter(modules).writeDocumentation();
	}

}
