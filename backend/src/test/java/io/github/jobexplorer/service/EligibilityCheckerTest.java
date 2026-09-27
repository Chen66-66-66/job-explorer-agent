package io.github.jobexplorer.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.jobexplorer.config.CandidateProfile;
import io.github.jobexplorer.config.CandidateProfile.EnglishCert;
import io.github.jobexplorer.domain.Company;
import io.github.jobexplorer.domain.Enums.RequirementOrigin;
import io.github.jobexplorer.domain.Enums.RequirementType;
import io.github.jobexplorer.domain.Enums.Verdict;
import io.github.jobexplorer.domain.Position;
import io.github.jobexplorer.domain.Requirement;

class EligibilityCheckerTest {

	private final EligibilityChecker checker = new EligibilityChecker();

	/** 1999-05-21 出生的硕士，境外学历，四级 425、PTE 59。 */
	private final CandidateProfile profile = new CandidateProfile("测试", "硕士", List.of("计算机科学与技术"),
			LocalDate.of(2027, 6, 30), LocalDate.of(1999, 5, 21), true, false,
			List.of(new EnglishCert("CET-4", 425.0), new EnglishCert("PTE", 59.0)), null);

	private final LocalDate today = LocalDate.of(2026, 9, 26);

	private final Company company = new Company("测试公司");

	/** 已确认、适用于全部岗位的人工条件：规则结论会被直接采用。 */
	private Requirement req(RequirementType type) {
		Requirement r = new Requirement(company, type, RequirementOrigin.MANUAL, "测试条件");
		r.setAppliesToAllPositions(true);
		return r;
	}

	/** 模型抽取、引用已核对、尚未人工确认。 */
	private Requirement llmReq(RequirementType type) {
		Requirement r = new Requirement(company, type, RequirementOrigin.LLM, "模型条件");
		r.setQuoteVerified(true);
		return r;
	}

	private Verdict verdict(Requirement r) {
		return checker.check(r, profile, today).verdict();
	}

	private Verdict overall(Requirement... rs) {
		List<CheckResult> results = Arrays.stream(rs).map(r -> checker.check(r, profile, today)).toList();
		return VerdictAggregator.overall(results, true);
	}

	// ---------- 英语 ----------

	@Test
	void 六级要求而只有四级_不符合() {
		Requirement r = req(RequirementType.ENGLISH);
		r.setLevel("CET-6");
		assertThat(verdict(r)).isEqualTo(Verdict.FAIL);
	}

	@Test
	void 六级要求但接受同等水平_待核实() {
		Requirement r = req(RequirementType.ENGLISH);
		r.setLevel("CET-6");
		r.setAllowEquivalent(true);
		assertThat(verdict(r)).isEqualTo(Verdict.UNKNOWN);
	}

	@Test
	void 四级425压线_符合() {
		Requirement r = req(RequirementType.ENGLISH);
		r.setLevel("大学英语四级");
		r.setMinScore(425.0);
		assertThat(verdict(r)).isEqualTo(Verdict.PASS);
	}

	@Test
	void 通过六级按425分线判断_只有成绩单不算通过() {
		CandidateProfile cet6Low = new CandidateProfile("测试", "硕士", List.of(), null, null, false, false,
				List.of(new EnglishCert("CET-6", 410.0)), null);
		Requirement r = req(RequirementType.ENGLISH);
		r.setLevel("CET-6");
		assertThat(checker.check(r, cet6Low, today).verdict()).isEqualTo(Verdict.FAIL);
		CandidateProfile cet6Pass = new CandidateProfile("测试", "硕士", List.of(), null, null, false, false,
				List.of(new EnglishCert("CET-6", 425.0)), null);
		assertThat(checker.check(r, cet6Pass, today).verdict()).isEqualTo(Verdict.PASS);
	}

	@Test
	void 要求四级而只有六级_不自行推断可替代() {
		CandidateProfile onlyCet6 = new CandidateProfile("测试", "硕士", List.of(), null, null, false, false,
				List.of(new EnglishCert("CET-6", 300.0)), null);
		Requirement r = req(RequirementType.ENGLISH);
		r.setLevel("CET-4");
		r.setMinScore(500.0);
		assertThat(checker.check(r, onlyCet6, today).verdict()).isEqualTo(Verdict.UNKNOWN);
	}

	// ---------- 分支条件 ----------

