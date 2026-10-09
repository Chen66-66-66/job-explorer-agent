package io.github.jobexplorer.domain;

/** 领域枚举集中放在这里，便于一眼看全。 */
public final class Enums {

	private Enums() {
	}

	/** 公司在探索流程中的状态。 */
	public enum CompanyStatus {
		CANDIDATE, INTERESTED, EXCLUDED
	}

	/** 岗位的投递状态。 */
	public enum PositionStatus {
		FOUND, APPLYING, APPLIED, DROPPED
	}

	/** 报名条件的类型，决定用哪条规则判断。 */
	public enum RequirementType {
		DEGREE, MAJOR, ENGLISH, GRADUATION_WINDOW, GRADUATION_COHORT, AGE, OVERSEAS_CERT, DEADLINE, OTHER
	}

	/** 条件从哪里来：模型抽取、人工录入、演示数据。 */
	public enum RequirementOrigin {
		LLM, MANUAL, DEMO
	}

	/**
	 * 一条条件为什么是「待核实」，决定下一步该谁做什么：
	 * MISSING_PROFILE 档案缺信息，本人补一次就能自动判断；
	 * CONFIRM_EXTRACTION 模型抽取的条件（或其适用范围）还没人工确认；
	 * NEEDS_JUDGEMENT 口径问题，程序判断不了，需要本人拍板（如专业是否相关）。
	 */
	public enum PendingKind {
		MISSING_PROFILE, CONFIRM_EXTRACTION, NEEDS_JUDGEMENT
	}

	/**
	 * 岗位的主要状态（一个岗位同一时间只有一个）：
	 * TO_CHECK 待核查：缺个人信息、缺招聘材料或材料范围未查清，还没有足够依据；
	 * PRELIM_MATCH 初筛匹配：材料范围已明确，已知硬条件没有不符合，可能还有需要本人拍板的口径问题；
	 * PROPOSED_EXCLUSION 拟排除·待确认：有条件按规则会导致不符合，但抽取还没人工确认，不提前隐藏；
	 * EXCLUDED 已排除：已确认的硬条件不符合；
	 * VERIFIED 已核实可报：所有适用条件都已确认满足、没有待办，并完成了投递前核对。
	 */
	public enum ReviewStatus {
		TO_CHECK, PRELIM_MATCH, PROPOSED_EXCLUSION, EXCLUDED, VERIFIED
	}

	/** 待办原因：一个岗位可以同时有多项。 */
	public enum TodoKind {
		MISSING_PROFILE, MISSING_MATERIAL, MATERIAL_SCOPE, NEEDS_JUDGEMENT, CONFIRM_EXCLUSION
	}

	/**
	 * 岗位材料范围的人工确认结果：
	 * PAGE_COMPLETE 岗位页面即完整要求，没有其他适用材料；
	 * REFERENCED_NO_EXTRA 岗位原文引用的材料已看过，没有与本岗位相关的额外条件；
	 * REFERENCED_EXTRA_ADDED 引用材料中有额外条件，已补录。
	 * 「看过但仍无法判断」不记录，待办保留。
	 */
	public enum MaterialResolution {
		PAGE_COMPLETE, REFERENCED_NO_EXTRA, REFERENCED_EXTRA_ADDED
	}

	/**
	 * 判断结论。
	 * PARTIAL 只用于整体：已核查的条件都通过，但还有来源没核查完，不能说「符合」。
	 * NOT_APPLICABLE 只用于单条：条件的适用对象不是本人（如只针对本科生的年龄要求）。
	 */
	public enum Verdict {
		PASS, FAIL, UNKNOWN, PARTIAL, NOT_APPLICABLE
	}

	/**
	 * 驳回模型抽取结果的原因：
	 * NOT_IN_SOURCE 原文没有这个要求；NOT_A_REQUIREMENT 不是报名硬性条件（如优先条件、岗位职责）；
	 * WRONG_VALUE 数值或参数读错；WRONG_TYPE 条件类型错；WRONG_SCOPE 适用对象或「满足其一」分组错；
	 * DUPLICATE 与另一条重复；OTHER 其他（须写说明）。
	 */
	public enum RejectionReason {
		NOT_IN_SOURCE, NOT_A_REQUIREMENT, WRONG_VALUE, WRONG_TYPE, WRONG_SCOPE, DUPLICATE, OTHER
	}
}
