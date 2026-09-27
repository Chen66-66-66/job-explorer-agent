package io.github.jobexplorer.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.jobexplorer.domain.Company;
import io.github.jobexplorer.domain.Enums.RequirementOrigin;
import io.github.jobexplorer.domain.Enums.RequirementType;
import io.github.jobexplorer.domain.Requirement;

class RequirementServiceTest {

	private Requirement english(String quote, Double minScore) {
		Requirement r = new Requirement(new Company("测试公司"), RequirementType.ENGLISH, RequirementOrigin.LLM, "英语");
		r.setQuote(quote);
		r.setMinScore(minScore);
		return r;
	}

	@Test
	void 引用真实但分数读错_能被发现() {
		// Codex 复现的情况：原文 500 分，模型解析成 200 分
		assertThat(RequirementService.numberMismatch(english("英语六级不少于500分", 200.0))).contains("分数 200");
		assertThat(RequirementService.numberMismatch(english("英语六级不少于500分", 500.0))).isNull();
	}

	@Test
	void 小数分数按数值比较() {
		assertThat(RequirementService.numberMismatch(english("雅思6.0及以上", 6.0))).isNull();
		assertThat(RequirementService.numberMismatch(english("雅思6.5及以上", 6.0))).isNotNull();
	}

	@Test
	void 日期的年月日都要出现在引用里() {
		Requirement r = new Requirement(new Company("测试公司"), RequirementType.DEADLINE, RequirementOrigin.LLM, "截止");
		r.setQuote("报名截止日期：2026年11月15日");
		r.setMaxDate(LocalDate.of(2026, 11, 15));
		assertThat(RequirementService.numberMismatch(r)).isNull();
		r.setMaxDate(LocalDate.of(2026, 11, 5));
		assertThat(RequirementService.numberMismatch(r)).contains("日期");
	}

	@Test
	void 日期解析失败要记下来_不能当作没有限制() {
		List<String> issues = new ArrayList<>();
		assertThat(RequirementService.parseDate("2026年1月", "起始日期", issues)).isNull();
		assertThat(issues).hasSize(1);
		assertThat(RequirementService.parseDate("", "起始日期", issues)).isNull();
		assertThat(issues).hasSize(1);
	}
	private Requirement cohort(String quote, String years) {
		Requirement r = new Requirement(new Company("测试公司"), RequirementType.GRADUATION_COHORT, RequirementOrigin.LLM, "届别");
		r.setQuote(quote);
		r.setListValues(years);
		return r;
	}

	@Test
	void 届别年份抽错_能被发现() {
		assertThat(RequirementService.numberMismatch(cohort("面向2026届高校毕业生", "2027"))).contains("届别 2027");
		assertThat(RequirementService.numberMismatch(cohort("面向2027届高校毕业生", "2027届"))).isNull();
	}

	@Test
	void 届别多个年份_逐个核对() {
		assertThat(RequirementService.numberMismatch(cohort("2026届、2027届毕业生均可报名", "2026、2027"))).isNull();
		assertThat(RequirementService.numberMismatch(cohort("2027届毕业生", "2026、2027"))).contains("届别 2026");
	}

	@Test
	void 届别年份格式不对_即使没有引用也要报告() {
		assertThat(RequirementService.numberMismatch(cohort(null, "27届"))).contains("格式不正确");
		assertThat(RequirementService.numberMismatch(cohort("27届毕业生", "27"))).contains("格式不正确");
	}
}
