package io.github.jobexplorer.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.jobexplorer.domain.Enums.MaterialResolution;
import io.github.jobexplorer.domain.Enums.PendingKind;
import io.github.jobexplorer.domain.Enums.ReviewStatus;
import io.github.jobexplorer.domain.Enums.TodoKind;
import io.github.jobexplorer.domain.Enums.Verdict;
import io.github.jobexplorer.service.AssessmentService.Assessment;

/**
 * 把逐条判断结果和材料情况汇总成「一个主要状态 + 多项待办」。不访问数据库，便于测试。
 *
 * 原则：
 * - 没查到足够信息就叫「待核查」，不把「没发现不符合」说成「初筛匹配」；
 * - 确认后会导致排除的条件，归为「拟排除·待确认」，不提前隐藏；
 * - 待办清空只是「已核实可报」的必要条件：还要所有适用条件已确认满足，并完成投递前核对。
 */
public final class PositionReviewer {

	/** 岗位原文中提示「还有别的材料」的说法。 */
	private static final Pattern REFERENCE = Pattern
		.compile("(?:详见|参见|见附件)[^。；;\\n]{0,20}|附件[^。；;，,\\n]{0,12}|简章|岗位表|招聘公告|公告要求");

	private PositionReviewer() {
	}

	/** 材料情况：是否有岗位原文、原文里引用了哪些材料、本人的确认结果、公司已核查的公告。 */
	public record MaterialState(boolean hasJdText, List<String> references, MaterialResolution resolution,
			String resolutionNote, List<String> checkedAnnouncements) {
	}

	public record Todo(TodoKind kind, String text, Long requirementId) {
	}

	/**
	 * @param verifiedConditions 已确认满足的条件（核查过的部分）
	 * @param unverifiedPositives 规则判为满足（或不适用）、但抽取尚未人工确认的条件
	 * @param readyForPreApplyCheck 所有适用条件已确认满足、没有待办，可以做投递前核对
	 */
	public record PositionReview(ReviewStatus status, String statusReason, List<String> verifiedConditions,
			List<String> unverifiedPositives, List<Todo> todos, String materialBasis, LocalDate deadline,
			Long daysLeft, boolean readyForPreApplyCheck, Instant preApplyCheckedAt) {
	}

	/** 找出岗位原文中引用其他材料的说法，如「详见招聘公告」「见附件岗位表」。 */
	public static List<String> findReferences(String jdText) {
		List<String> found = new ArrayList<>();
		if (jdText == null) {
			return found;
		}
		Matcher m = REFERENCE.matcher(jdText);
		while (m.find()) {
			String hit = m.group().trim();
			if (!found.contains(hit)) {
				found.add(hit);
			}
		}
		return found;
	}

