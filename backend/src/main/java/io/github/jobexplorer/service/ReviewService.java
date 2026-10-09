package io.github.jobexplorer.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.jobexplorer.domain.Enums.MaterialResolution;
import io.github.jobexplorer.domain.Position;
import io.github.jobexplorer.repository.AnnouncementRepository;
import io.github.jobexplorer.repository.PositionRepository;
import io.github.jobexplorer.service.PositionReviewer.MaterialState;
import io.github.jobexplorer.service.PositionReviewer.PositionReview;

/** 岗位的主要状态与待办，以及本人对材料范围、投递前核对的确认。 */
@Service
@Transactional
public class ReviewService {

	private final PositionRepository positions;

	private final AnnouncementRepository announcements;

	private final AssessmentService assessments;

	private final Clock clock;

	public ReviewService(PositionRepository positions, AnnouncementRepository announcements,
			AssessmentService assessments, Clock clock) {
		this.positions = positions;
		this.announcements = announcements;
		this.assessments = assessments;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PositionReview review(Long positionId) {
		Position pos = find(positionId);
		List<String> checkedAnnouncements = announcements.findByCompanyIdOrderByCreatedAtDesc(pos.getCompany().getId())
			.stream()
			.filter(a -> a.getCheckedAt() != null)
			.map(a -> "「" + a.getTitle() + "」")
			.toList();
		boolean hasJd = pos.getJdText() != null && !pos.getJdText().isBlank();
		MaterialState material = new MaterialState(hasJd, PositionReviewer.findReferences(pos.getJdText()),
				pos.getMaterialResolution(), pos.getMaterialNote(), checkedAnnouncements);
		return PositionReviewer.review(assessments.assessPosition(positionId), material, pos.getDeadline(),
				LocalDate.now(clock), pos.getPreApplyCheckedAt());
	}

	/**
	 * 本人确认材料范围，必须写明依据（看了哪份材料、在哪里看的）；resolution 为 null 表示撤销。
	 * 「看过但仍无法判断」不记录，待办保留。
	 */
	public PositionReview resolveMaterial(Long positionId, MaterialResolution resolution, String note) {
		Position pos = find(positionId);
		if (resolution != null) {
			if (pos.getJdText() == null || pos.getJdText().isBlank()) {
				throw new IllegalArgumentException("请先录入岗位原文，再确认材料范围");
			}
			if (note == null || note.isBlank()) {
				throw new IllegalArgumentException("请写明依据，例如「官网岗位详情页，未提及其他公告或附件」");
			}
		}
		pos.setMaterialResolution(resolution, note == null ? null : note.trim());
		return review(positionId);
	}

	/**
	 * 投递前核对：本人对照官网再看一遍，确认岗位信息没有变化。
	 * 只有所有适用条件都已确认满足、没有待办时才能记录，否则说明还差什么。
	 */
	public PositionReview markPreApplyChecked(Long positionId) {
		PositionReview before = review(positionId);
		if (!before.readyForPreApplyCheck()) {
			throw new IllegalArgumentException("还不能做投递前核对：" + before.statusReason()
					+ (before.todos().isEmpty() ? "" : "，还有 " + before.todos().size() + " 项待办"));
		}
		find(positionId).setPreApplyCheckedAt(Instant.now(clock));
		return review(positionId);
	}

	private Position find(Long id) {
		return positions.findById(id).orElseThrow(() -> new NotFoundException("岗位", id));
	}
}
