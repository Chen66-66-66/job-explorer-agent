package io.github.jobexplorer.web;

import java.time.Instant;
import java.time.LocalDate;

import io.github.jobexplorer.domain.Announcement;
import io.github.jobexplorer.domain.Company;
import io.github.jobexplorer.domain.Enums.CompanyStatus;
import io.github.jobexplorer.domain.Enums.PositionStatus;
import io.github.jobexplorer.domain.Enums.RequirementOrigin;
import io.github.jobexplorer.domain.Enums.RequirementType;
import io.github.jobexplorer.domain.Enums.Verdict;
import io.github.jobexplorer.domain.Note;
import io.github.jobexplorer.domain.Position;
import io.github.jobexplorer.domain.Requirement;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 接口的输入输出结构。实体不直接暴露给前端。 */
public final class Dtos {

	private Dtos() {
	}

	public record CompanyView(Long id, String name, String groupName, String careerSiteUrl, String platform,
			String summary, CompanyStatus status, Verdict verdict) {

		static CompanyView of(Company c, Verdict verdict) {
			return new CompanyView(c.getId(), c.getName(), c.getGroupName(), c.getCareerSiteUrl(), c.getPlatform(),
					c.getSummary(), c.getStatus(), verdict);
		}
	}

	public record CompanyRequest(@NotBlank String name, String groupName, String careerSiteUrl, String platform,
			String summary, CompanyStatus status) {
	}

	public record AnnouncementView(Long id, String title, String sourceUrl, LocalDate viewedAt, String content,
			Instant checkedAt) {

		static AnnouncementView of(Announcement a) {
			return new AnnouncementView(a.getId(), a.getTitle(), a.getSourceUrl(), a.getViewedAt(), a.getContent(),
					a.getCheckedAt());
		}
	}

	public record AnnouncementRequest(@NotBlank String title, String sourceUrl, LocalDate viewedAt,
			@NotBlank String content) {
	}

	public record PositionView(Long id, Long companyId, String companyName, String title, String unit,
			String location, LocalDate deadline, String jdText, String sourceUrl, LocalDate viewedAt,
			PositionStatus status, Instant checkedAt, Verdict verdict) {

		static PositionView of(Position p, Verdict verdict) {
			return new PositionView(p.getId(), p.getCompany().getId(), p.getCompany().getName(), p.getTitle(),
					p.getUnit(), p.getLocation(), p.getDeadline(), p.getJdText(), p.getSourceUrl(), p.getViewedAt(),
					p.getStatus(), p.getCheckedAt(), verdict);
		}
	}

	public record PositionRequest(@NotBlank String title, String unit, String location, LocalDate deadline,
			String jdText, String sourceUrl, LocalDate viewedAt, PositionStatus status) {
	}

	public record RequirementView(Long id, RequirementType type, RequirementOrigin origin, String description,
			String quote, boolean quoteVerified, String level, Double minScore, boolean allowEquivalent,
			LocalDate minDate, LocalDate maxDate, Integer maxAge, boolean ageStrict, LocalDate ageReferenceDate,
			String listValues, String appliesTo, String alternativeGroup, boolean confirmed,
			boolean appliesToAllPositions, String parseIssue, Long announcementId, Long positionId) {

		static RequirementView of(Requirement r) {
			return new RequirementView(r.getId(), r.getType(), r.getOrigin(), r.getDescription(), r.getQuote(),
					r.isQuoteVerified(), r.getLevel(), r.getMinScore(), r.isAllowEquivalent(), r.getMinDate(),
					r.getMaxDate(), r.getMaxAge(), r.isAgeStrict(), r.getAgeReferenceDate(), r.getListValues(),
					r.getAppliesTo(), r.getAlternativeGroup(), r.isConfirmed(), r.isAppliesToAllPositions(),
					r.getParseIssue(), r.getAnnouncement() == null ? null : r.getAnnouncement().getId(),
					r.getPosition() == null ? null : r.getPosition().getId());
		}
	}

	/** 人工录入条件。announcementId 与 positionId 至多填一个；都不填则为无出处的公司级条件。 */
	public record RequirementRequest(@NotNull Long companyId, Long announcementId, Long positionId,
			@NotNull RequirementType type, @NotBlank String description, String quote, String level, Double minScore,
			Boolean allowEquivalent, LocalDate minDate, LocalDate maxDate, Integer maxAge, Boolean ageStrict,
			LocalDate ageReferenceDate, String listValues, String appliesTo, String alternativeGroup,
			Boolean appliesToAllPositions) {
	}

	/** 人工确认一条条件；公司级条件同时确认是否适用于本批次全部岗位。 */
	public record ConfirmRequest(Boolean appliesToAllPositions) {
	}

	public record NoteView(Long id, String content, Long positionId, String positionTitle, Instant createdAt) {

		static NoteView of(Note n) {
			return new NoteView(n.getId(), n.getContent(), n.getPosition() == null ? null : n.getPosition().getId(),
					n.getPosition() == null ? null : n.getPosition().getTitle(), n.getCreatedAt());
		}
	}

	public record NoteRequest(@NotBlank String content, Long positionId) {
	}

	public record SystemStatus(boolean llmAvailable, String candidateName) {
	}

	public record ErrorBody(String message) {
	}
}