	@Test
	void 本科硕士年龄上限不同_只按本人学历判断() {
		Requirement bachelor = req(RequirementType.AGE);
		bachelor.setAppliesTo("本科生");
		bachelor.setMaxAge(25);
		bachelor.setAgeReferenceDate(LocalDate.of(2027, 7, 31));
		Requirement master = req(RequirementType.AGE);
		master.setAppliesTo("硕士研究生");
		master.setMaxAge(28);
		master.setAgeReferenceDate(LocalDate.of(2027, 7, 31));

		assertThat(verdict(bachelor)).isEqualTo(Verdict.NOT_APPLICABLE);
		assertThat(verdict(master)).isEqualTo(Verdict.PASS);
		assertThat(overall(bachelor, master)).isEqualTo(Verdict.PASS);
	}

	@Test
	void 六级或雅思满足其一_都不满足才算不符合() {
		Requirement cet6 = req(RequirementType.ENGLISH);
		cet6.setLevel("CET-6");
		cet6.setAlternativeGroup("g1");
		Requirement pte = req(RequirementType.ENGLISH);
		pte.setLevel("PTE");
		pte.setMinScore(50.0);
		pte.setAlternativeGroup("g1");
		assertThat(overall(cet6, pte)).isEqualTo(Verdict.PASS);

		pte.setMinScore(65.0);
		assertThat(overall(cet6, pte)).isEqualTo(Verdict.FAIL);
	}

	@Test
	void 模型标注的适用对象未确认_不能当作不适用而跳过() {
		// 模型可能把针对硕士的条件误标成「仅限本科生」，确认前不能让它悄悄帮助整体通过
		Requirement r = llmReq(RequirementType.ENGLISH);
		r.setLevel("CET-6");
		r.setAppliesTo("本科生");
		assertThat(verdict(r)).isEqualTo(Verdict.UNKNOWN);
		r.setConfirmed(true);
		assertThat(verdict(r)).isEqualTo(Verdict.NOT_APPLICABLE);
	}

	@Test
	void 必须满足的条件适用性未知_阻止整体通过() {
		Requirement degree = req(RequirementType.DEGREE);
		degree.setLevel("本科");
		Requirement unsure = llmReq(RequirementType.ENGLISH);
		unsure.setLevel("CET-6");
		unsure.setAppliesTo("本科生");
		assertThat(overall(degree, unsure)).isEqualTo(Verdict.UNKNOWN);
	}

	@Test
	void 满足其一时_已有确认适用且满足的路径_组可以通过() {
		Requirement ielts = req(RequirementType.ENGLISH);
		ielts.setLevel("PTE");
		ielts.setMinScore(50.0);
		ielts.setAlternativeGroup("g1");
		Requirement unsure = llmReq(RequirementType.ENGLISH);
		unsure.setLevel("CET-6");
		unsure.setAppliesTo("本科生");
		unsure.setAlternativeGroup("g1");
		assertThat(overall(ielts, unsure)).isEqualTo(Verdict.PASS);
	}

	@Test
	void 已确认的必需条件不满足_可直接判不符合_不必等其他条件() {
		Requirement cet6 = req(RequirementType.ENGLISH);
		cet6.setLevel("CET-6");
		Requirement unsure = llmReq(RequirementType.AGE);
		unsure.setMaxAge(30);
		unsure.setAppliesTo("本科生");
		assertThat(overall(cet6, unsure)).isEqualTo(Verdict.FAIL);
	}

	@Test
	void 适用对象无法识别时_不据此排除() {
		Requirement r = req(RequirementType.ENGLISH);
		r.setLevel("CET-6");
		r.setAppliesTo("研发类岗位");
		assertThat(verdict(r)).isEqualTo(Verdict.UNKNOWN);
	}

	@Test
	void 境内外毕业生窗口不同_只看适用于本人的() {
		Requirement domestic = req(RequirementType.GRADUATION_WINDOW);
		domestic.setAppliesTo("境内高校毕业生");
		domestic.setMaxDate(LocalDate.of(2027, 6, 1));
		Requirement overseas = req(RequirementType.GRADUATION_WINDOW);
		overseas.setAppliesTo("境外院校毕业生");
		overseas.setMaxDate(LocalDate.of(2027, 8, 31));
		assertThat(verdict(domestic)).isEqualTo(Verdict.NOT_APPLICABLE);
		assertThat(verdict(overseas)).isEqualTo(Verdict.PASS);
	}

	// ---------- 年龄 ----------

	@Test
	void 年龄按参考日计算_不超过28周岁压线() {
		Requirement r = req(RequirementType.AGE);
		r.setMaxAge(28);
		r.setAgeReferenceDate(LocalDate.of(2027, 7, 31));
		assertThat(verdict(r)).isEqualTo(Verdict.PASS);
		r.setMaxAge(27);
		assertThat(verdict(r)).isEqualTo(Verdict.FAIL);
	}

