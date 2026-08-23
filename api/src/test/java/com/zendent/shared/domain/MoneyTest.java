package com.zendent.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;

import org.junit.jupiter.api.Test;

class MoneyTest {

	@Test
	void normalisesToTheCurrencysOwnScale() {
		assertThat(Money.of("120.5", "PEN").amount()).isEqualByComparingTo("120.50");
		assertThat(Money.of("120", "PEN").toString()).isEqualTo("PEN 120.00");
	}

	@Test
	void addsAndSubtractsWithoutBinaryFloatingPointError() {
		Money total = Money.of("0.1", "PEN").plus(Money.of("0.2", "PEN"));

		assertThat(total.amount()).isEqualByComparingTo("0.30");
		assertThat(total.minus(Money.of("0.30", "PEN")).isZero()).isTrue();
	}

	@Test
	void refusesToCombineDifferentCurrencies() {
		assertThatThrownBy(() -> Money.of("10", "PEN").plus(Money.of("10", "USD")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("PEN")
			.hasMessageContaining("USD");
	}

	@Test
	void multipliesByACountAndStaysChargeable() {
		assertThat(Money.of("33.33", "PEN").times(3).amount()).isEqualByComparingTo("99.99");
		// A half-céntimo cannot be charged, so it rounds to something that can.
		assertThat(new Money(new BigDecimal("0.005"), Currency.getInstance("PEN")).amount())
			.isEqualByComparingTo("0.01");
	}

	@Test
	void readsAndReportsMinorUnits() {
		assertThat(Money.ofMinorUnits(12050, "PEN")).isEqualTo(Money.of("120.50", "PEN"));
		assertThat(Money.zero("PEN").isZero()).isTrue();
	}

	@Test
	void ordersAndDetectsNegativeAmounts() {
		assertThat(Money.of("10", "PEN")).isGreaterThan(Money.of("9.99", "PEN"));
		assertThat(Money.of("-1", "PEN").isNegative()).isTrue();
		assertThatThrownBy(() -> Money.of("10", "PEN").compareTo(Money.of("10", "USD")))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
