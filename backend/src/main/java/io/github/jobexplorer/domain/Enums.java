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
	 * 判断结论。
	 * PARTIAL 只用于整体：已核查的条件都通过，但还有来源没核查完，不能说「符合」。
	 * NOT_APPLICABLE 只用于单条：条件的适用对象不是本人（如只针对本科生的年龄要求）。
	 */
	public enum Verdict {
		PASS, FAIL, UNKNOWN, PARTIAL, NOT_APPLICABLE
	}
}
