package io.github.jobexplorer.service;

import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import io.github.jobexplorer.config.CandidateProfile;
import io.github.jobexplorer.config.CandidateProfile.EnglishCert;
import io.github.jobexplorer.domain.Enums.RequirementOrigin;
import io.github.jobexplorer.domain.Enums.Verdict;
import io.github.jobexplorer.domain.Requirement;

/**
 * 用确定性规则把一条条件和求职者档案做比对。这里不调用大模型。
 *
 * 判断分三步：
 * 1. 适用性：条件只针对某类人（如「本科生年龄不超过 25 周岁」）时，先看是否适用于本人；
 * 2. 按类型计算：学历、英语、年龄等各有规则；
 * 3. 把关：解析有问题、引用核对失败、尚未人工确认、未确认适用于全部岗位的条件，
 *    不允许给出确定结论，降为「待核实」。宁可待核实，不错误排除。
 */
@Component
public class EligibilityChecker {

	private static final Map<String, Integer> DEGREE_RANK = Map.of("大专", 1, "本科", 2, "硕士", 3, "博士", 4);

	/** 大学英语四、六级通常以 425 分作为「通过」的分数线。 */
	private static final double CET_PASS_LINE = 425;

	public CheckResult check(Requirement r, CandidateProfile p, LocalDate today) {
		Applicability applicability = applicability(r.getAppliesTo(), p);
		if (applicability == Applicability.NO) {
			return CheckResult.of(r, Verdict.NOT_APPLICABLE, "适用对象为「" + r.getAppliesTo() + "」，与档案不符，不适用");
		}
		CheckResult raw = checkByType(r, p, today);
		if (raw.verdict() == Verdict.UNKNOWN) {
			// 规则本身已判待核实，也要把出处问题说出来，避免把找不到的引用当成原文
			return r.getOrigin() == RequirementOrigin.LLM && !r.isQuoteVerified()
					? raw.withVerdict(Verdict.UNKNOWN, "原文出处未能核对；" + raw.reason())
					: raw;
		}
		String computed = "规则计算为「" + label(raw.verdict()) + "」（" + raw.reason() + "）";
		if (r.getOrigin() == RequirementOrigin.LLM && !r.isQuoteVerified()) {
			return raw.withVerdict(Verdict.UNKNOWN, "原文出处未能核对，暂不采信；" + computed);
		}
		// 解析问题需要人看过：人工确认后以人工为准，问题提示仍会显示
		if (r.getParseIssue() != null && !r.isConfirmed()) {
			return raw.withVerdict(Verdict.UNKNOWN, "解析有问题：" + r.getParseIssue() + "；" + computed);
		}
		if (!r.isConfirmed()) {
			return raw.withVerdict(Verdict.UNKNOWN, "解析结果待人工确认；" + computed);
		}
		if (applicability == Applicability.UNSURE) {
			return raw.withVerdict(Verdict.UNKNOWN, "适用对象「" + r.getAppliesTo() + "」无法自动判断是否包括本人；" + computed);
		}
		if (raw.verdict() == Verdict.FAIL && r.isCompanyLevel() && !r.isAppliesToAllPositions()) {
			return raw.withVerdict(Verdict.UNKNOWN, "尚未确认该条件适用于本批次全部岗位，不据此排除；" + computed);
		}
		return raw;
	}

	private enum Applicability {
		YES, NO, UNSURE
	}

	/** 只识别两类可靠的适用对象：学历层次、境内 / 境外院校。其他一律视为无法判断。 */
	private static Applicability applicability(String appliesTo, CandidateProfile p) {
		if (appliesTo == null || appliesTo.isBlank()) {
			return Applicability.YES;
		}
		String a = appliesTo.replace("研究生", "");
		boolean mentionsDegree = false;
		for (String degree : DEGREE_RANK.keySet()) {
			if (a.contains(degree)) {
				mentionsDegree = true;
				if (degree.equals(p.degree())) {
					return Applicability.YES;
				}
			}
		}
		if (mentionsDegree) {
			return Applicability.NO;
		}
		boolean overseas = a.contains("境外") || a.contains("国外") || a.contains("海外") || a.contains("留学");
		boolean domestic = a.contains("境内") || a.contains("国内");
		if (overseas != domestic) {
			return overseas == p.overseasDegree() ? Applicability.YES : Applicability.NO;
		}
		return Applicability.UNSURE;
	}

