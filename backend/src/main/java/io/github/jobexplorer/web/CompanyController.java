package io.github.jobexplorer.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.jobexplorer.domain.Announcement;
import io.github.jobexplorer.domain.Company;
import io.github.jobexplorer.domain.Enums.CompanyStatus;
import io.github.jobexplorer.domain.Note;
import io.github.jobexplorer.domain.Position;
import io.github.jobexplorer.repository.AnnouncementRepository;
import io.github.jobexplorer.repository.CompanyRepository;
import io.github.jobexplorer.repository.NoteRepository;
import io.github.jobexplorer.repository.PositionRepository;
import io.github.jobexplorer.repository.RequirementRepository;
import io.github.jobexplorer.service.AssessmentService;
import io.github.jobexplorer.service.AssessmentService.Assessment;
import io.github.jobexplorer.service.NotFoundException;
import io.github.jobexplorer.web.Dtos.AnnouncementRequest;
import io.github.jobexplorer.web.Dtos.AnnouncementView;
import io.github.jobexplorer.web.Dtos.CompanyRequest;
import io.github.jobexplorer.web.Dtos.CompanyView;
import io.github.jobexplorer.web.Dtos.NoteRequest;
import io.github.jobexplorer.web.Dtos.NoteView;
import io.github.jobexplorer.web.Dtos.PositionRequest;
import io.github.jobexplorer.web.Dtos.PositionView;
import io.github.jobexplorer.web.Dtos.RequirementView;
import jakarta.validation.Valid;

/** 公司及其下属资源（公告、岗位、笔记、公司级条件）。 */
@RestController
@RequestMapping("/api/companies")
@Transactional
public class CompanyController {

	private final CompanyRepository companies;

	private final AnnouncementRepository announcements;

	private final PositionRepository positions;

	private final RequirementRepository requirements;

	private final NoteRepository notes;

	private final AssessmentService assessments;

	public CompanyController(CompanyRepository companies, AnnouncementRepository announcements,
			PositionRepository positions, RequirementRepository requirements, NoteRepository notes,
			AssessmentService assessments) {
		this.companies = companies;
		this.announcements = announcements;
		this.positions = positions;
		this.requirements = requirements;
		this.notes = notes;
		this.assessments = assessments;
	}

	@GetMapping
	public List<CompanyView> list() {
		return companies.findAll()
			.stream()
			.map(c -> CompanyView.of(c, assessments.assessCompany(c.getId()).overall()))
			.toList();
	}

	@GetMapping("/{id}")
	public CompanyView get(@PathVariable Long id) {
		Company c = find(id);
		return CompanyView.of(c, assessments.assessCompany(id).overall());
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CompanyView create(@Valid @RequestBody CompanyRequest req) {
		Company c = new Company(req.name());
		apply(c, req);
		companies.save(c);
		return CompanyView.of(c, assessments.assessCompany(c.getId()).overall());
	}

	@PatchMapping("/{id}")
	public CompanyView update(@PathVariable Long id, @Valid @RequestBody CompanyRequest req) {
		Company c = find(id);
		c.setName(req.name());
		apply(c, req);
		return CompanyView.of(c, assessments.assessCompany(id).overall());
	}

	@GetMapping("/{id}/assessment")
	public Assessment assessment(@PathVariable Long id) {
		find(id);
		return assessments.assessCompany(id);
	}

	@GetMapping("/{id}/announcements")
	public List<AnnouncementView> announcements(@PathVariable Long id) {
		return announcements.findByCompanyIdOrderByCreatedAtDesc(id).stream().map(AnnouncementView::of).toList();
	}

	@PostMapping("/{id}/announcements")
	@ResponseStatus(HttpStatus.CREATED)
	public AnnouncementView addAnnouncement(@PathVariable Long id, @Valid @RequestBody AnnouncementRequest req) {
		Announcement a = new Announcement(find(id), req.title(), req.content());
		a.setSourceUrl(req.sourceUrl());
		a.setViewedAt(req.viewedAt());
		return AnnouncementView.of(announcements.save(a));
	}

	@GetMapping("/{id}/requirements")
	public List<RequirementView> companyRequirements(@PathVariable Long id) {
		return requirements.findByCompanyIdAndPositionIsNullOrderByIdAsc(id)
			.stream()
			.map(RequirementView::of)
			.toList();
	}

	@GetMapping("/{id}/positions")
	public List<PositionView> positions(@PathVariable Long id) {
		return positions.findByCompanyIdOrderByCreatedAtAsc(id)
			.stream()
			.map(p -> PositionView.of(p, assessments.assessPosition(p.getId()).overall()))
			.toList();
	}

	@PostMapping("/{id}/positions")
	@ResponseStatus(HttpStatus.CREATED)
	public PositionView addPosition(@PathVariable Long id, @Valid @RequestBody PositionRequest req) {
		Position p = new Position(find(id), req.title());
		PositionController.apply(p, req);
		positions.save(p);
		return PositionView.of(p, assessments.assessPosition(p.getId()).overall());
	}

	@GetMapping("/{id}/notes")
	public List<NoteView> notes(@PathVariable Long id) {
		return notes.findByCompanyIdOrderByCreatedAtDesc(id).stream().map(NoteView::of).toList();
	}

	@PostMapping("/{id}/notes")
	@ResponseStatus(HttpStatus.CREATED)
	public NoteView addNote(@PathVariable Long id, @Valid @RequestBody NoteRequest req) {
		Position p = req.positionId() == null ? null
				: positions.findById(req.positionId()).orElseThrow(() -> new NotFoundException("岗位", req.positionId()));
		return NoteView.of(notes.save(new Note(find(id), p, req.content())));
	}

	private Company find(Long id) {
		return companies.findById(id).orElseThrow(() -> new NotFoundException("公司", id));
	}

	private static void apply(Company c, CompanyRequest req) {
		c.setGroupName(req.groupName());
		c.setCareerSiteUrl(req.careerSiteUrl());
		c.setPlatform(req.platform());
		c.setSummary(req.summary());
		c.setStatus(req.status() == null ? CompanyStatus.CANDIDATE : req.status());
	}
}
