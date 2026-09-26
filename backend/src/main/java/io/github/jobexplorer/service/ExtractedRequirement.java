package io.github.jobexplorer.service;

import java.util.List;

/**
 * 大模型抽取结果的结构。字段含义见 LlmRequirementExtractor 中的提示词。
 * 日期用字符串接收，由程序解析；解析失败会被记为解析问题，而不是悄悄当作「没有限制」。
 */
public record ExtractedRequirement(
		String type,
		String description,
		String quote,
		String appliesTo,
		String alternativeGroup,
		String level,
		Double minScore,
		Boolean allowEquivalent,
		String minDate,
		String maxDate,
		Integer maxAge,
		Boolean ageStrict,
		String ageReferenceDate,
		String listValues) {

	/** 顶层包一层对象，比直接返回数组更容易让模型输出合法 JSON。 */
	public record Batch(List<ExtractedRequirement> requirements) {
	}
}