	private CheckResult checkByType(Requirement r, CandidateProfile p, LocalDate today) {
		return switch (r.getType()) {
			case DEGREE -> checkDegree(r, p);
			case MAJOR -> checkMajor(r, p);
			case ENGLISH -> checkEnglish(r, p);
			case GRADUATION_WINDOW -> checkGraduationWindow(r, p);
			case AGE -> checkAge(r, p, today);
			case OVERSEAS_CERT -> checkOverseasCert(r, p);
			case DEADLINE -> checkDeadline(r, today);
			case OTHER -> CheckResult.of(r, Verdict.UNKNOWN, "非结构化条件，需人工判断");
		};
	}

	private CheckResult checkDegree(Requirement r, CandidateProfile p) {
		Integer required = DEGREE_RANK.get(r.getLevel());
		Integer actual = DEGREE_RANK.get(p.degree());
		if (required == null || actual == null) {
			return CheckResult.of(r, Verdict.UNKNOWN, "学历要求或档案学历无法识别");
		}
		return actual >= required
				? CheckResult.of(r, Verdict.PASS, "档案学历为" + p.degree() + "，满足" + r.getLevel() + "及以上")
				: CheckResult.of(r, Verdict.FAIL, "档案学历为" + p.degree() + "，低于要求的" + r.getLevel());
	}

	private CheckResult checkMajor(Requirement r, CandidateProfile p) {
		List<String> required = splitList(r.getListValues());
		if (required.isEmpty()) {
			return CheckResult.of(r, Verdict.UNKNOWN, "专业名单为空");
		}
		for (String mine : p.majors()) {
			if (required.contains(mine.trim())) {
				return CheckResult.of(r, Verdict.PASS, "档案专业「" + mine + "」在名单中");
			}
		}
		for (String mine : p.majors()) {
			for (String want : required) {
				if (mine.contains(want) || want.contains(mine)) {
					return CheckResult.of(r, Verdict.UNKNOWN, "档案专业「" + mine + "」与名单中的「" + want + "」相近但不完全一致，需人工确认");
				}
			}
		}
		// 名单常写「计算机类」「等相关专业」，不直接判不符合
		return CheckResult.of(r, Verdict.UNKNOWN, "档案专业未出现在名单中，名单可能有类别或近似表述，需人工确认");
	}

	private CheckResult checkEnglish(Requirement r, CandidateProfile p) {
		if (r.getLevel() == null) {
			return CheckResult.of(r, Verdict.UNKNOWN, "未识别出具体英语证书要求");
		}
		String want = normalizeCert(r.getLevel());
		Optional<EnglishCert> same = p.english().stream().filter(c -> normalizeCert(c.type()).equals(want)).findFirst();
		if (same.isPresent()) {
			return scoreVerdict(r, same.get());
		}
		// 不自行推断「六级可以替代四级」等换算，交给人按公告确认
		if (!p.english().isEmpty() && (r.isAllowEquivalent() || want.equals("CET-4"))) {
			return CheckResult.of(r, Verdict.UNKNOWN, "档案中没有 " + r.getLevel() + "，但有其他英语证书，是否认可需按公告确认");
		}
		return CheckResult.of(r, Verdict.FAIL, "档案中没有 " + r.getLevel() + " 证书");
	}

	private CheckResult scoreVerdict(Requirement r, EnglishCert cert) {
		String type = normalizeCert(cert.type());
		boolean cet = type.equals("CET-4") || type.equals("CET-6");
		if (r.getMinScore() == null && cet) {
			// 「通过四 / 六级」按全国大学英语考试 425 分通过线理解；只有成绩单、没过线不算通过
			if (cert.score() == null) {
				return CheckResult.of(r, Verdict.UNKNOWN, "要求通过 " + type + "，档案未记录分数，无法判断是否过 425 分线");
			}
			return cert.score() >= CET_PASS_LINE
					? CheckResult.of(r, Verdict.PASS, type + " " + fmt(cert.score()) + " 分，达到 425 分通过线")
					: CheckResult.of(r, Verdict.FAIL, type + " " + fmt(cert.score()) + " 分，未达到 425 分通过线");
		}
		if (r.getMinScore() == null) {
			return CheckResult.of(r, Verdict.PASS, "持有 " + cert.type());
		}
		if (cert.score() == null) {
			return CheckResult.of(r, Verdict.UNKNOWN, "要求 " + fmt(r.getMinScore()) + " 分，档案未记录分数");
		}
		return cert.score() >= r.getMinScore()
				? CheckResult.of(r, Verdict.PASS, cert.type() + " " + fmt(cert.score()) + " 分，达到 " + fmt(r.getMinScore()))
				: CheckResult.of(r, Verdict.FAIL, cert.type() + " " + fmt(cert.score()) + " 分，低于 " + fmt(r.getMinScore()));
	}

