package io.github.jobexplorer.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.jobexplorer.domain.Company;
import io.github.jobexplorer.domain.Enums.RequirementOrigin;
import io.github.jobexplorer.domain.Requirement;
import io.github.jobexplorer.repository.AnnouncementRepository;
import io.github.jobexplorer.repository.CompanyRepository;
import io.github.jobexplorer.repository.PositionRepository;
import io.github.jobexplorer.service.NotFoundException;
import io.github.jobexplorer.service.RequirementService;
import io.github.jobexplorer.web.Dtos.ConfirmRequest;
import io.github.jobexplorer.web.Dtos.RequirementRequest;
import io.github.jobexplorer.web.Dtos.RequirementView;
import jakarta.validation.Valid;

/** 条件的抽取与人工录入 / 删除。 */
@RestController
@RequestMapping("/api")
@Transactional
public class RequirementController {

	private final RequirementService requirementService;

	private final CompanyRepository companies;

	private final AnnouncementRepository announcements;

	private final PositionRepository positions;

	public RequirementController(RequirementService requirementService, CompanyRepository companies,
			AnnouncementRepository announcements, PositionRepository positions) {
		this.requirementService = requirementService;
		this.companies = companies;
		this.announcements = announcements;
		this.positions = positions;
	}

	@PostMapping("/announcements/{id}/extract")
	public List<RequirementView> extractFromAnnouncement(@PathVariable Long id) {
		return requirementService.extractFromAnnouncement(id).stream().map(RequirementView::of).toList();
	}

	@PostMapping("/requirements")
	@ResponseStatus(HttpStatus.CREATED)
	public RequirementView create(@Valid @RequestBody RequirementRequest req) {
		if (req.announcementId() != null && req.positionId() != null) {
			throw new IllegalArgumentException("公告和岗位只能选一个作为出处");
		}
		Company company = companies.findById(req.companyId())
			.orElseThrow(() -> new NotFoundException("公司", req.companyId()));
		Requirement r = new Requirement(company, req.type(), RequirementOrigin.MANUAL, req.description());
		if (req.announcementId() != null) {
			r.setAnnouncement(announcements.findById(req.announcementId())
				.orElseThrow(() -> new NotFoundException("公告", req.announcementId())));
		}
		if (req.positionId() != null) {
			r.setPosition(positions.findById(req.positionId())
				.orElseThrow(() -> new NotFoundException("岗位", req.positionId())));
		}
		r.setQuote(req.quote());
		r.setLevel(req.level());
		r.setMinScore(req.minScore());
		r.setAllowEquivalent(Boolean.TRUE.equals(req.allowEquivalent()));
		r.setMinDate(req.minDate());
		r.setMaxDate(req.maxDate());
		r.setMaxAge(req.maxAge());
		r.setAgeReferenceDate(req.ageReferenceDate());
		r.setListValues(req.listValues());
		r.setAgeStrict(Boolean.TRUE.equals(req.ageStrict()));
		r.setAppliesTo(blankToNull(req.appliesTo()));
		r.setAlternativeGroup(blankToNull(req.alternativeGroup()));
		r.setAppliesToAllPositions(r.isCompanyLevel() && Boolean.TRUE.equals(req.appliesToAllPositions()));
		return RequirementView.of(requirementService.verifyAndSave(r));
	}

	@PostMapping("/requirements/{id}/confirm")
	public RequirementView confirm(@PathVariable Long id, @RequestBody(required = false) ConfirmRequest req) {
		boolean all = req != null && Boolean.TRUE.equals(req.appliesToAllPositions());
		return RequirementView.of(requirementService.confirm(id, all));
	}

	@PostMapping("/announcements/{id}/mark-checked")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void markAnnouncementChecked(@PathVariable Long id) {
		requirementService.markAnnouncementChecked(id);
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	@DeleteMapping("/requirements/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		requirementService.delete(id);
	}
}
