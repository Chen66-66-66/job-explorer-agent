package io.github.jobexplorer.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.jobexplorer.domain.Announcement;
import io.github.jobexplorer.domain.Company;
import io.github.jobexplorer.domain.Enums.RequirementOrigin;
import io.github.jobexplorer.domain.Enums.RequirementType;
import io.github.jobexplorer.domain.Position;
import io.github.jobexplorer.domain.Requirement;
import io.github.jobexplorer.repository.AnnouncementRepository;
import io.github.jobexplorer.repository.PositionRepository;
import io.github.jobexplorer.repository.RequirementRepository;

/** 条件的抽取、录入、核对与确认。 */
@Service
@Transactional
public class RequirementService {

	private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

	private final LlmRequirementExtractor extractor;

	private final QuoteVerifier quoteVerifier;

	private final RequirementRepository requirements;

	private final AnnouncementRepository announcements;

	private final PositionRepository positions;

	public RequirementService(LlmRequirementExtractor extractor, QuoteVerifier quoteVerifier,
			RequirementRepository requirements, AnnouncementRepository announcements, PositionRepository positions) {
		this.extractor = extractor;
		this.quoteVerifier = quoteVerifier;
		this.requirements = requirements;
		this.announcements = announcements;
		this.positions = positions;
	}

	/**
	 * 从公告抽取公司级条件。模型调用成功后才替换之前由模型抽取的条件；人工录入的保留。
	 * 抽取出的条件都处于「待确认」状态。
	 */
	public List<Requirement> extractFromAnnouncement(Long announcementId) {
		Announcement ann = announcements.findById(announcementId)
			.orElseThrow(() -> new NotFoundException("公告", announcementId));
		List<ExtractedRequirement> extracted = extractor.extract(ann.getContent());
		requirements.deleteByAnnouncementIdAndPositionIsNullAndOrigin(announcementId, RequirementOrigin.LLM);
		List<Requirement> saved = new ArrayList<>();
		for (ExtractedRequirement e : extracted) {
			Requirement r = fromExtracted(ann.getCompany(), e);
			r.setAnnouncement(ann);
			saved.add(verifyAndSave(r));
		}
		ann.setCheckedAt(Instant.now());
		return saved;
	}

	/** 从岗位 JD 抽取岗位级条件。 */
	public List<Requirement> extractFromPosition(Long positionId) {
		Position pos = positions.findById(positionId).orElseThrow(() -> new NotFoundException("岗位", positionId));
		if (pos.getJdText() == null || pos.getJdText().isBlank()) {
			throw new IllegalArgumentException("岗位还没有录入 JD 原文");
		}
		List<ExtractedRequirement> extracted = extractor.extract(pos.getJdText());
		requirements.deleteByPositionIdAndOrigin(positionId, RequirementOrigin.LLM);
		List<Requirement> saved = new ArrayList<>();
		for (ExtractedRequirement e : extracted) {
			Requirement r = fromExtracted(pos.getCompany(), e);
			r.setPosition(pos);
			saved.add(verifyAndSave(r));
		}
		pos.setCheckedAt(Instant.now());
		return saved;
	}

	/** 保存一条条件：核对原文引用，并检查解析出的数值是否出现在引用里。人工录入也不例外。 */
	public Requirement verifyAndSave(Requirement r) {
		runSourceChecks(r);
		return requirements.save(r);
	}

	/** 人工确认解析无误；公司级条件同时确认是否适用于本批次全部岗位。 */
	public Requirement confirm(Long requirementId, boolean appliesToAllPositions) {
		Requirement r = requirements.findById(requirementId)
			.orElseThrow(() -> new NotFoundException("条件", requirementId));
		r.setConfirmed(true);
		r.setAppliesToAllPositions(r.isCompanyLevel() && appliesToAllPositions);
		return r;
	}

	/** 人工逐条看过公告，确认条件已录全（例如没有用模型抽取时）。 */
	public void markAnnouncementChecked(Long announcementId) {
		announcements.findById(announcementId)
			.orElseThrow(() -> new NotFoundException("公告", announcementId))
			.setCheckedAt(Instant.now());
	}

	public void markPositionChecked(Long positionId) {
		positions.findById(positionId)
			.orElseThrow(() -> new NotFoundException("岗位", positionId))
			.setCheckedAt(Instant.now());
	}

	/**
	 * JD 原文改动后，旧的核查结论失效：重新核对岗位级条件的引用，
	 * 模型抽取的条件退回待确认，岗位标记为尚未核查。
	 */
	public void onJdChanged(Position pos) {
		pos.setCheckedAt(null);
		for (Requirement r : requirements.findByPositionIdOrderByIdAsc(pos.getId())) {
			runSourceChecks(r);
			if (r.getOrigin() == RequirementOrigin.LLM) {
				r.setConfirmed(false);
			}
		}
	}

	public void delete(Long requirementId) {
		requirements.deleteById(requirementId);
	}

	/** 对照原文做程序核对（不保存）：引用是否在原文中、数值是否在引用中。测评程序也用它。 */
	public void runSourceChecks(Requirement r) {
		r.setQuoteVerified(quoteVerifier.verify(r.getQuote(), r.sourceText()));
		String mismatch = numberMismatch(r);
		if (mismatch != null) {
			r.setParseIssue(join(r.getParseIssue(), mismatch));
		}
	}

