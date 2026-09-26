package io.github.jobexplorer.eval;

import java.time.LocalDate;
import java.util.List;

import io.github.jobexplorer.domain.Company;
import io.github.jobexplorer.domain.Enums.RequirementOrigin;
import io.github.jobexplorer.domain.Enums.RequirementType;
import io.github.jobexplorer.domain.Requirement;

/**
 * 一条测评用例：一份公告原文 + 人工确认过的标准答案（gold）。
 * split 用来区分：dev 可以用来调提示词；test 调试时不看，只在最后测。
 * asOf 是判断截止日等条件时使用的「今天」，固定下来保证结果可复现。
 */
public record EvalCase(String id, String split, String source, LocalDate asOf, String text,
		List<GoldRequirement> gold, String notes) {

	public EvalCase {
		gold = gold == null ? List.of() : List.copyOf(gold);
		asOf = asOf == null ? LocalDate.of(2026, 9, 26) : asOf;
		split = split == null ? "dev" : split;
	}

	/** 标准答案中的一条条件，字段含义与 Requirement 相同。 */
	public record GoldRequirement(RequirementType type, String description, String appliesTo,
			String alternativeGroup, String level, Double minScore, Boolean allowEquivalent, LocalDate minDate,
			LocalDate maxDate, Integer maxAge, Boolean ageStrict, LocalDate ageReferenceDate, String listValues) {

		/** 转成已确认、适用于全部岗位的人工条件，交给同一套规则判断。 */
		Requirement toRequirement(Company company) {
			Requirement r = new Requirement(company, type, RequirementOrigin.MANUAL,
					description == null ? type.name() : description);
			r.setAppliesTo(appliesTo);
			r.setAlternativeGroup(alternativeGroup);
			r.setLevel(level);
			r.setMinScore(minScore);
			r.setAllowEquivalent(Boolean.TRUE.equals(allowEquivalent));
			r.setMinDate(minDate);
			r.setMaxDate(maxDate);
			r.setMaxAge(maxAge);
			r.setAgeStrict(Boolean.TRUE.equals(ageStrict));
			r.setAgeReferenceDate(ageReferenceDate);
			r.setListValues(listValues);
			r.setAppliesToAllPositions(true);
			return r;
		}
	}
}
