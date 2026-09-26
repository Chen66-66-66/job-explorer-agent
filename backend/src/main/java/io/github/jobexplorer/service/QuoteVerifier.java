package io.github.jobexplorer.service;

import org.springframework.stereotype.Component;

/**
 * 核对模型给出的原文引用是否真的出现在来源文本里。
 * 这是防止模型编造依据的第一道关：核对不通过的条件，不能得出确定结论。
 * 比较前统一去掉空白、统一全半角标点，避免因排版差异误判。
 */
@Component
public class QuoteVerifier {

	/** 太短的引用（如「硕士」）几乎必然能匹配上，没有证明力。 */
	static final int MIN_QUOTE_LENGTH = 4;

	public boolean verify(String quote, String sourceText) {
		if (quote == null || sourceText == null) {
			return false;
		}
		String q = normalize(quote);
		if (q.length() < MIN_QUOTE_LENGTH) {
			return false;
		}
		return normalize(sourceText).contains(q);
	}

	static String normalize(String text) {
		StringBuilder sb = new StringBuilder(text.length());
		for (char c : text.toCharArray()) {
			if (Character.isWhitespace(c) || c == '　') {
				continue;
			}
			sb.append(toHalfWidth(c));
		}
		return sb.toString().toLowerCase();
	}

	private static char toHalfWidth(char c) {
		// 全角 ASCII 区段（！到～）映射到半角
		if (c >= '！' && c <= '～') {
			return (char) (c - 0xFEE0);
		}
		return switch (c) {
			case '，', '、' -> ',';
			case '。' -> '.';
			case '“', '”' -> '"';
			case '‘', '’' -> '\'';
			case '—', '–' -> '-';
			default -> c;
		};
	}
}
