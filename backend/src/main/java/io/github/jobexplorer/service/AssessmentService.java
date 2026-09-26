package io.github.jobexplorer.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.jobexplorer.config.CandidateProfile;
import io.github.jobexplorer.domain.Enums.Verdict;
import io.github.jobexplorer.domain.Position;
import io.github.jobexplorer.domain.Requirement;
import io.github.jobexplorer.repository.AnnouncementRepository;
import io.github.jobexplorer.repository.PositionRepository;
import io.github.jobexplorer.repository.RequirementRepository;

/**
 * 分两层判断：
 * 公司层只看公告里的通用条件，确认适用于全部岗位的条件不符合时整家剪掉；
 * 岗位层在公司条件之外，再加上该岗位 JD 的条件。
 * 同时记录核查是否完整：公告或 JD 还没核查过，整体最多是「已核查部分通过」。
 */
@Service
@Transactional(readOnly = true)
public class AssessmentService {

	private final RequirementRepository requirements;

	private final AnnouncementRepository announcements;

	private final PositionRepository positions;

	private final EligibilityChecker checker;

	private final CandidateProfile profile;

	private final Clock clock;

	public AssessmentService(RequirementRepository requirements, AnnouncementRepository announcements,
			PositionRepository positions, EligibilityChecker checker, CandidateProfile profile, Clock clock) {
		this.requirements = requirements;
		this.announcements = announcements;
		this.positions = positions;
		this.checker = checker;
		this.profile = profile;
		this.clock = clock;
	}

	public Assessment assessCompany(Long companyId) {
		List<String> pending = pendingAnnouncements(companyId);
		return evaluate(requirements.findByCompanyIdAndPositionIsNullOrderByIdAsc(companyId), pending);
	}

	public Assessment assessPosition(Long positionId) {
		Position pos = positions.findById(positionId).orElseThrow(() -> new NotFoundException("岗位", positionId));
		Long companyId = pos.getCompany().getId();
		List<Requirement> all = new ArrayList<>(requirements.findByCompanyIdAndPositionIsNullOrderByIdAsc(companyId));
		all.addAll(requirements.findByPositionIdOrderByIdAsc(positionId));
		List<String> pending = new ArrayList<>(pendingAnnouncements(companyId));
		if (pos.getJdText() != null && !pos.getJdText().isBlank() && pos.getCheckedAt() == null) {
			pending.add("岗位 JD 尚未核查");
		}
		return evaluate(all, pending);
	}

	private List<String> pendingAnnouncements(Long companyId) {
		return announcements.findByCompanyIdOrderByCreatedAtDesc(companyId)
			.stream()
			.filter(a -> a.getCheckedAt() == null)
			.map(a -> "公告「" + a.getTitle() + "」尚未核查")
			.toList();
	}

	private Assessment evaluate(List<Requirement> reqs, List<String> pending) {
		LocalDate today = LocalDate.now(clock);
		List<CheckResult> results = reqs.stream().map(r -> checker.check(r, profile, today)).toList();
		return new Assessment(VerdictAggregator.overall(results, pending.isEmpty()), results, pending);
	}

	/** pending 列出还没核查的来源，帮助用户知道结论为什么只是「部分」。 */
	public record Assessment(Verdict overall, List<CheckResult> results, List<String> pending) {
	}
}
