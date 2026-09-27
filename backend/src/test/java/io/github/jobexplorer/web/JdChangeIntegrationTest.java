package io.github.jobexplorer.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.github.jobexplorer.domain.Company;
import io.github.jobexplorer.domain.Enums.RequirementOrigin;
import io.github.jobexplorer.domain.Enums.RequirementType;
import io.github.jobexplorer.domain.Enums.Verdict;
import io.github.jobexplorer.domain.Position;
import io.github.jobexplorer.domain.Requirement;
import io.github.jobexplorer.repository.CompanyRepository;
import io.github.jobexplorer.repository.PositionRepository;
import io.github.jobexplorer.repository.RequirementRepository;
import io.github.jobexplorer.service.AssessmentService;
import io.github.jobexplorer.service.RequirementService;
import io.github.jobexplorer.web.Dtos.PositionRequest;

/**
 * 人工把关是否可靠：确认过的条件，在 JD 被修改后必须失效，不能继续沿用旧结论。
 * 走真实的 Spring 容器和内存数据库，通过控制器修改 JD。
 */
@SpringBootTest(properties = { "jobexplorer.demo-data=false", "spring.ai.model.chat=none",
		"spring.datasource.url=jdbc:h2:mem:jdchange;DB_CLOSE_DELAY=-1" })
class JdChangeIntegrationTest {

	private static final String OLD_JD = "任职要求：硕士研究生及以上学历。";

	@Autowired
	CompanyRepository companies;

	@Autowired
	PositionRepository positions;

	@Autowired
	RequirementRepository requirements;

	@Autowired
	RequirementService requirementService;

	@Autowired
	PositionController positionController;

	@Autowired
	AssessmentService assessments;

	/** 建一个已核查、条件已人工确认的岗位。演示档案是硕士，所以此时应判为符合。 */
	private Requirement confirmedPosition() {
		Company c = companies.save(new Company("测试公司"));
		Position p = new Position(c, "数据岗");
		p.setJdText(OLD_JD);
		p.setCheckedAt(Instant.now());
		positions.save(p);
		Requirement r = new Requirement(c, RequirementType.DEGREE, RequirementOrigin.LLM, "硕士及以上");
		r.setPosition(p);
		r.setLevel("硕士");
		r.setQuote("硕士研究生及以上学历");
		r = requirementService.verifyAndSave(r);
		requirementService.confirm(r.getId(), false);
		assertThat(assessments.assessPosition(p.getId()).overall()).isEqualTo(Verdict.PASS);
		return r;
	}

	private PositionRequest withJd(String jd) {
		return new PositionRequest("数据岗", null, null, null, jd, null, null, null);
	}

	@Test
	void 修改JD后_旧确认和核查失效_不再显示符合() {
		Requirement r = confirmedPosition();
		Long positionId = r.getPosition().getId();

		positionController.update(positionId, withJd("任职要求：本科及以上学历，通过大学英语六级。"));

		Position p = positions.findById(positionId).orElseThrow();
		Requirement after = requirements.findById(r.getId()).orElseThrow();
		assertThat(p.getCheckedAt()).as("岗位回到未核查").isNull();
		assertThat(after.isConfirmed()).as("模型抽取的条件退回待确认").isFalse();
		assertThat(after.isQuoteVerified()).as("旧引用在新 JD 中找不到").isFalse();
		assertThat(assessments.assessPosition(positionId).overall()).isNotEqualTo(Verdict.PASS);
	}

	@Test
	void 保存时JD没变_不影响已有确认() {
		Requirement r = confirmedPosition();
		Long positionId = r.getPosition().getId();

		positionController.update(positionId, withJd(OLD_JD));

		assertThat(positions.findById(positionId).orElseThrow().getCheckedAt()).isNotNull();
		assertThat(requirements.findById(r.getId()).orElseThrow().isConfirmed()).isTrue();
		assertThat(assessments.assessPosition(positionId).overall()).isEqualTo(Verdict.PASS);
	}
}
