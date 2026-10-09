package io.github.jobexplorer.service;

import io.github.jobexplorer.domain.Enums.PendingKind;
import io.github.jobexplorer.domain.Enums.RequirementOrigin;
import io.github.jobexplorer.domain.Enums.RequirementType;
import io.github.jobexplorer.domain.Enums.Verdict;
import io.github.jobexplorer.domain.Requirement;

/**
 * 一条条件的判断结果，连同原文出处与确认状态一起返回，便于用户核对。
 * ruleVerdict 是规则按档案算出的原始结论（把关降级之前），用来发现「确认后会被排除」的岗位；
 * pending 只在结论为「待核实」时有值，说明待核实的原因。
 */
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
		String parseIssue,
		Verdict ruleVerdict,
		PendingKind pending) {

	/** 确定结论（符合 / 不符合 / 不适用）。 */
	static CheckResult of(Requirement r, Verdict verdict, String reason) {
		return of(r, verdict, reason, null);
	}

	static CheckResult unknown(Requirement r, PendingKind pending, String reason) {
		return of(r, Verdict.UNKNOWN, reason, pending);
	}

	private static CheckResult of(Requirement r, Verdict verdict, String reason, PendingKind pending) {
		return new CheckResult(r.getId(), r.getType(), r.getOrigin(), r.isCompanyLevel(), r.getDescription(), verdict,
				reason, r.getQuote(), r.isQuoteVerified(), r.isConfirmed(), r.isAppliesToAllPositions(),
				r.getAppliesTo(), r.getAlternativeGroup(), r.getParseIssue(), verdict, pending);
	}

	/** 把关降级为「待核实」：保留规则的原始结论，记录降级原因。 */
	CheckResult downgrade(PendingKind newPending, String newReason) {
		return new CheckResult(requirementId, type, origin, companyLevel, description, Verdict.UNKNOWN, newReason, quote,
				quoteVerified, confirmed, appliesToAllPositions, appliesTo, alternativeGroup, parseIssue, ruleVerdict,
				newPending);
	}

	CheckResult withReason(String newReason) {
		return new CheckResult(requirementId, type, origin, companyLevel, description, verdict, newReason, quote,
				quoteVerified, confirmed, appliesToAllPositions, appliesTo, alternativeGroup, parseIssue, ruleVerdict, pending);
	}
}
