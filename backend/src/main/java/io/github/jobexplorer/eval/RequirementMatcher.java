package io.github.jobexplorer.eval;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.jobexplorer.domain.Requirement;

/**
 * 把模型抽取出的条件和标准答案逐条配对。
 * 同类型且适用对象一致的算「找到」；参数也一致算「正确」，否则算「参数错误」；
 * 标准答案有、抽取没有的算「遗漏」；抽取有、标准答案没有的算「多抽」。
 */
final class RequirementMatcher {

	private RequirementMatcher() {
	}

	record Result(int gold, int correct, int wrongParams, int missed, int spurious, List<String> problems) {
	}

	static Result match(List<Requirement> gold, List<Requirement> extracted) {
		List<Requirement> unused = new ArrayList<>(extracted);
		List<String> problems = new ArrayList<>();
		int correct = 0;
		int wrong = 0;
		int missed = 0;
		for (Requirement g : gold) {
			Requirement exact = unused.stream().filter(e -> sameSlot(g, e) && sameParams(g, e)).findFirst().orElse(null);
			if (exact != null) {
				unused.remove(exact);
				correct++;
				continue;
			}
			Requirement slot = unused.stream().filter(e -> sameSlot(g, e)).findFirst().orElse(null);
			if (slot != null) {
				unused.remove(slot);
				wrong++;
				problems.add("参数错误：" + describe(g) + " ≠ 抽取 " + describe(slot));
			}
			else {
				missed++;
				problems.add("遗漏：" + describe(g));
			}
		}
		for (Requirement e : unused) {
			problems.add("多抽：" + describe(e));
		}
		return new Result(gold.size(), correct, wrong, missed, unused.size(), problems);
	}

	private static boolean sameSlot(Requirement g, Requirement e) {
		return g.getType() == e.getType() && Objects.equals(appliesKey(g.getAppliesTo()), appliesKey(e.getAppliesTo()));
	}

	private static boolean sameParams(Requirement g, Requirement e) {
		boolean grouped = (g.getAlternativeGroup() != null) == (e.getAlternativeGroup() != null);
		boolean params = switch (g.getType()) {
			case DEGREE -> Objects.equals(g.getLevel(), e.getLevel());
			case MAJOR -> Objects.equals(listKey(g.getListValues()), listKey(e.getListValues()));
			case ENGLISH -> Objects.equals(certKey(g.getLevel()), certKey(e.getLevel()))
					&& Objects.equals(g.getMinScore(), e.getMinScore())
					&& g.isAllowEquivalent() == e.isAllowEquivalent();
			case GRADUATION_WINDOW -> Objects.equals(g.getMinDate(), e.getMinDate())
					&& Objects.equals(g.getMaxDate(), e.getMaxDate());
			case AGE -> Objects.equals(g.getMaxAge(), e.getMaxAge()) && g.isAgeStrict() == e.isAgeStrict()
					&& Objects.equals(g.getAgeReferenceDate(), e.getAgeReferenceDate());
			case DEADLINE -> Objects.equals(g.getMaxDate(), e.getMaxDate());
			case OVERSEAS_CERT, OTHER -> true;
		};
		return grouped && params;
	}

	/** 适用对象按含义归类，避免「硕士」和「硕士研究生」这类写法差异算成不一致。 */
	static String appliesKey(String appliesTo) {
		if (appliesTo == null || appliesTo.isBlank()) {
			return "";
		}
		for (String degree : new String[] { "博士", "硕士", "本科", "大专" }) {
			if (appliesTo.contains(degree)) {
				return degree;
			}
		}
		if (appliesTo.contains("境外") || appliesTo.contains("国外") || appliesTo.contains("海外") || appliesTo.contains("留学")) {
			return "境外";
		}
		if (appliesTo.contains("境内") || appliesTo.contains("国内")) {
			return "境内";
		}
		return appliesTo.trim();
	}

	private static Set<String> listKey(String values) {
		if (values == null) {
			return Set.of();
		}
		return Arrays.stream(values.split("[,，、;；/]")).map(String::trim).filter(s -> !s.isEmpty())
			.collect(Collectors.toSet());
	}

	private static String certKey(String level) {
		if (level == null) {
			return null;
		}
		String t = level.toUpperCase().replace(" ", "");
		if (t.contains("六级") || t.contains("CET6") || t.contains("CET-6")) {
			return "CET-6";
		}
		if (t.contains("四级") || t.contains("CET4") || t.contains("CET-4")) {
			return "CET-4";
		}
		if (t.contains("雅思")) {
			return "IELTS";
		}
		return t;
	}

	private static String describe(Requirement r) {
		StringBuilder sb = new StringBuilder(r.getType().name());
		if (r.getAppliesTo() != null) {
			sb.append("[仅限").append(r.getAppliesTo()).append("]");
		}
		if (r.getAlternativeGroup() != null) {
			sb.append("[其一]");
		}
		append(sb, "level", r.getLevel());
		append(sb, "minScore", r.getMinScore());
		append(sb, "minDate", r.getMinDate());
		append(sb, "maxDate", r.getMaxDate());
		append(sb, "maxAge", r.getMaxAge());
		if (r.isAgeStrict()) {
			sb.append(" 未满");
		}
		append(sb, "ageRef", r.getAgeReferenceDate());
		append(sb, "list", r.getListValues());
		return sb.toString();
	}

	private static void append(StringBuilder sb, String key, Object value) {
		if (value != null) {
			sb.append(' ').append(key).append('=').append(value);
		}
	}
}
