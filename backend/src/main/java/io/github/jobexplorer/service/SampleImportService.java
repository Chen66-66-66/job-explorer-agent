package io.github.jobexplorer.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.jobexplorer.domain.Announcement;
import io.github.jobexplorer.domain.Company;
import io.github.jobexplorer.domain.Position;
import io.github.jobexplorer.repository.AnnouncementRepository;
import io.github.jobexplorer.repository.CompanyRepository;
import io.github.jobexplorer.repository.PositionRepository;
import io.github.jobexplorer.service.SampleBundle.AnnouncementPart;
import io.github.jobexplorer.service.SampleBundle.CompanyPart;
import io.github.jobexplorer.service.SampleBundle.PositionPart;

/**
 * 导入本地样本，并给出导入报告：导入了什么、缺什么、和已有数据相比变了什么。
 *
 * 原则：
 * - 只录入原文，不调用大模型，也不把任何东西标为已核查；
 * - 同一份样本重复导入不产生重复数据；
 * - 岗位 JD 有变化时按页面修改的同一套规则处理：原有确认、判定、材料确认、投递前核对一并失效；
 * - 同名公告原文不同时不覆盖：公告改动会影响已抽取的公司级条件，交给人核对；
 * - 样本里没写的字段不清空已有数据。
 */
@Service
@Transactional
public class SampleImportService {

	public enum ImportOutcome {
		CREATED, UPDATED, UNCHANGED, SKIPPED
	}

	/** 一条公告或岗位的导入结果；notes 说明变化和缺失项。 */
	public record ItemResult(String title, ImportOutcome outcome, Long id, List<String> notes) {
	}

	public record ImportReport(Long companyId, String companyName, boolean companyCreated,
			List<ItemResult> announcements, List<ItemResult> positions, List<String> warnings) {
	}

	private final CompanyRepository companies;

	private final AnnouncementRepository announcements;

	private final PositionRepository positions;

	private final RequirementService requirementService;

	private final Clock clock;

	public SampleImportService(CompanyRepository companies, AnnouncementRepository announcements,
			PositionRepository positions, RequirementService requirementService, Clock clock) {
		this.companies = companies;
		this.announcements = announcements;
		this.positions = positions;
		this.requirementService = requirementService;
		this.clock = clock;
	}

	public ImportReport importBundle(SampleBundle bundle) {
		if (bundle == null || bundle.company() == null || blank(bundle.company().name())) {
			throw new IllegalArgumentException("样本缺少公司名称（company.name）");
		}
		CompanyPart cp = bundle.company();
		String name = cp.name().trim();
		Company company = companies.findFirstByName(name).orElse(null);
		boolean created = company == null;
		if (created) {
			company = companies.save(new Company(name));
		}
		// 只补空字段，不覆盖本人已经填写的内容
		if (company.getGroupName() == null) {
			company.setGroupName(trimToNull(cp.groupName()));
		}
		if (company.getCareerSiteUrl() == null) {
			company.setCareerSiteUrl(trimToNull(cp.careerSiteUrl()));
		}
		if (company.getPlatform() == null) {
			company.setPlatform(trimToNull(cp.platform()));
		}

		List<String> warnings = new ArrayList<>();
		List<ItemResult> annResults = new ArrayList<>();
		for (AnnouncementPart a : orEmpty(bundle.announcements())) {
			annResults.add(importAnnouncement(company, a));
		}
		if (announcements.findByCompanyIdOrderByCreatedAtDesc(company.getId()).isEmpty()) {
			warnings.add("没有公告：学历、年龄等通用条件常写在公告里。岗位会提示「材料范围」待办，可以补录公告，或逐个确认岗位页面即完整要求");
		}

		List<ItemResult> posResults = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (PositionPart p : orEmpty(bundle.positions())) {
			posResults.add(importPosition(company, p, seen));
		}
		if (orEmpty(bundle.positions()).isEmpty()) {
			warnings.add("样本里没有岗位");
		}
		return new ImportReport(company.getId(), company.getName(), created, annResults, posResults, warnings);
	}

	private ItemResult importAnnouncement(Company company, AnnouncementPart a) {
		if (a == null || blank(a.title()) || blank(a.content())) {
			return new ItemResult(a == null || blank(a.title()) ? "（无标题）" : a.title().trim(), ImportOutcome.SKIPPED,
					null, List.of("缺少标题或原文，未导入"));
		}
		String title = a.title().trim();
		String content = normalize(a.content());
		Announcement existing = announcements.findByCompanyIdOrderByCreatedAtDesc(company.getId())
			.stream()
			.filter(x -> x.getTitle().equals(title))
			.findFirst()
			.orElse(null);
		if (existing != null) {
			if (normalize(existing.getContent()).equals(content)) {
				return new ItemResult(title, ImportOutcome.UNCHANGED, existing.getId(), List.of());
			}
			return new ItemResult(title, ImportOutcome.SKIPPED, existing.getId(),
					List.of("已有同名公告但原文不同，未覆盖：公告改动会影响已抽取的公司级条件，请人工核对；如果是新一批公告，请改标题后导入"));
		}
		List<String> notes = new ArrayList<>();
		Announcement ann = new Announcement(company, title, content);
		ann.setSourceUrl(trimToNull(a.sourceUrl()));
		ann.setViewedAt(parseDate(a.viewedAt(), "查看日期", notes));
		announcements.save(ann);
		if (blank(a.viewedAt())) {
			notes.add("未记录查看日期：公开信息会过期，建议补上");
		}
		notes.add("尚未核查：请用模型抽取条件，或人工看完后标记已核查");
		return new ItemResult(title, ImportOutcome.CREATED, ann.getId(), notes);
	}

