package io.github.jobexplorer.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QuoteVerifierTest {

	private final QuoteVerifier verifier = new QuoteVerifier();

	private static final String SOURCE = "二、基本条件：硕士研究生年龄不超过 28 周岁；须通过大学英语六级考试（CET-6）。";

	@Test
	void 逐字存在的引用通过() {
		assertThat(verifier.verify("须通过大学英语六级考试（CET-6）", SOURCE)).isTrue();
	}

	@Test
	void 空白和全半角差异不影响核对() {
		assertThat(verifier.verify("硕士研究生年龄不超过28周岁", SOURCE)).isTrue();
		assertThat(verifier.verify("须通过大学英语六级考试(CET-6)", SOURCE)).isTrue();
	}

	@Test
	void 改写过的引用不通过() {
		assertThat(verifier.verify("硕士年龄不超过 28 岁", SOURCE)).isFalse();
		assertThat(verifier.verify("硕士研究生年龄不超过 24 周岁", SOURCE)).isFalse();
	}

	@Test
	void 过短或为空的引用不通过() {
		assertThat(verifier.verify("硕士", SOURCE)).isFalse();
		assertThat(verifier.verify(null, SOURCE)).isFalse();
		assertThat(verifier.verify("须通过大学英语六级", null)).isFalse();
	}
}
