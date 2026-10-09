package io.github.jobexplorer.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import io.github.jobexplorer.domain.ExtractionRejection;
import io.github.jobexplorer.domain.Requirement;
import tools.jackson.databind.json.JsonMapper;

/**
 * 驳回记录用到的纯计算：抽取结果的指纹、原文指纹、参数说明、恢复用的快照。不访问数据库，便于测试。
 */
public final class Rejections {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private Rejections() {
	}

	/**
	 * 同一个抽取结果的指纹：类型、引用（忽略空白）、解析参数、适用对象、是否属于「满足其一」组。
	 * 不含说明文字和组名：这两项模型每次可能写法不同，但错误是同一个。
	 * 任何参数不同都算不同的抽取，例如上次把 500 分读成 200 分被驳回，这次读对了就不会被跳过。
	 */
	public static String fingerprint(Requirement r) {
		return sha256(String.join("|", r.getType().name(), norm(r.getQuote()), norm(r.getLevel()), num(r.getMinScore()),
				String.valueOf(r.isAllowEquivalent()), date(r.getMinDate()), date(r.getMaxDate()),
				r.getMaxAge() == null ? "" : r.getMaxAge().toString(), String.valueOf(r.isAgeStrict()),
				date(r.getAgeReferenceDate()), norm(r.getListValues()), norm(r.getAppliesTo()),
				String.valueOf(r.getAlternativeGroup() != null)));
	}

	/** 原文指纹，用来判断驳回之后原文有没有改动。 */
	public static String sourceHash(String sourceText) {
		return sha256(sourceText == null ? "" : sourceText);
	}

	/** 解析出的参数，供人阅读。 */
	public static String params(Requirement r) {
		List<String> parts = new ArrayList<>();
		if (r.getLevel() != null) {
			parts.add("等级 " + r.getLevel());
		}
		if (r.getMinScore() != null) {
			parts.add("最低分 " + num(r.getMinScore()));
		}
		if (r.isAllowEquivalent()) {
			parts.add("接受同等水平");
		}
		if (r.getMinDate() != null) {
			parts.add("起始 " + r.getMinDate());
		}
		if (r.getMaxDate() != null) {
			parts.add("截止 " + r.getMaxDate());
		}
		if (r.getMaxAge() != null) {
			parts.add((r.isAgeStrict() ? "未满 " : "不超过 ") + r.getMaxAge() + " 周岁");
		}
		if (r.getAgeReferenceDate() != null) {
			parts.add("年龄计算日 " + r.getAgeReferenceDate());
		}
		if (r.getListValues() != null) {
			parts.add("名单 " + r.getListValues());
		}
		if (r.getAppliesTo() != null) {
			parts.add("仅限 " + r.getAppliesTo());
		}
		if (r.getAlternativeGroup() != null) {
			parts.add("满足其一 " + r.getAlternativeGroup());
		}
		return parts.isEmpty() ? null : String.join("；", parts);
	}

	/** 恢复用的快照：抽取结果本身，加上当时程序发现的解析问题（如日期无法解析，原始字符串已不在条件里）。 */
	public record Snapshot(ExtractedRequirement extracted, String parseIssue) {
	}

	public static String snapshot(Requirement r) {
		ExtractedRequirement e = new ExtractedRequirement(r.getType().name(), r.getDescription(), r.getQuote(),
				r.getAppliesTo(), r.getAlternativeGroup(), r.getLevel(), r.getMinScore(), r.isAllowEquivalent(),
				string(r.getMinDate()), string(r.getMaxDate()), r.getMaxAge(), r.isAgeStrict(),
				string(r.getAgeReferenceDate()), r.getListValues());
		return JSON.writeValueAsString(new Snapshot(e, r.getParseIssue()));
	}

	public static Snapshot readSnapshot(String json) {
		return JSON.readValue(json, Snapshot.class);
	}

	/**
	 * 重新抽取时的过滤器：只用原文未改动的驳回记录；每条驳回在一次抽取中只抵消一个结果，
	 * 这样「重复」被驳回一条后，重新抽取仍会保留另一条。
	 */
	public static final class Filter {

		private final Map<String, Deque<ExtractionRejection>> remaining = new HashMap<>();

		public Filter(List<ExtractionRejection> rejections, String currentSourceText) {
			String hash = sourceHash(currentSourceText);
			for (ExtractionRejection r : rejections) {
				if (r.getSourceHash().equals(hash)) {
					remaining.computeIfAbsent(r.getFingerprint(), k -> new ArrayDeque<>()).add(r);
				}
			}
		}

		/** 是此前驳回过的同一个抽取时，返回对应的驳回记录；否则返回 null。 */
		public ExtractionRejection match(Requirement r) {
			Deque<ExtractionRejection> d = remaining.get(fingerprint(r));
			return d == null ? null : d.poll();
		}
	}

	private static String norm(String s) {
		return s == null ? "" : s.replaceAll("\\s+", "");
	}

	private static String num(Double d) {
		return d == null ? "" : BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
	}

	private static String date(LocalDate d) {
		return d == null ? "" : d.toString();
	}

	private static String string(LocalDate d) {
		return d == null ? null : d.toString();
	}

	private static String sha256(String s) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
