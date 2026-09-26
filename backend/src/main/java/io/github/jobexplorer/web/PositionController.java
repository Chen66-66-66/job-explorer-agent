package io.github.jobexplorer.web;

import java.util.List;
import java.util.Objects;

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

import io.github.jobexplorer.domain.Enums.PositionStatus;
import io.github.jobexplorer.domain.Position;
import io.github.jobexplorer.repository.PositionRepository;
import io.github.jobexplorer.repository.RequirementRepository;
import io.github.jobexplorer.service.AssessmentService;
import io.github.jobexplorer.service.AssessmentService.Assessment;
import io.github.jobexplorer.service.NotFoundException;
import io.github.jobexplorer.service.RequirementService;
import io.github.jobexplorer.web.Dtos.PositionRequest;
import io.github.jobexplorer.web.Dtos.PositionView;
import io.github.jobexplorer.web.Dtos.RequirementView;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/positions")
@Transactional
public class PositionController {

	private final PositionRepository positions;

	private final RequirementRepository requirements;

	private final RequirementService requirementService;

	private final AssessmentService assessments;

	public PositionController(PositionRepository positions, RequirementRepository requirements,
			RequirementService requirementService, AssessmentService assessments) {
		this.positions = positions;
		this.requirements = requirements;
		this.requirementService = requirementService;
		this.assessments = assessments;
	}

	@GetMapping("/{id}")
	public PositionView get(@PathVariable Long id) {
		return PositionView.of(find(id), assessments.assessPosition(id).overall());
	}

	@PatchMapping("/{id}")
	public PositionView update(@PathVariable Long id, @Valid @RequestBody PositionRequest req) {
		Position p = find(id);
		boolean jdChanged = !Objects.equals(p.getJdText(), req.jdText());
		p.setTitle(req.title());
		apply(p, req);
		if (jdChanged) {
			requirementService.onJdChanged(p);
		}
		return PositionView.of(p, assessments.assessPosition(id).overall());
	}

	@GetMapping("/{id}/requirements")
	public List<RequirementView> requirements(@PathVariable Long id) {
		return requirements.findByPositionIdOrderByIdAsc(id).stream().map(RequirementView::of).toList();
	}

	@PostMapping("/{id}/extract")
	public List<RequirementView> extract(@PathVariable Long id) {
		return requirementService.extractFromPosition(id).stream().map(RequirementView::of).toList();
	}

	@PostMapping("/{id}/mark-checked")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void markChecked(@PathVariable Long id) {
		requirementService.markPositionChecked(id);
	}

	@GetMapping("/{id}/assessment")
	public Assessment assessment(@PathVariable Long id) {
		return assessments.assessPosition(id);
	}

	private Position find(Long id) {
		return positions.findById(id).orElseThrow(() -> new NotFoundException("岗位", id));
	}

	static void apply(Position p, PositionRequest req) {
		p.setUnit(req.unit());
		p.setLocation(req.location());
		p.setDeadline(req.deadline());
		p.setJdText(req.jdText());
		p.setSourceUrl(req.sourceUrl());
		p.setViewedAt(req.viewedAt());
		p.setStatus(req.status() == null ? PositionStatus.FOUND : req.status());
	}
}
