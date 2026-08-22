package com.zendent.shared.tenancy;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;

@Component
final class ClinicTransactionListener implements TransactionExecutionListener {

	private final JdbcTemplate jdbcTemplate;

	ClinicTransactionListener(DataSource dataSource) {
		this.jdbcTemplate = new JdbcTemplate(dataSource);
	}

	@Override
	public void afterBegin(TransactionExecution transaction, Throwable beginFailure) {
		if (beginFailure != null) {
			return;
		}

		TenantContext.get().ifPresent(clinicId -> jdbcTemplate.queryForObject(
				"SELECT set_config('app.clinic_id', ?, true)",
				String.class,
				clinicId.toString()));
	}

}
