package io.github.jobexplorer.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.jobexplorer.domain.Enums.MaterialResolution;
import io.github.jobexplorer.domain.Enums.RequirementOrigin;
import io.github.jobexplorer.domain.Enums.RequirementType;
import io.github.jobexplorer.domain.Position;
import io.github.jobexplorer.domain.Requirement;
import io.github.jobexplorer.domain.Company;
import io.github.jobexplorer.repository.CompanyRepository;
import io.github.jobexplorer.repository.PositionRepository;
import io.github.jobexplorer.repository.RequirementRepository;
import io.github.jobexplorer.service.RequirementService;
import io.github.jobexplorer.service.ReviewService;
import io.github.jobexplorer.service.SampleBundle;
import io.github.jobexplorer.service.SampleBundle.AnnouncementPart;
import io.github.jobexplorer.service.SampleBundle.CompanyPart;
import io.github.jobexplorer.service.SampleBundle.PositionPart;
import io.github.jobexplorer.service.SampleImportService;
import io.github.jobexplorer.service.SampleImportService.ImportOutcome;
import io.github.jobexplorer.service.SampleImportService.ImportReport;
import io.github.jobexplorer.service.SampleImportService.ItemResult;
import tools.jackson.databind.json.JsonMapper;

/** 样本导入：用仓库里的虚构样本走真实的 Spring 容器和内存数据库。每个测试结束后回滚，互不影响。 */
@SpringBootTest(properties = { "jobexplorer.demo-data=false", "spring.ai.model.chat=none",
		"spring.datasource.url=jdbc:h2:mem:sampleimport;DB_CLOSE_DELAY=-1" })