	@Test
	void 未满28周岁比不超过28周岁更严() {
		Requirement r = req(RequirementType.AGE);
		r.setMaxAge(28);
		r.setAgeStrict(true);
		r.setAgeReferenceDate(LocalDate.of(2027, 7, 31));
		assertThat(verdict(r)).isEqualTo(Verdict.FAIL);
	}

	@Test
	void 公告未写年龄计算日_待核实() {
		Requirement r = req(RequirementType.AGE);
		r.setMaxAge(30);
		assertThat(verdict(r)).isEqualTo(Verdict.UNKNOWN);
	}

	// ---------- 其他规则 ----------

	@Test
	void 毕业时间窗口() {
		Requirement r = req(RequirementType.GRADUATION_WINDOW);
		r.setMinDate(LocalDate.of(2026, 7, 1));
		r.setMaxDate(LocalDate.of(2027, 6, 30));
		assertThat(verdict(r)).isEqualTo(Verdict.PASS);
		r.setMaxDate(LocalDate.of(2027, 6, 29));
		assertThat(verdict(r)).isEqualTo(Verdict.FAIL);
	}

	@Test
	void 届别_未申报时同年毕业也只判待核实() {
		// 档案 2027-06-30 毕业，但未申报届别：届别由招聘单位认定，年份相同不能证明属于该届
		Requirement r = req(RequirementType.GRADUATION_COHORT);
		r.setListValues("2027届");
		CheckResult result = checker.check(r, profile, today);
		assertThat(result.verdict()).isEqualTo(Verdict.UNKNOWN);
		assertThat(result.reason()).contains("届别资格待核实");
		r.setListValues("2026");
		assertThat(verdict(r)).isEqualTo(Verdict.UNKNOWN);
		r.setListValues(null);
		assertThat(verdict(r)).isEqualTo(Verdict.UNKNOWN);
	}

	@Test
	void 届别_本人申报一致才判符合_并注明依据() {
		CandidateProfile declared2027 = new CandidateProfile("测试", "硕士", List.of(), LocalDate.of(2027, 6, 30), null,
				false, false, List.of(), 2027);
		Requirement r = req(RequirementType.GRADUATION_COHORT);
		r.setListValues("2026、2027");
		CheckResult result = checker.check(r, declared2027, today);
		assertThat(result.verdict()).isEqualTo(Verdict.PASS);
		assertThat(result.reason()).contains("本人").contains("申报");
		r.setListValues("2028");
		assertThat(checker.check(r, declared2027, today).verdict()).as("不一致也不直接排除").isEqualTo(Verdict.UNKNOWN);
	}

	@Test
	void 届别_人工确认抽取无误不等于确认本人属于该届() {
		Requirement r = llmReq(RequirementType.GRADUATION_COHORT);
		r.setListValues("2027");
		r.setConfirmed(true);
		r.setAppliesToAllPositions(true);
		assertThat(verdict(r)).isEqualTo(Verdict.UNKNOWN);
	}

	@Test
	void 届别_同年毕业但另有毕业窗口时由窗口决定() {
		// 要求「2027届」，另有截至 2027-08-31 的毕业窗口；候选人 2027-12 毕业
		CandidateProfile lateGrad = new CandidateProfile("测试", "硕士", List.of(), LocalDate.of(2027, 12, 15), null,
				true, true, List.of(), null);
		Requirement cohort = req(RequirementType.GRADUATION_COHORT);
		cohort.setListValues("2027");
		Requirement window = req(RequirementType.GRADUATION_WINDOW);
		window.setMaxDate(LocalDate.of(2027, 8, 31));
		assertThat(checker.check(cohort, lateGrad, today).verdict()).isEqualTo(Verdict.UNKNOWN);
		List<CheckResult> results = List.of(checker.check(cohort, lateGrad, today), checker.check(window, lateGrad, today));
		assertThat(VerdictAggregator.overall(results, true)).isEqualTo(Verdict.FAIL);
	}

	@Test
	void 专业精确匹配才通过_相近只算待核实() {
		Requirement r = req(RequirementType.MAJOR);
		r.setListValues("软件工程、计算机科学与技术");
		assertThat(verdict(r)).isEqualTo(Verdict.PASS);
		r.setListValues("计算机科学");
		assertThat(verdict(r)).isEqualTo(Verdict.UNKNOWN);
		r.setListValues("计算机类、电子信息类");
		assertThat(verdict(r)).isEqualTo(Verdict.UNKNOWN);
	}

