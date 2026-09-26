package io.github.jobexplorer.service;

import io.github.jobexplorer.domain.Enums.RequirementOrigin;
import io.github.jobexplorer.domain.Enums.RequirementType;
import io.github.jobexplorer.domain.Enums.Verdict;
import io.github.jobexplorer.domain.Requirement;

/** 一条条件的判断结果，连同原文出处与确认状态一起返回，便于用户核对。 */
public record CheckResult(
		Long requirementId,
		RequirementType type,
		RequirementOrigin origin,
		boolean companyLevel,
		String description,
		Verdict verdict,
		String reason,
		String quote,
		boolean quoteVerified,
		boolean confirmed,
		boolean appliesToAllPositions,
		String appliesTo,
		String alternativeGroup,
		String parseIssue) {

	static CheckResult of(Requirement r, Verdict verdict, String reason) {
		return new CheckResult(r.getId(), r.getType(), r.getOrigin(), r.isCompanyLevel(), r.getDescription(), verdict,
				reason, r.getQuote(), r.isQuoteVerified(), r.isConfirmed(), r.isAppliesToAllPositions(),
				r.getAppliesTo(), r.getAlternativeGroup(), r.getParseIssue());
	}

	CheckResult withVerdict(Verdict newVerdict, String newReason) {
		return new CheckResult(requirementId, type, origin, companyLevel, description, newVerdict, newReason, quote,
				quoteVerified, confirmed, appliesToAllPositions, appliesTo, alternativeGroup, parseIssue);
	}
}
