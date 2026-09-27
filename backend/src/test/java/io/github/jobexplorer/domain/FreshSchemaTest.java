package io.github.jobexplorer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** 全新数据库：枚举列应为普通字符串，且没有把取值写死的约束，这样以后新增枚举取值不会被数据库拒绝。 */
@SpringBootTest(properties = { "jobexplorer.demo-data=false", "spring.ai.model.chat=none",
		"spring.datasource.url=jdbc:h2:mem:freshschema;DB_CLOSE_DELAY=-1" })
class FreshSchemaTest {

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void 新建数据库_枚举列为字符串且无取值约束() {
		List<String> types = jdbc.queryForList(
				"SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE (TABLE_NAME, COLUMN_NAME) IN "
						+ "(('REQUIREMENT','TYPE'), ('REQUIREMENT','ORIGIN'), ('COMPANY','STATUS'), ('POSITION','STATUS'))",
				String.class);
		assertThat(types).hasSize(4).containsOnly("CHARACTER VARYING");
		assertThat(jdbc.queryForList("SELECT CHECK_CLAUSE FROM INFORMATION_SCHEMA.CHECK_CONSTRAINTS", String.class))
			.noneMatch(c -> c.contains("'DEGREE'") || c.contains("'CANDIDATE'"));
	}
}