	private CheckResult checkGraduationWindow(Requirement r, CandidateProfile p) {
		if (p.graduationDate() == null || (r.getMinDate() == null && r.getMaxDate() == null)) {
			return CheckResult.of(r, Verdict.UNKNOWN, "缺少毕业时间或窗口日期");
		}
		LocalDate g = p.graduationDate();
		if (r.getMinDate() != null && g.isBefore(r.getMinDate())) {
			return CheckResult.of(r, Verdict.FAIL, "毕业时间 " + g + " 早于窗口起点 " + r.getMinDate());
		}
		if (r.getMaxDate() != null && g.isAfter(r.getMaxDate())) {
			return CheckResult.of(r, Verdict.FAIL, "毕业时间 " + g + " 晚于窗口终点 " + r.getMaxDate());
		}
		return CheckResult.of(r, Verdict.PASS, "毕业时间 " + g + " 在窗口内");
	}

	private CheckResult checkAge(Requirement r, CandidateProfile p, LocalDate today) {
		if (r.getMaxAge() == null || p.birthDate() == null) {
			return CheckResult.of(r, Verdict.UNKNOWN, "缺少年龄上限或出生日期");
		}
		// 「不超过 N 周岁」到 N+1 岁生日前都符合；「未满 N 周岁」则必须在 N 岁生日前
		int limit = r.isAgeStrict() ? r.getMaxAge() - 1 : r.getMaxAge();
		String rule = (r.isAgeStrict() ? "未满 " : "不超过 ") + r.getMaxAge() + " 周岁";
		if (r.getAgeReferenceDate() == null) {
			int now = Period.between(p.birthDate(), today).getYears();
			return CheckResult.of(r, Verdict.UNKNOWN, "要求" + rule + "，但公告未写年龄计算截止日（目前 " + now + " 周岁），需确认");
		}
		int age = Period.between(p.birthDate(), r.getAgeReferenceDate()).getYears();
		String at = "截至 " + r.getAgeReferenceDate() + " 为 " + age + " 周岁";
		return age <= limit
				? CheckResult.of(r, Verdict.PASS, at + "，符合" + rule)
				: CheckResult.of(r, Verdict.FAIL, at + "，不符合" + rule);
	}

	private CheckResult checkOverseasCert(Requirement r, CandidateProfile p) {
		if (!p.overseasDegree()) {
			return CheckResult.of(r, Verdict.PASS, "非境外学历，不涉及留服认证");
		}
		if (p.overseasCertPlanned()) {
			return CheckResult.of(r, Verdict.PASS, "档案注明将按要求办理留服认证；请留意公告要求的办理时限");
		}
		return CheckResult.of(r, Verdict.UNKNOWN, "境外学历需按要求取得留服认证，请确认能否在规定时间前办好");
	}

	private CheckResult checkDeadline(Requirement r, LocalDate today) {
		if (r.getMaxDate() == null) {
			return CheckResult.of(r, Verdict.UNKNOWN, "未识别出截止日期");
		}
		long days = ChronoUnit.DAYS.between(today, r.getMaxDate());
		return days >= 0
				? CheckResult.of(r, Verdict.PASS, "截止 " + r.getMaxDate() + "，还剩 " + days + " 天")
				: CheckResult.of(r, Verdict.FAIL, "已于 " + r.getMaxDate() + " 截止");
	}

	/** 统一英语证书名称，如「六级」「CET6」都认作 CET-6。测评程序也用它。 */
	public static String normalizeCert(String type) {
		if (type == null) {
			return "";
		}
		String t = type.toUpperCase(Locale.ROOT).replace(" ", "").replace("_", "-");
		if (t.contains("六级") || t.equals("CET6") || t.equals("CET-6")) {
			return "CET-6";
		}
		if (t.contains("四级") || t.equals("CET4") || t.equals("CET-4")) {
			return "CET-4";
		}
		if (t.contains("托业") || t.contains("TOEIC")) {
			return "TOEIC";
		}
		if (t.contains("托福") || t.contains("TOEFL")) {
			return "TOEFL";
		}
		if (t.contains("雅思") || t.contains("IELTS")) {
			return "IELTS";
		}
		return t;
	}

	private static List<String> splitList(String values) {
		if (values == null || values.isBlank()) {
			return List.of();
		}
		return Arrays.stream(values.split("[,，、;；/]")).map(String::trim).filter(s -> !s.isEmpty()).toList();
	}

	static String label(Verdict v) {
		return switch (v) {
			case PASS -> "符合";
			case FAIL -> "不符合";
			case UNKNOWN -> "待核实";
			case PARTIAL -> "已核查部分通过";
			case NOT_APPLICABLE -> "不适用";
		};
	}

	private static String fmt(double d) {
		return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
	}
}
