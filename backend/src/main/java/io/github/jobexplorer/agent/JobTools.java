package io.github.jobexplorer.agent;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import io.github.jobexplorer.domain.Company;
import io.github.jobexplorer.domain.Enums.Verdict;
import io.github.jobexplorer.domain.Position;
import io.github.jobexplorer.repository.CompanyRepository;
import io.github.jobexplorer.repository.PositionRepository;
import io.github.jobexplorer.service.AssessmentService;
import io.github.jobexplorer.service.AssessmentService.Assessment;
import io.github.jobexplorer.service.CheckResult;

/**
 * 提供给大模型调用的工具。全部只读：模型只能查询和读取判断结果，不能修改任何数据。
 * 「能不能报」由 AssessmentService 用规则算出，模型只负责决定查什么、怎么组织回答。
 * 每次调用都记入 trace，前端会展示出来，方便核对模型的回答有没有依据。
 * 每次提问新建一个实例，trace 不会在请求之间串用。
 */
public class JobTools {

	private final CompanyRepository companies;

	private final PositionRepository positions;

	private final AssessmentService assessments;

	private final List<ToolCall> trace = new ArrayList<>();

	JobTools(CompanyRepository companies, PositionRepository positions, AssessmentService assessments) {
		this.companies = companies;
		this.positions = positions;
		this.assessments = assessments;
	}

	List<ToolCall> trace() {
		return trace;
	}

	@Tool(description = "列出已录入的全部公司，以及每家公司按公告条件判断的整体结论和岗位数量。回答任何问题前一般先调用它。")
	public List<CompanySummary> listCompanies() {
		List<CompanySummary> result = companies.findAll().stream().map(c -> {
			Assessment a = assessments.assessCompany(c.getId());
			int count = positions.findByCompanyIdOrderByCreatedAtAsc(c.getId()).size();
			return new CompanySummary(c.getId(), c.getName(), label(a.overall()), a.pending(), count);
		}).toList();
		trace.add(new ToolCall("listCompanies", "", result.size() + " 家公司"));
		return result;
	}

	@Tool(description = "查看某家公司的公司级报名条件，每条包含判断结论、理由和原文引用。")
	public ConditionReport getCompanyConditions(@ToolParam(description = "公司 id") long companyId) {
		Company c = companies.findById(companyId).orElse(null);
		if (c == null) {
			trace.add(new ToolCall("getCompanyConditions", "companyId=" + companyId, "公司不存在"));
			return null;
		}
		ConditionReport report = ConditionReport.of(c.getName(), assessments.assessCompany(companyId));
		trace.add(new ToolCall("getCompanyConditions", "companyId=" + companyId,
				c.getName() + "：" + report.overall() + "，" + report.conditions().size() + " 条条件"));
		return report;
	}

	@Tool(description = "列出某家公司下已录入的岗位，以及每个岗位的整体结论（含公司级和岗位级条件）。")
	public List<PositionSummary> listPositions(@ToolParam(description = "公司 id") long companyId) {
		List<PositionSummary> result = positions.findByCompanyIdOrderByCreatedAtAsc(companyId)
			.stream()
			.map(p -> PositionSummary.of(p, label(assessments.assessPosition(p.getId()).overall())))
			.toList();
		trace.add(new ToolCall("listPositions", "companyId=" + companyId, result.size() + " 个岗位"));
		return result;
	}

	@Tool(description = "查看某个岗位的全部报名条件（公司级 + 岗位级），每条包含判断结论、理由和原文引用。")
	public ConditionReport getPositionConditions(@ToolParam(description = "岗位 id") long positionId) {
		Position p = positions.findById(positionId).orElse(null);
		if (p == null) {
			trace.add(new ToolCall("getPositionConditions", "positionId=" + positionId, "岗位不存在"));
			return null;
		}
		ConditionReport report = ConditionReport.of(p.getCompany().getName() + " · " + p.getTitle(),
				assessments.assessPosition(positionId));
		trace.add(new ToolCall("getPositionConditions", "positionId=" + positionId,
				p.getTitle() + "：" + report.overall() + "，" + report.conditions().size() + " 条条件"));
		return report;
	}

	static String label(Verdict v) {
		return switch (v) {
			case PASS -> "符合";
			case FAIL -> "不符合";
			case UNKNOWN -> "待核实";
			case PARTIAL -> "已核查部分通过（还有来源未核查）";
			case NOT_APPLICABLE -> "不适用";
		};
	}

	public record ToolCall(String tool, String arguments, String result) {
	}

	public record CompanySummary(long id, String name, String overall, List<String> pendingSources,
			int positionCount) {
	}

	public record PositionSummary(long id, String title, String unit, String location, String deadline,
			String status, String overall) {

		static PositionSummary of(Position p, String overall) {
			return new PositionSummary(p.getId(), p.getTitle(), p.getUnit(), p.getLocation(),
					p.getDeadline() == null ? null : p.getDeadline().toString(), p.getStatus().name(), overall);
		}
	}

	public record Condition(String type, String description, String verdict, String reason, String appliesTo,
			String alternativeGroup, boolean confirmed, String quote, boolean quoteFoundInSource) {

		static Condition of(CheckResult r) {
			return new Condition(r.type().name(), r.description(), label(r.verdict()), r.reason(), r.appliesTo(),
					r.alternativeGroup(), r.confirmed(), r.quote(), r.quoteVerified());
		}
	}

	public record ConditionReport(String target, String overall, List<String> pendingSources,
			List<Condition> conditions) {

		static ConditionReport of(String target, Assessment a) {
			return new ConditionReport(target, label(a.overall()), a.pending(),
					a.results().stream().map(Condition::of).toList());
		}
	}
}
