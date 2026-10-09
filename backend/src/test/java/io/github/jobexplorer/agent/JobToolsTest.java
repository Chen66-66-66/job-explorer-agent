package io.github.jobexplorer.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.jobexplorer.agent.JobTools.PositionSummary;
import io.github.jobexplorer.agent.JobTools.PositionTodo;
import io.github.jobexplorer.domain.Company;
import io.github.jobexplorer.domain.Enums.RequirementOrigin;
import io.github.jobexplorer.domain.Enums.RequirementType;
import io.github.jobexplorer.domain.Position;
import io.github.jobexplorer.domain.Requirement;
import io.github.jobexplorer.repository.CompanyRepository;
import io.github.jobexplorer.repository.PositionRepository;
import io.github.jobexplorer.service.AssessmentService;
import io.github.jobexplorer.service.RequirementService;
import io.github.jobexplorer.service.ReviewService;

/** Agent 工具返回的是规则汇总出的主要状态和待办，不需要模型即可测试。 */
@SpringBootTest(properties = { "jobexplorer.demo-data=false", "spring.ai.model.chat=none",
		"spring.datasource.url=jdbc:h2:mem:jobtools;DB_CLOSE_DELAY=-1" })
class JobToolsTest {

	@Autowired
	CompanyRepository companies;

	@Autowired
	PositionRepository positions;

	@Autowired
	RequirementService requirementService;

	@Autowired
	AssessmentService assessments;

	@Autowired
	ReviewService reviews;

	/** 生产中工具在 AgentService.ask 的只读事务里被调用，这里同样放在事务中。 */
	@Test
	@Transactional
	void 岗位工具返回主要状态和待办_并记录调用() {
		Company c = companies.save(new Company("测试公司"));
		Position p = new Position(c, "数据岗");
		p.setJdText("任职要求：硕士研究生及以上学历。其他要求详见招聘公告。");
		p.setCheckedAt(Instant.now());
		positions.save(p);
		Requirement r = new Requirement(c, RequirementType.DEGREE, RequirementOrigin.MANUAL, "硕士及以上");
		r.setPosition(p);
		r.setLevel("硕士");
		r.setQuote("硕士研究生及以上学历");
		requirementService.verifyAndSave(r);

		JobTools tools = new JobTools(companies, positions, assessments, reviews);
		PositionSummary summary = tools.listPositions(c.getId()).get(0);
		assertThat(summary.status()).isEqualTo("待核查");
		assertThat(summary.todoCount()).isEqualTo(1);
		assertThat(summary.applicationStatus()).isEqualTo("已找到");

		PositionTodo todo = tools.getPositionTodos(p.getId());
		assertThat(todo.todos()).singleElement().satisfies(t -> {
			assertThat(t.kind()).isEqualTo("缺招聘材料");
			assertThat(t.text()).contains("详见招聘公告");
		});
		assertThat(todo.verifiedConditions()).containsExactly("硕士及以上");

		assertThat(tools.getPositionTodos(-1)).isNull();
		assertThat(tools.trace()).extracting(JobTools.ToolCall::tool)
			.containsExactly("listPositions", "getPositionTodos", "getPositionTodos");
	}
}
