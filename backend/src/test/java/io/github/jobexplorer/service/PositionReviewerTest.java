package io.github.jobexplorer.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.jobexplorer.domain.Enums.MaterialResolution;
import io.github.jobexplorer.domain.Enums.PendingKind;
import io.github.jobexplorer.domain.Enums.RequirementOrigin;
import io.github.jobexplorer.domain.Enums.RequirementType;
import io.github.jobexplorer.domain.Enums.ReviewStatus;
import io.github.jobexplorer.domain.Enums.TodoKind;
import io.github.jobexplorer.domain.Enums.Verdict;
import io.github.jobexplorer.service.AssessmentService.Assessment;
import io.github.jobexplorer.service.PositionReviewer.MaterialState;
import io.github.jobexplorer.service.PositionReviewer.PositionReview;

class PositionReviewerTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);

	/** 岗位页面即完整要求，已由本人确认。 */
	private static final MaterialState PAGE_COMPLETE = new MaterialState(true, List.of(),
			MaterialResolution.PAGE_COMPLETE, "官网详情页", List.of());

	private static long ids = 1;

	private static CheckResult result(String desc, Verdict verdict, Verdict ruleVerdict, PendingKind pending,
			boolean confirmed, String group) {
		return new CheckResult(ids++, RequirementType.OTHER, RequirementOrigin.LLM, false, desc, verdict, "理由", null, true,
				confirmed, false, null, group, null, ruleVerdict, pending, false);
	}

	private static CheckResult confirmedPass(String desc) {
		return result(desc, Verdict.PASS, Verdict.PASS, null, true, null);
	}

	/** 规则算出的结论，因抽取未确认被降为待核实。 */
	private static CheckResult unconfirmed(String desc, Verdict ruleVerdict, String group) {
		return result(desc, Verdict.UNKNOWN, ruleVerdict, PendingKind.CONFIRM_EXTRACTION, false, group);
	}

	private static Assessment assessment(List<String> pending, CheckResult... results) {
		List<CheckResult> rs = List.of(results);
		return new Assessment(VerdictAggregator.overall(rs, pending.isEmpty()), rs, pending);
	}

	private static PositionReview review(MaterialState m, Instant preApply, CheckResult... results) {
		return PositionReviewer.review(assessment(List.of(), results), m, null, TODAY, preApply);
	}

	private static List<TodoKind> kinds(PositionReview r) {
		return r.todos().stream().map(PositionReviewer.Todo::kind).toList();
	}

	@Test
	void 识别岗位原文中引用的其他材料() {
		assertThat(PositionReviewer.findReferences("其他要求详见招聘公告。专业要求见附件岗位表"))
			.containsExactly("详见招聘公告", "见附件岗位表");
		assertThat(PositionReviewer.findReferences("任职要求：硕士研究生及以上学历。")).isEmpty();
		assertThat(PositionReviewer.findReferences(null)).isEmpty();
	}

	@Test
	void 没有条件时是待核查_不是初筛匹配() {
		PositionReview r = review(PAGE_COMPLETE, null);
		assertThat(r.status()).isEqualTo(ReviewStatus.TO_CHECK);
		assertThat(r.readyForPreApplyCheck()).isFalse();
	}

	@Test
	void 条件均已确认满足_未做投递前核对时是初筛匹配() {
		PositionReview r = review(PAGE_COMPLETE, null, confirmedPass("硕士及以上"));
		assertThat(r.status()).isEqualTo(ReviewStatus.PRELIM_MATCH);
		assertThat(r.readyForPreApplyCheck()).isTrue();
		assertThat(r.verifiedConditions()).containsExactly("硕士及以上");
		assertThat(r.materialBasis()).contains("岗位页面即完整要求");
	}

	@Test
	void 完成投递前核对后才是已核实可报() {
		PositionReview r = review(PAGE_COMPLETE, Instant.now(), confirmedPass("硕士及以上"));
		assertThat(r.status()).isEqualTo(ReviewStatus.VERIFIED);
	}

	@Test
	void 有条件未确认时_即使做过投递前核对也不是已核实可报() {
		PositionReview r = review(PAGE_COMPLETE, Instant.now(), confirmedPass("硕士及以上"),
				unconfirmed("六级 425", Verdict.PASS, null));
		assertThat(r.status()).isEqualTo(ReviewStatus.PRELIM_MATCH);
		assertThat(r.readyForPreApplyCheck()).isFalse();
		assertThat(r.unverifiedPositives()).containsExactly("六级 425");
	}

	@Test
	void 确认后会导致不符合的条件_归为拟排除待确认() {
		PositionReview r = review(PAGE_COMPLETE, null, confirmedPass("硕士及以上"),
				unconfirmed("年龄不超过 26 周岁", Verdict.FAIL, null));
		assertThat(r.status()).isEqualTo(ReviewStatus.PROPOSED_EXCLUSION);
		assertThat(kinds(r)).containsExactly(TodoKind.CONFIRM_EXCLUSION);
	}

	@Test
	void 已确认的条件不符合_是已排除() {
		PositionReview r = review(PAGE_COMPLETE, null,
				result("博士", Verdict.FAIL, Verdict.FAIL, null, true, null));
		assertThat(r.status()).isEqualTo(ReviewStatus.EXCLUDED);
	}

	@Test
	void 档案缺信息时是待核查() {
		PositionReview r = review(PAGE_COMPLETE, null, confirmedPass("硕士及以上"),
				result("2027 届", Verdict.UNKNOWN, Verdict.UNKNOWN, PendingKind.MISSING_PROFILE, true, null));
		assertThat(r.status()).isEqualTo(ReviewStatus.TO_CHECK);
		assertThat(kinds(r)).containsExactly(TodoKind.MISSING_PROFILE);
	}

	@Test
	void 需要本人拍板的口径问题_不影响初筛匹配但不能做投递前核对() {
		PositionReview r = review(PAGE_COMPLETE, null, confirmedPass("硕士及以上"),
				result("电子信息类专业", Verdict.UNKNOWN, Verdict.UNKNOWN, PendingKind.NEEDS_JUDGEMENT, true, null));
		assertThat(r.status()).isEqualTo(ReviewStatus.PRELIM_MATCH);
		assertThat(kinds(r)).containsExactly(TodoKind.NEEDS_JUDGEMENT);
		assertThat(r.readyForPreApplyCheck()).isFalse();
	}

	@Test
	void 岗位原文引用了其他材料_未核对前是待核查() {
		MaterialState m = new MaterialState(true, List.of("详见招聘公告"), null, null, List.of("「2027 校招公告」"));
		PositionReview r = review(m, null, confirmedPass("硕士及以上"));
		assertThat(r.status()).isEqualTo(ReviewStatus.TO_CHECK);
		assertThat(kinds(r)).containsExactly(TodoKind.MISSING_MATERIAL);

		MaterialState done = new MaterialState(true, List.of("详见招聘公告"), MaterialResolution.REFERENCED_NO_EXTRA,
				"已核对 2027 校招公告", List.of("「2027 校招公告」"));
		assertThat(review(done, null, confirmedPass("硕士及以上")).status()).isEqualTo(ReviewStatus.PRELIM_MATCH);
	}

	@Test
	void 材料范围未确认时是待核查_公司公告已核查则视为已明确() {
		MaterialState unknown = new MaterialState(true, List.of(), null, null, List.of());
		PositionReview r = review(unknown, null, confirmedPass("硕士及以上"));
		assertThat(r.status()).isEqualTo(ReviewStatus.TO_CHECK);
		assertThat(kinds(r)).containsExactly(TodoKind.MATERIAL_SCOPE);

		MaterialState withAnnouncement = new MaterialState(true, List.of(), null, null, List.of("「2027 校招公告」"));
		PositionReview ok = review(withAnnouncement, null, confirmedPass("硕士及以上"));
		assertThat(ok.status()).isEqualTo(ReviewStatus.PRELIM_MATCH);
		assertThat(ok.materialBasis()).contains("2027 校招公告");
	}

	@Test
	void 公告或JD未核查时是待核查() {
		PositionReview r = PositionReviewer.review(assessment(List.of("岗位 JD 尚未核查"), confirmedPass("硕士及以上")),
				PAGE_COMPLETE, null, TODAY, null);
		assertThat(r.status()).isEqualTo(ReviewStatus.TO_CHECK);
		assertThat(r.todos()).anyMatch(t -> t.text().contains("岗位 JD 尚未核查"));
	}

	@Test
	void 满足其一即可_另一项还可能满足时不拟排除() {
		PositionReview r = review(PAGE_COMPLETE, null, unconfirmed("六级 425", Verdict.PASS, "英语"),
				unconfirmed("雅思 6.5", Verdict.FAIL, "英语"));
		assertThat(r.status()).isEqualTo(ReviewStatus.PRELIM_MATCH);
		assertThat(r.todos()).isEmpty();
		assertThat(r.unverifiedPositives()).containsExactly("六级 425");
	}

	@Test
	void 满足其一即可_已有一项确认满足时其余项不影响() {
		PositionReview r = review(PAGE_COMPLETE, null, confirmedPass("六级 425"),
				result("雅思 6.5", Verdict.FAIL, Verdict.FAIL, null, true, null) /* 不同组：单独必须满足 */);
		assertThat(r.status()).isEqualTo(ReviewStatus.EXCLUDED);

		PositionReview grouped = review(PAGE_COMPLETE, null, result("六级 425", Verdict.PASS, Verdict.PASS, null, true, "英语"),
				result("雅思 6.5", Verdict.FAIL, Verdict.FAIL, null, true, "英语"));
		assertThat(grouped.readyForPreApplyCheck()).isTrue();
	}

	@Test
	void 计算距截止天数() {
		PositionReview r = PositionReviewer.review(assessment(List.of(), confirmedPass("硕士及以上")), PAGE_COMPLETE,
				LocalDate.of(2026, 10, 12), TODAY, null);
		assertThat(r.daysLeft()).isEqualTo(3);
	}
}
