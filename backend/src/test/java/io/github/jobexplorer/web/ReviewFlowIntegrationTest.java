package io.github.jobexplorer.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.github.jobexplorer.domain.Company;
import io.github.jobexplorer.domain.Enums.MaterialResolution;
import io.github.jobexplorer.domain.Enums.RequirementOrigin;
import io.github.jobexplorer.domain.Enums.RequirementType;
import io.github.jobexplorer.domain.Enums.ReviewStatus;
import io.github.jobexplorer.domain.Enums.TodoKind;
import io.github.jobexplorer.domain.Position;
import io.github.jobexplorer.domain.Requirement;
import io.github.jobexplorer.repository.CompanyRepository;
import io.github.jobexplorer.repository.PositionRepository;
import io.github.jobexplorer.service.PositionReviewer.PositionReview;
import io.github.jobexplorer.service.RequirementService;
import io.github.jobexplorer.service.ReviewService;
import io.github.jobexplorer.web.Dtos.PositionRequest;

/**
 * 岗位从「待核查」到「已核实可报」的完整流程，以及原文改动后状态回退。
 * 走真实的 Spring 容器和内存数据库。
 */
@SpringBootTest(properties = { "jobexplorer.demo-data=false", "spring.ai.model.chat=none",
		"spring.datasource.url=jdbc:h2:mem:reviewflow;DB_CLOSE_DELAY=-1" })
class ReviewFlowIntegrationTest {

	private static final String JD = "任职要求：硕士研究生及以上学历。";

	@Autowired
	CompanyRepository companies;

	@Autowired
	PositionRepository positions;

	@Autowired
	RequirementService requirementService;

	@Autowired
	ReviewService reviews;

	@Autowired
	PositionController positionController;

	/** 已核查、条件已人工确认的岗位。演示档案是硕士，条件满足。 */
	private Long checkedPosition() {
		Company c = companies.save(new Company("测试公司"));
		Position p = new Position(c, "数据岗");
		p.setJdText(JD);
		p.setCheckedAt(Instant.now());
		positions.save(p);
		Requirement r = new Requirement(c, RequirementType.DEGREE, RequirementOrigin.LLM, "硕士及以上");
		r.setPosition(p);
		r.setLevel("硕士");
		r.setQuote("硕士研究生及以上学历");
		requirementService.confirm(requirementService.verifyAndSave(r).getId(), false);
		return p.getId();
	}

	@Test
	void 确认材料范围并完成投递前核对后_才是已核实可报() {
		Long id = checkedPosition();

		PositionReview first = reviews.review(id);
		assertThat(first.status()).as("材料范围未确认").isEqualTo(ReviewStatus.TO_CHECK);
		assertThat(first.todos()).extracting(t -> t.kind()).containsExactly(TodoKind.MATERIAL_SCOPE);
		assertThatThrownBy(() -> reviews.markPreApplyChecked(id)).hasMessageContaining("还不能做投递前核对");

		PositionReview second = reviews.resolveMaterial(id, MaterialResolution.PAGE_COMPLETE, "官网详情页，未提及公告或附件");
		assertThat(second.status()).isEqualTo(ReviewStatus.PRELIM_MATCH);
		assertThat(second.readyForPreApplyCheck()).isTrue();

		assertThat(reviews.markPreApplyChecked(id).status()).isEqualTo(ReviewStatus.VERIFIED);
	}

	@Test
	void 确认材料范围必须写依据() {
		Long id = checkedPosition();
		assertThatThrownBy(() -> reviews.resolveMaterial(id, MaterialResolution.PAGE_COMPLETE, " "))
			.hasMessageContaining("依据");
	}

	@Test
	void 修改JD后_材料确认和投递前核对一并失效() {
		Long id = checkedPosition();
		reviews.resolveMaterial(id, MaterialResolution.PAGE_COMPLETE, "官网详情页");
		reviews.markPreApplyChecked(id);

		positionController.update(id, new PositionRequest("数据岗", null, null, null,
				"任职要求：硕士研究生及以上学历，其他要求详见招聘公告。", null, null, null));

		Position p = positions.findById(id).orElseThrow();
		assertThat(p.getMaterialResolution()).isNull();
		assertThat(p.getPreApplyCheckedAt()).isNull();
		PositionReview after = reviews.review(id);
		assertThat(after.status()).isEqualTo(ReviewStatus.TO_CHECK);
		assertThat(after.todos()).anyMatch(t -> t.kind() == TodoKind.MISSING_MATERIAL && t.text().contains("详见招聘公告"));
	}
}
