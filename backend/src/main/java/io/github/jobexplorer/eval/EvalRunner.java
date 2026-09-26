package io.github.jobexplorer.eval;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.github.jobexplorer.config.CandidateProfile;
import io.github.jobexplorer.domain.Announcement;
import io.github.jobexplorer.domain.Company;
import io.github.jobexplorer.domain.Enums.Verdict;
import io.github.jobexplorer.domain.Requirement;
import io.github.jobexplorer.service.EligibilityChecker;
import io.github.jobexplorer.service.ExtractedRequirement;
import io.github.jobexplorer.service.LlmRequirementExtractor;
import io.github.jobexplorer.service.ModelCallException;
import io.github.jobexplorer.service.RequirementService;
import io.github.jobexplorer.service.VerdictAggregator;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 测评程序。设置 jobexplorer.eval.dir 时启动，跑完输出报告后退出。
 *
 * 对每份公告、每个虚构求职者，比较三种结论：
 * - 标准答案：人工写的条件 → 规则判断；
 * - 本系统：模型抽取的条件（模拟用户全部点了确认）→ 同一套规则判断；
 * - 对照组：把公告和档案直接交给同一个模型，问能不能报。
 * 核心指标是「错误排除」：标准答案为符合或待核实，却被判为不符合。
 */
