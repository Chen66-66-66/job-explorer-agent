package io.github.jobexplorer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.jobexplorer.domain.Enums.CompanyStatus;
import io.github.jobexplorer.domain.Enums.RequirementOrigin;
import io.github.jobexplorer.domain.Enums.RequirementType;
import io.github.jobexplorer.repository.CompanyRepository;
import io.github.jobexplorer.repository.RequirementRepository;

/**
 * 旧数据库升级：用 main 分支（e62d426）生成的真实表结构建库并写入记录，再启动当前版本，
 * 验证原记录仍可读取、新条件类型可以写入。
 * 旧库的枚举列是 H2 ENUM 类型（取值固定）；实体改为 columnDefinition = "varchar(40)" 后，
 * Hibernate 的 ddl-auto=update 会把这些列转成字符串并保留原数据（对照实验：去掉该定义时，写入新类型会被拒绝）。
 */
@SpringBootTest(properties = { "jobexplorer.demo-data=false", "spring.ai.model.chat=none",
		"spring.datasource.url=" + SchemaUpgradeTest.URL })
class SchemaUpgradeTest {

	static final String URL = "jdbc:h2:mem:upgrade;DB_CLOSE_DELAY=-1";

	// 在 Spring 容器启动前（类加载时）建好「旧数据库」
	static {
		try (Connection c = DriverManager.getConnection(URL, "sa", ""); Statement st = c.createStatement()) {
			String ddl = new ClassPathResource("db/schema-main-e62d426.sql").getContentAsString(StandardCharsets.UTF_8);
			for (String sql : ddl.split(";\\s*\\n")) {
				String stmt = sql.lines().filter(l -> !l.startsWith("--")).reduce("", (a, b) -> a + b + "\n").trim();
				if (!stmt.isEmpty()) {
					st.execute(stmt);
				}
			}
			st.execute("INSERT INTO COMPANY(ID, NAME, STATUS) VALUES (1, '旧公司', 'INTERESTED')");
			st.execute("INSERT INTO REQUIREMENT(ID, COMPANY_ID, TYPE, ORIGIN, DESCRIPTION, AGE_STRICT, ALLOW_EQUIVALENT, "
					+ "APPLIES_TO_ALL_POSITIONS, CONFIRMED, QUOTE_VERIFIED, LEVEL) "
					+ "VALUES (1, 1, 'DEGREE', 'MANUAL', '硕士及以上', FALSE, FALSE, TRUE, TRUE, FALSE, '硕士')");
			st.execute("ALTER TABLE COMPANY ALTER COLUMN ID RESTART WITH 100");
			st.execute("ALTER TABLE REQUIREMENT ALTER COLUMN ID RESTART WITH 100");
		}
		catch (Exception e) {
			throw new IllegalStateException("准备旧数据库失败", e);
		}
	}

	@Autowired
	CompanyRepository companies;

	@Autowired
	RequirementRepository requirements;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void 旧库升级后_原记录可读_新条件类型可写() {
		Company old = companies.findById(1L).orElseThrow();
		assertThat(old.getName()).isEqualTo("旧公司");
		assertThat(old.getStatus()).isEqualTo(CompanyStatus.INTERESTED);
		Requirement oldReq = requirements.findById(1L).orElseThrow();
		assertThat(oldReq.getType()).isEqualTo(RequirementType.DEGREE);
		assertThat(oldReq.getLevel()).isEqualTo("硕士");

		Requirement cohort = new Requirement(old, RequirementType.GRADUATION_COHORT, RequirementOrigin.MANUAL, "2027届");
		cohort.setListValues("2027");
		Long id = requirements.save(cohort).getId();
		assertThat(requirements.findById(id).orElseThrow().getType()).isEqualTo(RequirementType.GRADUATION_COHORT);

	}

	@Test
	void 升级后枚举列是普通字符串_不再固定取值() {
		List<String> types = jdbc.queryForList(
				"SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE (TABLE_NAME, COLUMN_NAME) IN "
						+ "(('REQUIREMENT','TYPE'), ('REQUIREMENT','ORIGIN'), ('COMPANY','STATUS'), ('POSITION','STATUS'))",
				String.class);
		assertThat(types).hasSize(4).containsOnly("CHARACTER VARYING");
	}
}
