package io.github.jobexplorer.agent;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import io.github.jobexplorer.domain.Company;
import io.github.jobexplorer.domain.Enums.PendingKind;
import io.github.jobexplorer.domain.Enums.PositionStatus;
import io.github.jobexplorer.domain.Enums.ReviewStatus;
import io.github.jobexplorer.domain.Enums.TodoKind;
import io.github.jobexplorer.domain.Enums.Verdict;
import io.github.jobexplorer.domain.Position;
import io.github.jobexplorer.repository.CompanyRepository;
import io.github.jobexplorer.repository.PositionRepository;
import io.github.jobexplorer.service.AssessmentService;
import io.github.jobexplorer.service.AssessmentService.Assessment;
import io.github.jobexplorer.service.CheckResult;
import io.github.jobexplorer.service.PositionReviewer.PositionReview;
import io.github.jobexplorer.service.ReviewService;

/**
 * 提供给大模型调用的工具。全部只读：模型只能查询和读取判断结果，不能修改任何数据。
 * 「能不能报」由 AssessmentService 用规则算出，岗位的主要状态和待办由 ReviewService 汇总，
 * 模型只负责决定查什么、怎么组织回答。
 * 每次调用都记入 trace，前端会展示出来，方便核对模型的回答有没有依据。
 * 每次提问新建一个实例，trace 不会在请求之间串用。
 */
public class JobTools {

	private final CompanyRepository companies;

	private final PositionRepository positions;

	private final AssessmentService assessments;

	private final ReviewService reviews;

	private final List<ToolCall> trace = new ArrayList<>();

	JobTools(CompanyRepository companies, PositionRepository positions, AssessmentService assessments,
			ReviewService reviews) {
		this.companies = companies;
		this.positions = positions;
		this.assessments = assessments;
		this.reviews = reviews;
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

	@Tool(description = "列出某家公司下已录入的岗位，以及每个岗位的主要状态、状态原因、待办数和距截止天数。")
	public List<PositionSummary> listPositions(@ToolParam(description = "公司 id") long companyId) {
		List<PositionSummary> result = positions.findByCompanyIdOrderByCreatedAtAsc(companyId)
			.stream()
			.map(p -> PositionSummary.of(p, reviews.review(p.getId())))
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

	@Tool(description = "查看某个岗位的主要状态、待办（还差什么）、已确认满足的条件，以及材料范围依据和投递前核对情况。")
	public PositionTodo getPositionTodos(@ToolParam(description = "岗位 id") long positionId) {
		Position p = positions.findById(positionId).orElse(null);
		if (p == null) {
			trace.add(new ToolCall("getPositionTodos", "positionId=" + positionId, "岗位不存在"));
			return null;
		}
		PositionTodo result = PositionTodo.of(p.getCompany().getName() + " · " + p.getTitle(), reviews.review(positionId));
		trace.add(new ToolCall("getPositionTodos", "positionId=" + positionId,
				p.getTitle() + "：" + result.status() + "，" + result.todos().size() + " 项待办"));
		return result;
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

	static String label(ReviewStatus s) {
		return switch (s) {
			case TO_CHECK -> "待核查";
			case PRELIM_MATCH -> "初筛匹配";
			case PROPOSED_EXCLUSION -> "拟排除·待确认";
			case EXCLUDED -> "已排除";
			case VERIFIED -> "已核实可报";
		};
	}

	static String label(TodoKind k) {
		return switch (k) {
			case MISSING_PROFILE -> "档案缺信息";
			case MISSING_MATERIAL -> "缺招聘材料";
			case MATERIAL_SCOPE -> "材料范围";
			case NEEDS_JUDGEMENT -> "需本人判定";
			case CONFIRM_EXCLUSION -> "确认后会排除";
		};
	}

	static String label(PendingKind k) {
		return k == null ? null : switch (k) {
			case MISSING_PROFILE -> "档案缺信息";
			case CONFIRM_EXTRACTION -> "抽取结果尚未人工确认";
			case NEEDS_JUDGEMENT -> "需本人判定";
		};
	}

	static String label(PositionStatus s) {
		return switch (s) {
			case FOUND -> "已找到";
			case APPLYING -> "准备投";
			case APPLIED -> "已投递";
			case DROPPED -> "放弃";
		};
	}

	public record ToolCall(String tool, String arguments, String result) {
	}

	public record CompanySummary(long id, String name, String overall, List<String> pendingSources,
			int positionCount) {
	}

	/** status 是岗位的主要状态；applicationStatus 是本人记录的投递进度。 */
	public record PositionSummary(long id, String title, String unit, String location, String deadline,
			Long daysLeft, String applicationStatus, String status, String statusReason, int todoCount) {

		static PositionSummary of(Position p, PositionReview r) {
			return new PositionSummary(p.getId(), p.getTitle(), p.getUnit(), p.getLocation(),
					p.getDeadline() == null ? null : p.getDeadline().toString(), r.daysLeft(), label(p.getStatus()),
					label(r.status()), r.statusReason(), r.todos().size());
		}
	}

	public record Todo(String kind, String text) {
	}

	public record PositionTodo(String target, String status, String statusReason, List<Todo> todos,
			List<String> verifiedConditions, List<String> unverifiedConditions, String materialBasis,
			boolean readyForPreApplyCheck, String preApplyCheckedAt) {

		static PositionTodo of(String target, PositionReview r) {
			return new PositionTodo(target, label(r.status()), r.statusReason(),
					r.todos().stream().map(t -> new Todo(label(t.kind()), t.text())).toList(), r.verifiedConditions(),
					r.unverifiedPositives(), r.materialBasis(), r.readyForPreApplyCheck(),
					r.preApplyCheckedAt() == null ? null : r.preApplyCheckedAt().toString());
		}
	}

	public record Condition(String type, String description, String verdict, String pendingReason, String reason,
			String appliesTo, String alternativeGroup, boolean confirmed, boolean judgedByUser, String quote,
			boolean quoteFoundInSource) {

		static Condition of(CheckResult r) {
			return new Condition(r.type().name(), r.description(), label(r.verdict()), label(r.pending()), r.reason(),
					r.appliesTo(), r.alternativeGroup(), r.confirmed(), r.judged(), r.quote(), r.quoteVerified());
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
