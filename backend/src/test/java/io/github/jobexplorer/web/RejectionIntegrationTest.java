package io.github.jobexplorer.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import io.github.jobexplorer.domain.Company;
import io.github.jobexplorer.domain.Enums.RejectionReason;
import io.github.jobexplorer.domain.Enums.RequirementOrigin;
import io.github.jobexplorer.domain.Enums.RequirementType;
import io.github.jobexplorer.domain.Position;
import io.github.jobexplorer.domain.Requirement;
import io.github.jobexplorer.repository.CompanyRepository;
import io.github.jobexplorer.repository.ExtractionRejectionRepository;
import io.github.jobexplorer.repository.PositionRepository;
import io.github.jobexplorer.repository.RequirementRepository;
import io.github.jobexplorer.service.ExtractedRequirement;
import io.github.jobexplorer.service.LlmRequirementExtractor;
import io.github.jobexplorer.service.RequirementService;
import io.github.jobexplorer.service.RequirementService.ExtractionResult;
import io.github.jobexplorer.web.Dtos.PositionRequest;

/**
 * 驳回记录：驳回后重新抽取不再出现同一个错误；原文改动后不再生效；驳回错了可以恢复。
 * 模型用替身代替，每次「抽取」返回设定好的结果，相当于模型重复犯同一个错。
 */
@SpringBootTest(properties = { "jobexplorer.demo-data=false", "spring.ai.model.chat=none",
		"spring.datasource.url=jdbc:h2:mem:rejection;DB_CLOSE_DELAY=-1" })
class RejectionIntegrationTest {

	private static final String JD = "任职要求：硕士研究生及以上学历，通过大学英语六级。优先考虑有实习经历者。";

	private static final ExtractedRequirement DEGREE = new ExtractedRequirement("DEGREE", "硕士及以上", "硕士研究生及以上学历",
			null, null, "硕士", null, null, null, null, null, null, null, null);

	/** 模型把「优先」条件抽成了硬性条件。 */
	private static final ExtractedRequirement PREFERRED = new ExtractedRequirement("OTHER", "须有实习经历",
			"优先考虑有实习经历者", null, null, null, null, null, null, null, null, null, null, null);

	/** 抽取器替身：不调用模型，返回设定好的结果。 */
	static class StubExtractor extends LlmRequirementExtractor {

		List<ExtractedRequirement> next = List.of();

		StubExtractor(ObjectProvider<ChatModel> chatModel) {
			super(chatModel);
		}

		@Override
		public List<ExtractedRequirement> extract(String sourceText) {
			return next;
		}
	}

	@TestConfiguration
	static class StubConfig {

		@Bean
		@Primary
		StubExtractor stubExtractor(ObjectProvider<ChatModel> chatModel) {
			return new StubExtractor(chatModel);
		}
	}

	@Autowired
	StubExtractor extractor;

	@Autowired
	CompanyRepository companies;

	@Autowired
	PositionRepository positions;

	@Autowired
	RequirementRepository requirements;

	@Autowired
	ExtractionRejectionRepository rejections;

	@Autowired
	RequirementService requirementService;

	@Autowired
	PositionController positionController;

	@Autowired
	RejectionController rejectionController;

	private Long position() {
		Company c = companies.save(new Company("测试公司"));
		Position p = new Position(c, "数据岗");
		p.setJdText(JD);
		return positions.save(p).getId();
	}

	private Long idOf(ExtractionResult result, String description) {
		return result.saved().stream().filter(r -> r.getDescription().equals(description)).findFirst().orElseThrow().getId();
	}

	@Test
	void 驳回后重新抽取_同一个错误抽取被跳过() {
		extractor.next = List.of(DEGREE, PREFERRED);
		Long id = position();
		ExtractionResult first = requirementService.extractFromPosition(id);
		requirementService.reject(idOf(first, "须有实习经历"), RejectionReason.NOT_A_REQUIREMENT, "原文是优先条件");

		ExtractionResult second = requirementService.extractFromPosition(id);
		assertThat(second.saved()).extracting(Requirement::getDescription).containsExactly("硕士及以上");
		assertThat(second.skipped()).singleElement().satisfies(r -> {
			assertThat(r.getReason()).isEqualTo(RejectionReason.NOT_A_REQUIREMENT);
			assertThat(r.getNote()).isEqualTo("原文是优先条件");
		});
		assertThat(rejectionController.positionRejections(id)).singleElement()
			.satisfies(v -> assertThat(v.active()).isTrue());
	}