@Transactional
class SampleImportIntegrationTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	SampleImportService importService;

	@Autowired
	CompanyRepository companies;

	@Autowired
	PositionRepository positions;

	@Autowired
	RequirementRepository requirements;

	@Autowired
	RequirementService requirementService;

	@Autowired
	ReviewService reviews;

	/** 仓库根目录 samples/ 下的样本（测试在 backend/ 目录运行）。 */
	private static SampleBundle sample(String file) throws Exception {
		return JSON.readValue(Files.readString(Path.of("..", "samples", file)), SampleBundle.class);
	}

	private static ItemResult position(ImportReport report, String title) {
		return report.positions().stream().filter(p -> p.title().equals(title)).findFirst().orElseThrow();
	}

	private static SampleBundle bundle(String company, List<AnnouncementPart> anns, List<PositionPart> pos) {
		return new SampleBundle(new CompanyPart(company, null, null, null), anns, pos);
	}

	@Test
	void 导入演示样本_报告列出缺失项() throws Exception {
		ImportReport report = importService.importBundle(sample("demo-import.json"));

		assertThat(report.companyCreated()).isTrue();
		assertThat(report.announcements()).singleElement()
			.satisfies(a -> assertThat(a.outcome()).isEqualTo(ImportOutcome.CREATED));
		assertThat(report.positions()).hasSize(4).allMatch(p -> p.outcome() == ImportOutcome.CREATED);
		assertThat(report.warnings()).isEmpty();

		assertThat(position(report, "信息化运维工程师").notes()).anyMatch(n -> n.contains("详见招聘公告"))
			.anyMatch(n -> n.contains("没有截止日期"));
		assertThat(position(report, "数字化项目管理岗").notes()).anyMatch(n -> n.contains("没有 JD 原文"))
			.anyMatch(n -> n.contains("没有来源链接"));

		Position p = positions.findById(position(report, "数据开发工程师").id()).orElseThrow();
		assertThat(p.getCheckedAt()).as("导入不等于核查").isNull();
		assertThat(p.getJdText()).contains("硕士研究生及以上学历");
	}

	@Test
	void 同一份样本重复导入_不产生重复数据() throws Exception {
		SampleBundle b = sample("demo-import.json");
		b = bundle("重复导入测试公司", b.announcements(), b.positions());
		importService.importBundle(b);
		ImportReport again = importService.importBundle(b);

		assertThat(again.companyCreated()).isFalse();
		assertThat(again.announcements()).allMatch(a -> a.outcome() == ImportOutcome.UNCHANGED);
		assertThat(again.positions()).allMatch(p -> p.outcome() == ImportOutcome.UNCHANGED);
		assertThat(positions.findByCompanyIdOrderByCreatedAtAsc(again.companyId())).hasSize(4);
	}

	@Test
	void 岗位JD有变化_更新并让原有核查失效() throws Exception {
		ImportReport first = importService.importBundle(sample("demo-import.json"));
		Long positionId = position(first, "数据开发工程师").id();
		Position p = positions.findById(positionId).orElseThrow();
		Requirement r = new Requirement(p.getCompany(), RequirementType.DEGREE, RequirementOrigin.LLM, "硕士及以上");
		r.setPosition(p);
		r.setLevel("硕士");
		r.setQuote("硕士研究生及以上学历");
		requirementService.confirm(requirementService.verifyAndSave(r).getId(), false);
		requirementService.markPositionChecked(positionId);
		reviews.resolveMaterial(positionId, MaterialResolution.PAGE_COMPLETE, "官网详情页");

		ImportReport second = importService.importBundle(sample("demo-import-v2.json"));
		ItemResult updated = position(second, "数据开发工程师");
		assertThat(updated.outcome()).isEqualTo(ImportOutcome.UPDATED);
		assertThat(updated.notes()).anyMatch(n -> n.contains("截止日期：2026-10-30 → 2026-11-06"))
			.anyMatch(n -> n.contains("JD 原文有变化"));

		Position after = positions.findById(positionId).orElseThrow();
		assertThat(after.getJdText()).contains("大学英语六级");
		assertThat(after.getCheckedAt()).isNull();
		assertThat(after.getMaterialResolution()).isNull();
		assertThat(requirements.findById(r.getId()).orElseThrow().isConfirmed()).isFalse();
		// v2 没有写其他岗位和公告，已有数据保持不变
		assertThat(positions.findByCompanyIdOrderByCreatedAtAsc(first.companyId())).hasSize(4);
	}

	@Test
	void 同名公告原文不同_不覆盖() {
		AnnouncementPart v1 = new AnnouncementPart("2027 校招公告", null, "2026-10-12", "本科及以上学历。");
		AnnouncementPart v2 = new AnnouncementPart("2027 校招公告", null, "2026-10-13", "硕士及以上学历。");
		importService.importBundle(bundle("公告测试公司", List.of(v1), List.of()));
		ImportReport report = importService.importBundle(bundle("公告测试公司", List.of(v2), List.of()));
		assertThat(report.announcements()).singleElement().satisfies(a -> {
			assertThat(a.outcome()).isEqualTo(ImportOutcome.SKIPPED);
			assertThat(a.notes()).anyMatch(n -> n.contains("未覆盖"));
		});
	}

	@Test
	void 日期无法解析_样本内重复_缺必填项() {
		PositionPart badDate = new PositionPart("岗位甲", null, "郑州", "2026年10月底", null, "2026-10-12", "JD");
		PositionPart duplicate = new PositionPart("岗位甲", null, "郑州", null, null, null, "JD");
		PositionPart noTitle = new PositionPart(" ", null, null, null, null, null, null);
		ImportReport report = importService.importBundle(bundle("校验测试公司", null, List.of(badDate, duplicate, noTitle)));

		assertThat(report.positions().get(0).notes()).anyMatch(n -> n.contains("无法解析"))
			.anyMatch(n -> n.contains("没有截止日期"));
		assertThat(report.positions().get(1).outcome()).isEqualTo(ImportOutcome.SKIPPED);
		assertThat(report.positions().get(2).outcome()).isEqualTo(ImportOutcome.SKIPPED);
		assertThat(report.warnings()).anyMatch(w -> w.contains("没有公告"));

		assertThatThrownBy(() -> importService.importBundle(bundle(" ", null, null))).hasMessageContaining("公司名称");
	}

	@Test
	void 已有公司只补空字段_不覆盖已填写的内容() {
		importService.importBundle(new SampleBundle(new CompanyPart("字段测试公司", "原集团", null, null), null, null));
		ImportReport report = importService
			.importBundle(new SampleBundle(new CompanyPart("字段测试公司", "新集团", null, "北森"), null, null));
		assertThat(report.companyCreated()).isFalse();
		Company c = companies.findById(report.companyId()).orElseThrow();
		assertThat(c.getGroupName()).as("已填写的不覆盖").isEqualTo("原集团");
		assertThat(c.getPlatform()).as("空字段补上").isEqualTo("北森");
	}
}
