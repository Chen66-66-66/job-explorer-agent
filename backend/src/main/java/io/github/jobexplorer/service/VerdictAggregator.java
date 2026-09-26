package io.github.jobexplorer.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.jobexplorer.domain.Enums.Verdict;

/**
 * 把逐条结果汇总成整体结论。
 * - 同一 alternativeGroup 的条件满足其一即可：任一符合则该组符合；全部不符合才算不符合；
 * - 不适用的条件不参与汇总；
 * - 任一不符合即整体不符合；有待核实则整体待核实；
 * - 条件都通过但还有来源没核查完，只能算「已核查部分通过」，不能算「符合」。
 */
public final class VerdictAggregator {

	private VerdictAggregator() {
	}

	public static Verdict overall(List<CheckResult> results, boolean coverageComplete) {
		List<Verdict> units = new ArrayList<>();
		Map<String, List<Verdict>> groups = new LinkedHashMap<>();
		for (CheckResult r : results) {
			if (r.verdict() == Verdict.NOT_APPLICABLE) {
				continue;
			}
			String g = r.alternativeGroup();
			if (g == null || g.isBlank()) {
				units.add(r.verdict());
			}
			else {
				groups.computeIfAbsent(g, k -> new ArrayList<>()).add(r.verdict());
			}
		}
		groups.values().forEach(vs -> units.add(groupVerdict(vs)));

		if (units.contains(Verdict.FAIL)) {
			return Verdict.FAIL;
		}
		if (units.isEmpty() || units.contains(Verdict.UNKNOWN)) {
			return Verdict.UNKNOWN;
		}
		return coverageComplete ? Verdict.PASS : Verdict.PARTIAL;
	}

	static Verdict groupVerdict(List<Verdict> vs) {
		if (vs.contains(Verdict.PASS)) {
			return Verdict.PASS;
		}
		if (vs.stream().allMatch(v -> v == Verdict.FAIL)) {
			return Verdict.FAIL;
		}
		return Verdict.UNKNOWN;
	}
}
