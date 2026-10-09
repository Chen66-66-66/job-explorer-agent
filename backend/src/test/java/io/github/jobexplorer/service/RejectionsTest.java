package io.github.jobexplorer.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.jobexplorer.domain.Company;
import io.github.jobexplorer.domain.Enums.RejectionReason;
import io.github.jobexplorer.domain.Enums.RequirementOrigin;
import io.github.jobexplorer.domain.Enums.RequirementType;
import io.github.jobexplorer.domain.ExtractionRejection;
import io.github.jobexplorer.domain.Requirement;

class RejectionsTest {

	private static final String SOURCE = "英语要求：通过大学英语六级。";

	private final Company company = new Company("测试公司");

	private Requirement english(String description, String quote, Double minScore, String group) {
		Requirement r = new Requirement(company, RequirementType.ENGLISH, RequirementOrigin.LLM, description);
		r.setQuote(quote);
		r.setLevel("CET-6");
		r.setMinScore(minScore);
		r.setAlternativeGroup(group);
		return r;
	}

	private ExtractionRejection rejection(Requirement r, String sourceText) {
		return new ExtractionRejection(r, RejectionReason.WRONG_VALUE, null, Rejections.params(r), Rejections.snapshot(r),
				Rejections.fingerprint(r), Rejections.sourceHash(sourceText));
	}

	@Test
	void 说明文字_组名_引用中的空白不同_仍是同一个抽取() {
		String a = Rejections.fingerprint(english("六级 425", "通过大学英语六级", 425.0, "english-1"));
		String b = Rejections.fingerprint(english("大学英语六级达到 425 分", "通过大学英语 六级", 425.00, "g2"));
		assertThat(a).isEqualTo(b);
	}

	@Test
	void 参数不同就是不同的抽取_读对了不会被跳过() {
		String wrong = Rejections.fingerprint(english("六级", "通过大学英语六级", 200.0, null));
		String right = Rejections.fingerprint(english("六级", "通过大学英语六级", 500.0, null));
		String grouped = Rejections.fingerprint(english("六级", "通过大学英语六级", 200.0, "english-1"));
		assertThat(wrong).isNotEqualTo(right).isNotEqualTo(grouped);
	}

	@Test
	void 只用原文未改动的驳回_每条驳回只抵消一个结果() {
		Requirement r = english("六级", "通过大学英语六级", null, null);
		ExtractionRejection rej = rejection(r, SOURCE);

		Rejections.Filter sameSource = new Rejections.Filter(List.of(rej), SOURCE);
		assertThat(sameSource.match(english("六级要求", "通过大学英语六级", null, null))).isSameAs(rej);
		assertThat(sameSource.match(english("六级要求", "通过大学英语六级", null, null))).as("已抵消过一次").isNull();

		Rejections.Filter changed = new Rejections.Filter(List.of(rej), SOURCE + "或雅思 6.0。");
		assertThat(changed.match(english("六级", "通过大学英语六级", null, null))).isNull();
	}

	@Test
	void 快照可还原抽取结果和当时的解析问题() {
		Requirement r = english("六级", "通过大学英语六级", 425.0, "english-1");
		r.setMaxDate(LocalDate.of(2027, 7, 31));
		r.setParseIssue("截止日期「2027年7月底」无法解析");
		Rejections.Snapshot s = Rejections.readSnapshot(Rejections.snapshot(r));
		assertThat(s.extracted().type()).isEqualTo("ENGLISH");
		assertThat(s.extracted().minScore()).isEqualTo(425.0);
		assertThat(s.extracted().maxDate()).isEqualTo("2027-07-31");
		assertThat(s.extracted().alternativeGroup()).isEqualTo("english-1");
		assertThat(s.parseIssue()).contains("无法解析");

		Requirement restored = RequirementService.fromExtracted(company, s.extracted());
		assertThat(Rejections.fingerprint(restored)).isEqualTo(Rejections.fingerprint(r));
	}

	@Test
	void 参数说明供人阅读() {
		assertThat(Rejections.params(english("六级", "q", 425.0, "english-1")))
			.isEqualTo("等级 CET-6；最低分 425；满足其一 english-1");
	}
}
