package io.github.jobexplorer.web;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.jobexplorer.repository.ExtractionRejectionRepository;
import io.github.jobexplorer.service.RequirementService;
import io.github.jobexplorer.web.Dtos.RejectionView;
import io.github.jobexplorer.web.Dtos.RequirementView;

/** 模型抽取结果的驳回记录：查看与撤销驳回。驳回本身在 RequirementController。 */
@RestController
@RequestMapping("/api")
@Transactional
public class RejectionController {

	private final ExtractionRejectionRepository rejections;

	private final RequirementService requirementService;

	public RejectionController(ExtractionRejectionRepository rejections, RequirementService requirementService) {
		this.rejections = rejections;
		this.requirementService = requirementService;
	}

	@GetMapping("/positions/{id}/rejections")
	public List<RejectionView> positionRejections(@PathVariable Long id) {
		return rejections.findByPositionIdOrderByRejectedAtDesc(id).stream().map(RejectionView::of).toList();
	}

	/** 公司级（来自公告）抽取结果的驳回记录。 */
	@GetMapping("/companies/{id}/rejections")
	public List<RejectionView> companyRejections(@PathVariable Long id) {
		return rejections.findByCompanyIdAndPositionIsNullOrderByRejectedAtDesc(id)
			.stream()
			.map(RejectionView::of)
			.toList();
	}

	/** 撤销驳回：恢复为待确认的条件。 */
	@PostMapping("/rejections/{id}/restore")
	public RequirementView restore(@PathVariable Long id) {
		return RequirementView.of(requirementService.restore(id));
	}
}
