package com.zendent.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * An amount of money.
 *
 * <p>Never a {@code double}: binary floating point cannot represent most decimal
 * amounts exactly, and a Charge that is a hundredth of a Sol wrong is a Charge
 * somebody has to explain. Amounts are held as {@link BigDecimal} normalised to
 * the currency's own scale.
 *
 * <p>Arithmetic between different currencies is refused rather than coerced.
 * There is no exchange rate here, and inventing one silently would be worse than
 * failing.
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

	public Money {
		Objects.requireNonNull(amount, "amount must not be null");
		Objects.requireNonNull(currency, "currency must not be null");
		amount = amount.setScale(currency.getDefaultFractionDigits(), RoundingMode.HALF_UP);
	}

	public static Money of(String amount, String currencyCode) {
		return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
	}

	public static Money zero(String currencyCode) {
		return of("0", currencyCode);
	}

	/** From the currency's smallest unit — céntimos for PEN, cents for USD. */
	public static Money ofMinorUnits(long minorUnits, String currencyCode) {
		Currency currency = Currency.getInstance(currencyCode);
		return new Money(BigDecimal.valueOf(minorUnits, currency.getDefaultFractionDigits()), currency);
	}

	public Money plus(Money other) {
		return new Money(amount.add(sameCurrencyAs(other).amount()), currency);
	}

	public Money minus(Money other) {
		return new Money(amount.subtract(sameCurrencyAs(other).amount()), currency);
	}

	/**
	 * Multiplies by a count — a Procedure performed three times. Rounds at the
	 * currency's scale, so the result is always an amount that can be charged.
	 */
	public Money times(int quantity) {
		return new Money(amount.multiply(BigDecimal.valueOf(quantity)), currency);
	}

	public boolean isNegative() {
		return amount.signum() < 0;
	}

	public boolean isZero() {
		return amount.signum() == 0;
	}

	@Override
	public int compareTo(Money other) {
		return amount.compareTo(sameCurrencyAs(other).amount());
	}

	@Override
	public String toString() {
		return currency.getCurrencyCode() + " " + amount.toPlainString();
	}

	private Money sameCurrencyAs(Money other) {
		Objects.requireNonNull(other, "other must not be null");
		if (!currency.equals(other.currency())) {
			throw new IllegalArgumentException(
					"Cannot combine " + currency.getCurrencyCode() + " with " + other.currency().getCurrencyCode());
		}
		return other;
	}

}