@Component
@ConditionalOnProperty(name = "jobexplorer.eval.dir")
public class EvalRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);

	private final LlmRequirementExtractor extractor;

	private final RequirementService requirementService;

	private final EligibilityChecker checker;

	private final ObjectProvider<ChatModel> chatModel;

	private final Path dir;

	private final boolean runBaseline;

	private final JsonMapper json = JsonMapper.builder().build();

	public EvalRunner(LlmRequirementExtractor extractor, RequirementService requirementService,
			EligibilityChecker checker, ObjectProvider<ChatModel> chatModel,
			@Value("${jobexplorer.eval.dir}") String dir,
			@Value("${jobexplorer.eval.baseline:true}") boolean runBaseline) {
		this.extractor = extractor;
		this.requirementService = requirementService;
		this.checker = checker;
		this.chatModel = chatModel;
		this.dir = Path.of(dir);
		this.runBaseline = runBaseline;
	}

	record NamedProfile(String id, CandidateProfile profile) {
	}

	@Override
	public void run(ApplicationArguments args) throws IOException {
		ChatModel model = chatModel.getIfAvailable();
		if (model == null) {
			log.error("测评需要大模型：请设置 LLM_PROVIDER 和 LLM_API_KEY");
			return;
		}
		List<NamedProfile> profiles = json.readValue(dir.resolve("profiles.json").toFile(),
				new TypeReference<List<NamedProfile>>() {
				});
		List<EvalCase> cases = loadCases();
		log.info("测评开始：{} 份公告 × {} 个求职者", cases.size(), profiles.size());

		BaselineJudge baseline = new BaselineJudge(model);
		Map<String, Tally> tallies = new LinkedHashMap<>();
		StringBuilder details = new StringBuilder();
		for (EvalCase c : cases) {
			log.info("  {}（{}）", c.id(), c.split());
			Tally t = tallies.computeIfAbsent(c.split(), k -> new Tally());
			details.append(runCase(c, profiles, baseline, t));
		}

		String report = render(cases.size(), profiles, tallies, details);
		Path out = dir.resolve("reports")
			.resolve("eval-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".md");
		Files.createDirectories(out.getParent());
		Files.writeString(out, report, StandardCharsets.UTF_8);
		log.info("测评完成，报告：{}", out.toAbsolutePath());
		tallies.forEach((split, t) -> log.info("[{}] {}", split, t.summaryLine()));
	}

	/** cases/ 放可公开的虚构样例；private/ 放真实公告，已被 .gitignore 排除。 */
	private List<EvalCase> loadCases() throws IOException {
		List<EvalCase> cases = new ArrayList<>();
		for (String sub : new String[] { "cases", "private" }) {
			Path d = dir.resolve(sub);
			if (!Files.isDirectory(d)) {
				continue;
			}
			try (Stream<Path> files = Files.list(d)) {
				for (Path f : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
					cases.add(json.readValue(f.toFile(), EvalCase.class));
				}
			}
		}
		return cases;
	}

	private String runCase(EvalCase c, List<NamedProfile> profiles, BaselineJudge baseline, Tally t) {
		StringBuilder sb = new StringBuilder("\n### ").append(c.id()).append("（").append(c.split()).append("）\n\n");
		if (c.source() != null) {
			sb.append("来源：").append(c.source()).append("\n\n");
		}
		Company company = new Company("eval");
		Announcement ann = new Announcement(company, c.id(), c.text());

		List<Requirement> extracted = new ArrayList<>();
		try {
			for (ExtractedRequirement e : extractor.extract(c.text())) {
				Requirement r = RequirementService.fromExtracted(company, e);
				r.setAnnouncement(ann);
				requirementService.runSourceChecks(r);
				// 模拟用户对所有条件都点了确认：测的是抽取质量本身会不会导致误判
				r.setConfirmed(true);
				r.setAppliesToAllPositions(true);
				extracted.add(r);
			}
		}
		catch (ModelCallException ex) {
			t.extractionFailures++;
			sb.append("**抽取失败**：").append(ex.getMessage()).append("\n\n");
		}
		List<Requirement> gold = c.gold().stream().map(g -> g.toRequirement(company)).toList();

		RequirementMatcher.Result m = RequirementMatcher.match(gold, extracted);
		t.add(m);
		long verified = extracted.stream().filter(Requirement::isQuoteVerified).count();
		long flagged = extracted.stream().filter(r -> r.getParseIssue() != null).count();
		t.extracted += extracted.size();
		t.quoteVerified += verified;
		t.parseFlagged += flagged;
		sb.append("条件抽取：标准 ").append(m.gold()).append(" 条，正确 ").append(m.correct()).append("，参数错误 ")
			.append(m.wrongParams()).append("，遗漏 ").append(m.missed()).append("，多抽 ").append(m.spurious())
			.append("；引用在原文中找到 ").append(verified).append("/").append(extracted.size()).append("，数值核对报警 ")
			.append(flagged).append("\n\n");
		m.problems().forEach(p -> sb.append("- ").append(p).append("\n"));

		sb.append("\n| 求职者 | 标准答案 | 本系统 | 对照组 |\n|---|---|---|---|\n");
		for (NamedProfile np : profiles) {
			Verdict derived = overall(gold, np.profile(), c.asOf());
			Verdict human = c.expected().get(np.id());
			Verdict goldV = human != null ? human : derived;
			Verdict sysV = overall(extracted, np.profile(), c.asOf());
			Verdict baseV = null;
			if (runBaseline) {
				try {
					baseV = baseline.judge(c.text(), np.profile(), c.asOf());
				}
				catch (RuntimeException ex) {
					t.baselineFailures++;
				}
			}
			t.addVerdicts(goldV, human != null, sysV, baseV);
			String goldCell = human != null ? label(human) + "（人工）" : label(derived) + "（规则推算）";
			sb.append("| ").append(np.id()).append(" | ").append(goldCell).append(" | ")
				.append(mark(goldV, sysV)).append(" | ").append(baseV == null ? "—" : mark(goldV, baseV)).append(" |\n");
		}
		return sb.toString();
	}

	private Verdict overall(List<Requirement> reqs, CandidateProfile p, LocalDate asOf) {
		return VerdictAggregator.overall(reqs.stream().map(r -> checker.check(r, p, asOf)).toList(), true);
	}

	private static String mark(Verdict gold, Verdict got) {
		String s = label(got);
		if (Tally.wronglyExcluded(gold, got)) {
			return s + " ❌错误排除";
		}
		if (Tally.wronglyIncluded(gold, got)) {
			return s + " ⚠错误放行";
		}
		if (Tally.overconfident(gold, got)) {
			return s + " ·过度确定";
		}
		return s;
	}

	private static String label(Verdict v) {
		return switch (v) {
			case PASS -> "符合";
			case FAIL -> "不符合";
			case UNKNOWN -> "待核实";
			case PARTIAL -> "部分通过";
			case NOT_APPLICABLE -> "不适用";
		};
	}

	private String render(int caseCount, List<NamedProfile> profiles, Map<String, Tally> tallies,
			StringBuilder details) {
		StringBuilder sb = new StringBuilder("# 条件抽取与资格判断测评报告\n\n");
		sb.append("生成时间：").append(LocalDateTime.now().withNano(0)).append("  \n");
		sb.append("公告 ").append(caseCount).append(" 份 × 虚构求职者 ").append(profiles.size()).append(" 个\n\n");
		sb.append("""
				指标说明：
				- **错误排除**：标准答案为「符合」或「待核实」，却被判为「不符合」。这是最要紧的错误：本来可以报的机会被删掉了。
				- **错误放行**：标准答案为「不符合」，却被判为「符合」。
				- **确定率**：给出「符合 / 不符合」而不是「待核实」的比例，防止系统靠全答「待核实」来回避错误。
				- **过度确定**：标准答案为「待核实」，却给出了「符合」或「不符合」。
				- **标准答案**标「人工」的由人逐条判定；标「规则推算」的由被测规则根据标准条件算出，等于自己给自己出答案，只作参考。
				- 本系统一栏模拟用户对抽取结果全部点了确认，衡量的是抽取质量本身；实际使用时未确认的条件只会显示「待核实」。
				- 样本量小，结果只作诊断参考，不代表普遍可靠性。

				""");
		sb.append("| 分组 | 标准条件 | 抽取正确 | 参数错误 | 遗漏 | 多抽 | 引用找到 | 判断次数 | 本系统错误排除 | 对照组错误排除 | 本系统错误放行 | 对照组错误放行 | 本系统确定率 | 对照组确定率 |\n");
		sb.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
		tallies.forEach((split, t) -> sb.append(t.row(split)));
		sb.append("\n## 逐份明细\n").append(details);
		return sb.toString();
	}
}