	@Test
	void 已过截止日_不符合() {
		Requirement r = req(RequirementType.DEADLINE);
		r.setMaxDate(LocalDate.of(2026, 9, 25));
		assertThat(verdict(r)).isEqualTo(Verdict.FAIL);
	}

	// ---------- 把关：不确定就不下结论 ----------

	@Test
	void 模型抽取且引用未核实_不给确定结论() {
		Requirement r = llmReq(RequirementType.ENGLISH);
		r.setLevel("CET-6");
		r.setQuoteVerified(false);
		r.setConfirmed(true);
		r.setAppliesToAllPositions(true);
		assertThat(verdict(r)).isEqualTo(Verdict.UNKNOWN);
		r.setQuoteVerified(true);
		assertThat(verdict(r)).isEqualTo(Verdict.FAIL);
	}

	@Test
	void 规则本身判待核实时_也要说明引用找不到() {
		Requirement r = llmReq(RequirementType.AGE);
		r.setMaxAge(24);
		r.setQuoteVerified(false);
		CheckResult result = checker.check(r, profile, today);
		assertThat(result.verdict()).isEqualTo(Verdict.UNKNOWN);
		assertThat(result.reason()).startsWith("原文出处未能核对");
	}

	@Test
	void 模型抽取未经人工确认_通过和不通过都只算待核实() {
		Requirement fail = llmReq(RequirementType.ENGLISH);
		fail.setLevel("CET-6");
		assertThat(verdict(fail)).isEqualTo(Verdict.UNKNOWN);
		Requirement pass = llmReq(RequirementType.DEGREE);
		pass.setLevel("本科");
		assertThat(verdict(pass)).isEqualTo(Verdict.UNKNOWN);
	}

	@Test
	void 有解析问题且未确认_不给确定结论_人工确认后以人工为准() {
		Requirement r = llmReq(RequirementType.GRADUATION_WINDOW);
		r.setMaxDate(LocalDate.of(2027, 8, 31));
		r.setParseIssue("起始日期「2026年1月」无法解析");
		r.setAppliesToAllPositions(true);
		assertThat(verdict(r)).isEqualTo(Verdict.UNKNOWN);
		r.setConfirmed(true);
		assertThat(verdict(r)).isEqualTo(Verdict.PASS);
	}

	@Test
	void 留服认证_档案注明计划办理才算符合() {
		Requirement r = req(RequirementType.OVERSEAS_CERT);
		assertThat(verdict(r)).isEqualTo(Verdict.UNKNOWN);
		CandidateProfile planned = new CandidateProfile("测试", "硕士", List.of(), null, null, true, true, List.of(), null);
		assertThat(checker.check(r, planned, today).verdict()).isEqualTo(Verdict.PASS);
	}

	@Test
	void 公司级条件未确认适用全部岗位_不据此排除() {
		Requirement r = req(RequirementType.ENGLISH);
		r.setLevel("CET-6");
		r.setAppliesToAllPositions(false);
		assertThat(verdict(r)).isEqualTo(Verdict.UNKNOWN);
	}

	@Test
	void 岗位级条件不需要适用全部岗位的确认() {
		Requirement r = new Requirement(company, RequirementType.ENGLISH, RequirementOrigin.MANUAL, "岗位条件");
		r.setPosition(new Position(company, "测试岗位"));
		r.setLevel("CET-6");
		assertThat(verdict(r)).isEqualTo(Verdict.FAIL);
	}

	// ---------- 整体汇总 ----------

	@Test
	void 整体结论_任一不符合即不符合_无条件为待核实() {
		Requirement ok = req(RequirementType.DEGREE);
		ok.setLevel("本科");
		Requirement bad = req(RequirementType.ENGLISH);
		bad.setLevel("CET-6");
		assertThat(overall(ok, bad)).isEqualTo(Verdict.FAIL);
		assertThat(VerdictAggregator.overall(List.of(), true)).isEqualTo(Verdict.UNKNOWN);
	}

	@Test
	void 还有来源没核查_最多算部分通过() {
		Requirement ok = req(RequirementType.DEGREE);
		ok.setLevel("本科");
		List<CheckResult> results = List.of(checker.check(ok, profile, today));
		assertThat(VerdictAggregator.overall(results, true)).isEqualTo(Verdict.PASS);
		assertThat(VerdictAggregator.overall(results, false)).isEqualTo(Verdict.PARTIAL);
	}
}