	public static PositionReview review(Assessment a, MaterialState m, LocalDate deadline, LocalDate today,
			Instant preApplyCheckedAt) {
		List<Todo> todos = new ArrayList<>();
		String materialBasis = materialTodos(a, m, todos);

		// 「满足其一即可」的组：已有一项满足，组内其余项不再影响结论；还有项可能满足，组内规则不符合的项不构成排除
		Map<String, Boolean> groupPassed = new HashMap<>();
		Map<String, Boolean> groupMayPass = new HashMap<>();
		for (CheckResult r : a.results()) {
			if (r.alternativeGroup() != null) {
				groupPassed.merge(r.alternativeGroup(), r.verdict() == Verdict.PASS, Boolean::logicalOr);
				groupMayPass.merge(r.alternativeGroup(), mayPass(r), Boolean::logicalOr);
			}
		}
		List<String> verified = new ArrayList<>();
		List<String> unverified = new ArrayList<>();
		boolean allConfirmedPass = true;
		for (CheckResult r : a.results()) {
			String group = r.alternativeGroup();
			if (r.verdict() == Verdict.NOT_APPLICABLE || group != null && groupPassed.get(group) && r.verdict() != Verdict.PASS) {
				continue;
			}
			if (r.verdict() == Verdict.PASS) {
				if (r.confirmed()) {
					verified.add(r.description() + (r.judged() ? "（本人判定）" : ""));
				}
				else {
					unverified.add(r.description());
					allConfirmedPass = false;
				}
				continue;
			}
			allConfirmedPass = false;
			if (r.verdict() != Verdict.UNKNOWN) {
				continue;
			}
			if (r.ruleVerdict() == Verdict.FAIL && r.pending() == PendingKind.CONFIRM_EXTRACTION) {
				if (group == null || !groupMayPass.get(group)) {
					todos.add(new Todo(TodoKind.CONFIRM_EXCLUSION, "确认后会导致不符合：" + r.description() + "（" + r.reason() + "）",
							r.requirementId()));
				}
			}
			else if (r.pending() == PendingKind.MISSING_PROFILE) {
				todos.add(new Todo(TodoKind.MISSING_PROFILE, "档案缺信息：" + r.description() + "（" + r.reason() + "）",
						r.requirementId()));
			}
			else if (r.pending() == PendingKind.NEEDS_JUDGEMENT) {
				todos.add(new Todo(TodoKind.NEEDS_JUDGEMENT,
						(r.confirmed() ? "需本人判定：" : "需本人判定（先确认抽取无误）：") + r.description() + "（" + r.reason() + "）",
						r.requirementId()));
			}
			else if (r.ruleVerdict() == Verdict.PASS) {
				unverified.add(r.description());
			}
			else if (r.ruleVerdict() == Verdict.NOT_APPLICABLE) {
				unverified.add(r.description() + "（模型标注为不适用于你）");
			}
		}

		Long daysLeft = deadline == null ? null : ChronoUnit.DAYS.between(today, deadline);
		boolean ready = todos.isEmpty() && allConfirmedPass && !a.results().isEmpty() && a.overall() != Verdict.FAIL;
		ReviewStatus status;
		String reason;
		if (a.overall() == Verdict.FAIL) {
			status = ReviewStatus.EXCLUDED;
			reason = "已确认的条件不符合";
		}
		else if (has(todos, TodoKind.CONFIRM_EXCLUSION)) {
			status = ReviewStatus.PROPOSED_EXCLUSION;
			reason = "有条件按规则会导致不符合，但抽取还没人工确认";
		}
		else if (a.results().isEmpty() || has(todos, TodoKind.MISSING_PROFILE) || has(todos, TodoKind.MISSING_MATERIAL)
				|| has(todos, TodoKind.MATERIAL_SCOPE)) {
			status = ReviewStatus.TO_CHECK;
			reason = a.results().isEmpty() ? "还没有可判断的条件" : "缺个人信息、缺招聘材料或材料范围未查清";
		}
		else if (ready && preApplyCheckedAt != null) {
			status = ReviewStatus.VERIFIED;
			reason = "所有适用条件已确认满足，已完成投递前核对";
		}
		else {
			status = ReviewStatus.PRELIM_MATCH;
			reason = !todos.isEmpty() ? "已知硬条件没有不符合，还有需要本人拍板的口径问题"
					: ready ? "条件均已确认满足，投递前请对照官网再核对一遍" : "已知条件满足，部分条件尚未人工确认";
		}
		return new PositionReview(status, reason, verified, unverified, todos, materialBasis, deadline, daysLeft, ready,
				preApplyCheckedAt);
	}

	/** 材料方面的待办；材料范围已明确时返回依据说明。 */
	private static String materialTodos(Assessment a, MaterialState m, List<Todo> todos) {
		for (String p : a.pending()) {
			todos.add(new Todo(TodoKind.MATERIAL_SCOPE, p + "：请抽取条件，或人工看完后标记已核查", null));
		}
		if (!m.hasJdText()) {
			todos.add(new Todo(TodoKind.MATERIAL_SCOPE, "还没有录入岗位原文", null));
			return null;
		}
		if (m.resolution() != null) {
			return switch (m.resolution()) {
				case PAGE_COMPLETE -> "本人确认岗位页面即完整要求";
				case REFERENCED_NO_EXTRA -> "本人已核对岗位原文引用的材料，没有额外条件";
				case REFERENCED_EXTRA_ADDED -> "本人已核对岗位原文引用的材料，额外条件已补录";
			} + "：" + m.resolutionNote();
		}
		if (!m.references().isEmpty()) {
			todos.add(new Todo(TodoKind.MISSING_MATERIAL, "岗位原文提到「" + String.join("」「", m.references())
					+ "」，相关材料还没核对：录入并核查该材料后，标记「引用材料已核对」", null));
			return null;
		}
		if (!m.checkedAnnouncements().isEmpty()) {
			return "已核查公司公告：" + String.join("、", m.checkedAnnouncements());
		}
		todos.add(new Todo(TodoKind.MATERIAL_SCOPE, "尚未确认是否还有通用招聘条件（公告、简章）：录入公告，或确认岗位页面即完整要求", null));
		return null;
	}

	/** 这一项将来可能满足：已满足、规则判满足但待确认，或规则还判断不了。 */
	private static boolean mayPass(CheckResult r) {
		return r.verdict() == Verdict.PASS || r.ruleVerdict() == Verdict.PASS
				|| r.verdict() == Verdict.UNKNOWN && r.ruleVerdict() == Verdict.UNKNOWN;
	}

	private static boolean has(List<Todo> todos, TodoKind kind) {
		return todos.stream().anyMatch(t -> t.kind() == kind);
	}
}
