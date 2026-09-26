package io.github.jobexplorer.eval;

import io.github.jobexplorer.domain.Enums.Verdict;

/** 一个分组（dev / test / sample）的累计指标。 */
final class Tally {

	int gold;

	int correct;

	int wrongParams;

	int missed;

	int spurious;

	long extracted;

	long quoteVerified;

	long parseFlagged;

	int extractionFailures;

	int baselineFailures;

	int judgements;

	int sysExcluded;

	int sysIncluded;

	int sysDeterminate;

	int baseJudgements;

	int baseExcluded;

	int baseIncluded;

	int baseDeterminate;

	int sysOverconfident;

	int baseOverconfident;

	int humanLabeled;

	void add(RequirementMatcher.Result m) {
		gold += m.gold();
		correct += m.correct();
		wrongParams += m.wrongParams();
		missed += m.missed();
		spurious += m.spurious();
	}

	void addVerdicts(Verdict goldV, boolean human, Verdict sysV, Verdict baseV) {
		judgements++;
		humanLabeled += human ? 1 : 0;
		sysOverconfident += overconfident(goldV, sysV) ? 1 : 0;
		sysExcluded += wronglyExcluded(goldV, sysV) ? 1 : 0;
		sysIncluded += wronglyIncluded(goldV, sysV) ? 1 : 0;
		sysDeterminate += determinate(sysV) ? 1 : 0;
		if (baseV != null) {
			baseJudgements++;
			baseExcluded += wronglyExcluded(goldV, baseV) ? 1 : 0;
			baseIncluded += wronglyIncluded(goldV, baseV) ? 1 : 0;
			baseDeterminate += determinate(baseV) ? 1 : 0;
			baseOverconfident += overconfident(goldV, baseV) ? 1 : 0;
		}
	}

	/** 本来可能能报（符合或待核实），却被判不符合。 */
	static boolean wronglyExcluded(Verdict gold, Verdict got) {
		return got == Verdict.FAIL && gold != Verdict.FAIL;
	}

	static boolean wronglyIncluded(Verdict gold, Verdict got) {
		return got == Verdict.PASS && gold == Verdict.FAIL;
	}

	/** 本该待核实，却给了确定结论。 */
	static boolean overconfident(Verdict gold, Verdict got) {
		return gold == Verdict.UNKNOWN && determinate(got);
	}

	private static boolean determinate(Verdict v) {
		return v == Verdict.PASS || v == Verdict.FAIL;
	}

	String row(String split) {
		return "| " + split + " | " + gold + " | " + correct + " | " + wrongParams + " | " + missed + " | " + spurious
				+ " | " + quoteVerified + "/" + extracted + " | " + judgements + "（人工 " + humanLabeled + "） | " + sysExcluded + " | "
				+ (baseJudgements == 0 ? "—" : baseExcluded) + " | " + sysIncluded + " | "
				+ (baseJudgements == 0 ? "—" : baseIncluded) + " | " + pct(sysDeterminate, judgements) + " | "
				+ (baseJudgements == 0 ? "—" : pct(baseDeterminate, baseJudgements)) + " | " + sysOverconfident + " | "
				+ (baseJudgements == 0 ? "—" : baseOverconfident) + " |\n";
	}

	String summaryLine() {
		return "条件 " + correct + "/" + gold + " 正确，遗漏 " + missed + "，多抽 " + spurious + "；判断 " + judgements
				+ " 次（人工判定 " + humanLabeled + "），错误排除 本系统 " + sysExcluded + " / 对照组 " + baseExcluded
				+ "，过度确定 本系统 " + sysOverconfident + " / 对照组 " + baseOverconfident;
	}

	private static String pct(int a, int b) {
		return b == 0 ? "—" : Math.round(100.0 * a / b) + "%";
	}
}