	private ItemResult importPosition(Company company, PositionPart p, Set<String> seen) {
		if (p == null || blank(p.title())) {
			return new ItemResult("（无岗位名称）", ImportOutcome.SKIPPED, null, List.of("缺少岗位名称，未导入"));
		}
		String title = p.title().trim();
		String unit = trimToNull(p.unit());
		String location = trimToNull(p.location());
		if (!seen.add(key(title, unit, location))) {
			return new ItemResult(title, ImportOutcome.SKIPPED, null, List.of("样本内重复（名称、单位、地点都相同），未导入"));
		}
		List<String> notes = new ArrayList<>();
		LocalDate deadline = parseDate(p.deadline(), "截止日期", notes);
		LocalDate viewedAt = parseDate(p.viewedAt(), "查看日期", notes);
		String sourceUrl = trimToNull(p.sourceUrl());
		String jd = blank(p.jdText()) ? null : normalize(p.jdText());

		Position pos = positions.findByCompanyIdOrderByCreatedAtAsc(company.getId())
			.stream()
			.filter(x -> key(x.getTitle(), x.getUnit(), x.getLocation()).equals(key(title, unit, location)))
			.findFirst()
			.orElse(null);
		ImportOutcome outcome;
		if (pos == null) {
			pos = new Position(company, title);
			pos.setUnit(unit);
			pos.setLocation(location);
			pos.setDeadline(deadline);
			pos.setSourceUrl(sourceUrl);
			pos.setViewedAt(viewedAt);
			pos.setJdText(jd);
			positions.save(pos);
			outcome = ImportOutcome.CREATED;
		}
		else {
			List<String> changes = new ArrayList<>();
			if (deadline != null && !deadline.equals(pos.getDeadline())) {
				changes.add("截止日期：" + (pos.getDeadline() == null ? "无" : pos.getDeadline()) + " → " + deadline);
				pos.setDeadline(deadline);
			}
			if (sourceUrl != null && !sourceUrl.equals(pos.getSourceUrl())) {
				changes.add("来源链接已更新");
				pos.setSourceUrl(sourceUrl);
			}
			if (viewedAt != null && (pos.getViewedAt() == null || viewedAt.isAfter(pos.getViewedAt()))) {
				changes.add("查看日期更新为 " + viewedAt);
				pos.setViewedAt(viewedAt);
			}
			if (jd != null && !Objects.equals(jd, pos.getJdText() == null ? null : normalize(pos.getJdText()))) {
				pos.setJdText(jd);
				requirementService.onJdChanged(pos);
				changes.add("JD 原文有变化：原有确认、本人判定、材料确认和投递前核对已失效，需要重新核查");
			}
			if (jd == null && pos.getJdText() != null) {
				notes.add("样本没有 JD 原文，保留已有的 JD");
			}
			notes.addAll(0, changes);
			outcome = changes.isEmpty() ? ImportOutcome.UNCHANGED : ImportOutcome.UPDATED;
		}
		notes.addAll(missingItems(pos));
		return new ItemResult(title, outcome, pos.getId(), notes);
	}

	/** 缺失项提示：这些信息缺了，后面的判断或投递会受影响。 */
	private List<String> missingItems(Position pos) {
		List<String> missing = new ArrayList<>();
		if (pos.getJdText() == null) {
			missing.add("没有 JD 原文：无法抽取岗位条件");
		}
		else {
			List<String> refs = PositionReviewer.findReferences(pos.getJdText());
			if (!refs.isEmpty()) {
				missing.add("JD 提到「" + String.join("」「", refs) + "」：需要核对引用的材料");
			}
		}
		LocalDate today = LocalDate.now(clock);
		if (pos.getDeadline() == null) {
			missing.add("没有截止日期");
		}
		else if (pos.getDeadline().isBefore(today)) {
			missing.add("截止日期已过（" + pos.getDeadline() + "）");
		}
		if (pos.getSourceUrl() == null) {
			missing.add("没有来源链接：投递前核对时不方便回到官网");
		}
		if (pos.getViewedAt() == null) {
			missing.add("未记录查看日期");
		}
		return missing;
	}

	/** 原文没写返回 null；写了但解析失败，记入说明，不能当作没有。 */
	private static LocalDate parseDate(String s, String what, List<String> notes) {
		if (blank(s)) {
			return null;
		}
		try {
			return LocalDate.parse(s.trim());
		}
		catch (DateTimeParseException ex) {
			notes.add(what + "「" + s + "」无法解析（应为 yyyy-MM-dd），未导入该字段");
			return null;
		}
	}

	private static String key(String title, String unit, String location) {
		return title.trim() + "|" + (unit == null ? "" : unit.trim()) + "|" + (location == null ? "" : location.trim());
	}

	/** 统一换行符，避免同一段原文因 Windows / Unix 换行不同被当成改动。 */
	private static String normalize(String s) {
		return s.replace("\r\n", "\n");
	}

	private static <T> List<T> orEmpty(List<T> list) {
		return list == null ? List.of() : list;
	}

	private static boolean blank(String s) {
		return s == null || s.isBlank();
	}

	private static String trimToNull(String s) {
		return blank(s) ? null : s.trim();
	}
}