	@Test
	void 重复的两条驳回一条后_重新抽取仍保留另一条() {
		extractor.next = List.of(DEGREE, DEGREE);
		Long id = position();
		ExtractionResult first = requirementService.extractFromPosition(id);
		requirementService.reject(first.saved().get(1).getId(), RejectionReason.DUPLICATE, null);

		ExtractionResult second = requirementService.extractFromPosition(id);
		assertThat(second.saved()).hasSize(1);
		assertThat(second.skipped()).hasSize(1);
	}

	@Test
	void 原文改动后_驳回记录不再生效() {
		extractor.next = List.of(DEGREE, PREFERRED);
		Long id = position();
		ExtractionResult first = requirementService.extractFromPosition(id);
		requirementService.reject(idOf(first, "须有实习经历"), RejectionReason.NOT_A_REQUIREMENT, null);

		positionController.update(id, new PositionRequest("数据岗", null, null, null, JD + "须有实习经历。", null, null, null));
		assertThat(rejectionController.positionRejections(id)).singleElement()
			.satisfies(v -> assertThat(v.active()).isFalse());
		assertThat(requirementService.extractFromPosition(id).saved()).hasSize(2);
	}

	@Test
	void 驳回错了可以恢复_恢复为待确认_不重复创建() {
		extractor.next = List.of(DEGREE);
		Long id = position();
		Long reqId = requirementService.extractFromPosition(id).saved().get(0).getId();
		requirementService.confirm(reqId, false);
		Long rejectionId = requirementService.reject(reqId, RejectionReason.WRONG_VALUE, null).getId();

		Requirement restored = requirementService.restore(rejectionId);
		assertThat(restored.getDescription()).isEqualTo("硕士及以上");
		assertThat(restored.getLevel()).isEqualTo("硕士");
		assertThat(restored.isConfirmed()).as("恢复后需重新确认").isFalse();
		assertThat(restored.isQuoteVerified()).isTrue();
		assertThat(rejections.findById(rejectionId)).isEmpty();

		// 已有完全相同的抽取时，恢复不再新建
		Long again = requirementService.reject(restored.getId(), RejectionReason.WRONG_VALUE, null).getId();
		requirementService.extractFromPosition(id); // 跳过，不新建
		positionController.update(id, new PositionRequest("数据岗", null, null, null, JD + " ", null, null, null));
		requirementService.extractFromPosition(id); // 原文改了，重新抽出
		requirementService.restore(again);
		assertThat(requirements.findByPositionIdOrderByIdAsc(id)).hasSize(1);
	}

	@Test
	void 人工条件不能驳回_模型条件不能直接删除_其他原因须写说明() {
		extractor.next = List.of(DEGREE);
		Long id = position();
		Long llmId = requirementService.extractFromPosition(id).saved().get(0).getId();
		Requirement manual = new Requirement(positions.findById(id).orElseThrow().getCompany(), RequirementType.DEGREE,
				RequirementOrigin.MANUAL, "人工录入");
		Long manualId = requirementService.verifyAndSave(manual).getId();

		assertThatThrownBy(() -> requirementService.reject(manualId, RejectionReason.OTHER, "x"))
			.hasMessageContaining("直接删除");
		assertThatThrownBy(() -> requirementService.delete(llmId)).hasMessageContaining("驳回");
		assertThatThrownBy(() -> requirementService.reject(llmId, RejectionReason.OTHER, " "))
			.hasMessageContaining("写明原因");
		assertThatThrownBy(() -> requirementService.reject(llmId, null, null)).hasMessageContaining("驳回原因");
		requirementService.delete(manualId);
		assertThat(requirements.findById(manualId)).isEmpty();
	}
}