	/**
	 * 解析出的关键数值（分数、年龄、日期、届别年份）必须出现在引用原文里。
	 * 例如原文「六级不少于 500 分」却解析成 200、「2026届」却解析成 2027，都会在这里被发现。
	 * 注意：数值出现在引用中只能证明数值存在，不能证明语义理解正确，所以仍需人工确认。
	 */
	static String numberMismatch(Requirement r) {
		List<String> cohortYears = r.getType() == RequirementType.GRADUATION_COHORT && r.getListValues() != null
				? Arrays.stream(r.getListValues().split("[,，、;；/\\s]+")).map(y -> y.replace("届", "").trim())
					.filter(y -> !y.isEmpty()).toList()
				: List.of();
		List<String> badFormat = cohortYears.stream().filter(y -> !y.matches("\\d{4}")).toList();
		String formatIssue = badFormat.isEmpty() ? null : "届别年份「" + String.join("、", badFormat) + "」格式不正确";
		if (r.getQuote() == null || r.getQuote().isBlank()) {
			return formatIssue;
		}
		List<String> numbers = new ArrayList<>();
		Matcher m = NUMBER.matcher(r.getQuote());
		while (m.find()) {
			numbers.add(stripZeros(m.group()));
		}
		List<String> missing = new ArrayList<>();
		cohortYears.stream()
			.filter(y -> y.matches("\\d{4}") && !numbers.contains(y))
			.forEach(y -> missing.add("届别 " + y));
		if (r.getMinScore() != null && !numbers.contains(stripZeros(String.valueOf(r.getMinScore())))) {
			missing.add("分数 " + stripZeros(String.valueOf(r.getMinScore())));
		}
		if (r.getMaxAge() != null && !numbers.contains(String.valueOf(r.getMaxAge()))) {
			missing.add("年龄 " + r.getMaxAge());
		}
		for (LocalDate d : new LocalDate[] { r.getMinDate(), r.getMaxDate(), r.getAgeReferenceDate() }) {
			if (d != null && !dateInNumbers(d, numbers)) {
				missing.add("日期 " + d);
			}
		}
		String missingIssue = missing.isEmpty() ? null : "解析出的" + String.join("、", missing) + " 未出现在引用原文中";
		return formatIssue == null ? missingIssue : missingIssue == null ? formatIssue : formatIssue + "；" + missingIssue;
	}

	private static boolean dateInNumbers(LocalDate d, List<String> numbers) {
		return numbers.contains(String.valueOf(d.getYear())) && numbers.contains(String.valueOf(d.getMonthValue()))
				&& numbers.contains(String.valueOf(d.getDayOfMonth()));
	}

	private static String stripZeros(String n) {
		return n.contains(".") ? n.replaceAll("0+$", "").replaceAll("\\.$", "") : n.replaceFirst("^0+(?=\\d)", "");
	}

	/** 把模型抽取结果转成条件对象（不保存）。测评程序也用它，保证测的就是正式流程。 */
	public static Requirement fromExtracted(Company company, ExtractedRequirement e) {
		String quote = blankToNull(e.quote());
		String description = blankToNull(e.description());
		Requirement r = new Requirement(company, parseType(e.type()), RequirementOrigin.LLM,
				description != null ? description : quote != null ? quote : "（模型未给出说明）");
		r.setQuote(quote);
		r.setAppliesTo(blankToNull(e.appliesTo()));
		r.setAlternativeGroup(blankToNull(e.alternativeGroup()));
		r.setLevel(blankToNull(e.level()));
		// 模型常把「没有分数」填成 0，0 分作为门槛没有意义，按未填处理
		r.setMinScore(e.minScore() != null && e.minScore() > 0 ? e.minScore() : null);
		r.setAllowEquivalent(Boolean.TRUE.equals(e.allowEquivalent()));
		r.setMaxAge(e.maxAge() != null && e.maxAge() > 0 ? e.maxAge() : null);
		r.setAgeStrict(Boolean.TRUE.equals(e.ageStrict()));
		r.setListValues(blankToNull(e.listValues()));
		List<String> issues = new ArrayList<>();
		r.setMinDate(parseDate(e.minDate(), "起始日期", issues));
		r.setMaxDate(parseDate(e.maxDate(), "截止日期", issues));
		r.setAgeReferenceDate(parseDate(e.ageReferenceDate(), "年龄计算日", issues));
		r.setParseIssue(issues.isEmpty() ? null : String.join("；", issues));
		return r;
	}

	static RequirementType parseType(String type) {
		if (type == null) {
			return RequirementType.OTHER;
		}
		try {
			return RequirementType.valueOf(type.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			return RequirementType.OTHER;
		}
	}

	/** 原文没写（空）返回 null；写了但解析失败，记为解析问题，不能当作「没有限制」。 */
	static LocalDate parseDate(String s, String what, List<String> issues) {
		if (s == null || s.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(s.trim());
		}
		catch (DateTimeParseException ex) {
			issues.add(what + "「" + s + "」无法解析");
			return null;
		}
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	private static String join(String a, String b) {
		return a == null ? b : Objects.equals(a, b) || a.contains(b) ? a : a + "；" + b;
	}
}
