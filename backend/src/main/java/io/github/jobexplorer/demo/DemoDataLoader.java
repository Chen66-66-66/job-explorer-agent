package io.github.jobexplorer.demo;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.jobexplorer.domain.Announcement;
import io.github.jobexplorer.domain.Company;
import io.github.jobexplorer.domain.Enums.RequirementOrigin;
import io.github.jobexplorer.domain.Enums.RequirementType;
import io.github.jobexplorer.domain.Note;
import io.github.jobexplorer.domain.Position;
import io.github.jobexplorer.domain.Requirement;
import io.github.jobexplorer.repository.AnnouncementRepository;
import io.github.jobexplorer.repository.CompanyRepository;
import io.github.jobexplorer.repository.NoteRepository;
import io.github.jobexplorer.repository.PositionRepository;
import io.github.jobexplorer.service.RequirementService;

/**
 * 写入虚构演示数据，让没有配置大模型的人也能看到完整流程。
 * 四家公司分别演示：整家被剪枝、条件基本符合、模型引用核对失败、分支条件。
 * 所有单位名称和公告内容均为虚构。
 */
@Component
@ConditionalOnProperty(name = "jobexplorer.demo-data", havingValue = "true")
public class DemoDataLoader implements ApplicationRunner {

	private final CompanyRepository companies;

	private final AnnouncementRepository announcements;

	private final PositionRepository positions;

	private final NoteRepository notes;

	private final RequirementService requirementService;

	public DemoDataLoader(CompanyRepository companies, AnnouncementRepository announcements,
			PositionRepository positions, NoteRepository notes, RequirementService requirementService) {
		this.companies = companies;
		this.announcements = announcements;
		this.positions = positions;
		this.notes = notes;
		this.requirementService = requirementService;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (companies.count() > 0) {
			return;
		}
		loadPrunedCompany();
		loadEligibleCompany();
		loadUnverifiedQuoteCompany();
		loadBranchingCompany();
	}

	/** 演示四：分支条件。本科、硕士年龄上限不同；六级或雅思满足其一。 */
	private void loadBranchingCompany() {
		Company c = company("示例装备制造公司（虚构）", "示例装备集团", "北森", "装备制造企业，演示分支条件的判断。");
		Announcement a = announcement(c, "示例装备制造 2027 届校招公告", """
				本科生年龄不超过 25 周岁，硕士研究生年龄不超过 28 周岁（年龄计算截至 2027 年 7 月 31 日）。
				英语要求：通过大学英语六级（CET-6）或雅思 6.0 及以上，满足其一即可。
				""");
		Requirement bachelorAge = req(c, a, RequirementType.AGE, "本科生年龄上限",
				"本科生年龄不超过 25 周岁，硕士研究生年龄不超过 28 周岁（年龄计算截至 2027 年 7 月 31 日）");
		bachelorAge.setAppliesTo("本科生");
		bachelorAge.setMaxAge(25);
		bachelorAge.setAgeReferenceDate(LocalDate.of(2027, 7, 31));
		requirementService.verifyAndSave(bachelorAge);
		Requirement masterAge = req(c, a, RequirementType.AGE, "硕士研究生年龄上限",
				"硕士研究生年龄不超过 28 周岁（年龄计算截至 2027 年 7 月 31 日）");
		masterAge.setAppliesTo("硕士研究生");
		masterAge.setMaxAge(28);
		masterAge.setAgeReferenceDate(LocalDate.of(2027, 7, 31));
		requirementService.verifyAndSave(masterAge);
		Requirement cet6 = req(c, a, RequirementType.ENGLISH, "六级（与雅思二选一）", "通过大学英语六级（CET-6）或雅思 6.0 及以上");
		cet6.setLevel("CET-6");
		cet6.setAlternativeGroup("english-1");
		requirementService.verifyAndSave(cet6);
		Requirement ielts = req(c, a, RequirementType.ENGLISH, "雅思 6.0（与六级二选一）", "通过大学英语六级（CET-6）或雅思 6.0 及以上");
		ielts.setLevel("IELTS");
		ielts.setMinScore(6.0);
		ielts.setAlternativeGroup("english-1");
		requirementService.verifyAndSave(ielts);
	}

	/** 演示一：公告要求六级，档案只有四级，整家公司被剪掉。 */
	private void loadPrunedCompany() {
		Company c = company("示例能源集团（虚构）", "示例控股", "北森", "能源行业央企，信息化岗位集中在数字科技子公司。");
		Announcement a = announcement(c, "示例能源集团 2027 届校园招聘公告", """
				一、招聘对象：2027 届全日制高校毕业生，境外院校毕业生须在 2026 年 9 月 1 日至 2027 年 8 月 31 日期间毕业，并取得教育部留学服务中心学历学位认证。
				二、基本条件：硕士研究生年龄不超过 28 周岁；须通过大学英语六级考试（CET-6）。
				三、报名时间：即日起至 2026 年 12 月 31 日。
				""");
		Requirement eng = req(c, a, RequirementType.ENGLISH, "须通过大学英语六级", "须通过大学英语六级考试（CET-6）");
		eng.setLevel("CET-6");
		// 已人工确认适用于本批次全部岗位，所以可以据此整家剪枝
		eng.setAppliesToAllPositions(true);
		requirementService.verifyAndSave(eng);
		Requirement win = req(c, a, RequirementType.GRADUATION_WINDOW, "境外院校毕业时间窗口",
				"境外院校毕业生须在 2026 年 9 月 1 日至 2027 年 8 月 31 日期间毕业");
		win.setMinDate(LocalDate.of(2026, 9, 1));
		win.setMaxDate(LocalDate.of(2027, 8, 31));
		win.setAppliesTo("境外院校毕业生");
		win.setAppliesToAllPositions(true);
		requirementService.verifyAndSave(win);
	}

	/** 演示二：条件基本符合，只剩留服认证待确认；下有两个岗位。 */
	private void loadEligibleCompany() {
		Company c = company("示例信息科技有限公司（虚构）", "示例通信集团", "国聘", "通信集团旗下科技子公司，招聘软件与数据方向。");
		Announcement a = announcement(c, "示例信息科技 2027 届校招简章", """
				应聘条件：
				1. 学历要求：硕士研究生及以上学历；
				2. 专业要求：计算机科学与技术、软件工程、数据科学与大数据技术等相关专业；
				3. 毕业时间在 2026 年 1 月 1 日至 2027 年 7 月 31 日之间，境外院校毕业生需取得教育部留学服务中心认证；
				4. 英语达到大学英语四级 425 分及以上或同等水平。
				""");
		Requirement deg = req(c, a, RequirementType.DEGREE, "硕士及以上", "学历要求：硕士研究生及以上学历");
		deg.setLevel("硕士");
		requirementService.verifyAndSave(deg);
		Requirement major = req(c, a, RequirementType.MAJOR, "计算机相关专业",
				"专业要求：计算机科学与技术、软件工程、数据科学与大数据技术等相关专业");
		major.setListValues("计算机科学与技术、软件工程、数据科学与大数据技术");
		requirementService.verifyAndSave(major);
		Requirement eng = req(c, a, RequirementType.ENGLISH, "CET-4 425 或同等", "英语达到大学英语四级 425 分及以上或同等水平");
		eng.setLevel("CET-4");
		eng.setMinScore(425.0);
		eng.setAllowEquivalent(true);
		requirementService.verifyAndSave(eng);
		requirementService.verifyAndSave(
				req(c, a, RequirementType.OVERSEAS_CERT, "境外学历需留服认证", "境外院校毕业生需取得教育部留学服务中心认证"));

		Position data = position(c, "数据开发工程师", "北京", LocalDate.of(2026, 11, 30), """
				岗位职责：负责数据仓库建模与 ETL 开发，建立数据质量校验规则。
				任职要求：熟练掌握 SQL，熟悉 Java 或 Python；本岗位工作地点为北京。
				""");
		Position java = position(c, "Java 开发工程师", "上海", LocalDate.of(2026, 10, 20), """
				岗位职责：负责业务系统后端开发与单元测试。
				任职要求：熟悉 Java、Spring Boot 与关系型数据库。
				""");
		Requirement dl = new Requirement(c, RequirementType.DEADLINE, RequirementOrigin.DEMO, "岗位截止日");
		dl.setPosition(java);
		dl.setMaxDate(LocalDate.of(2026, 10, 20));
		requirementService.verifyAndSave(dl);
		notes.save(new Note(c, data, "这个岗位的数据质量方向和我的数仓项目最对口，优先投。"));
	}

	/** 演示三：模拟模型给出一条原文中不存在的引用，系统不采信，判为待核实。 */
	private void loadUnverifiedQuoteCompany() {
		Company c = company("示例城市银行金融科技部（虚构）", null, "智联", "地方银行科技岗，公告较简略。");
		Announcement a = announcement(c, "示例城市银行 2027 校园招聘", """
				面向 2027 届毕业生招聘金融科技岗位若干名，要求计算机相关专业，具体条件以岗位说明为准。
				""");
		// 以模型抽取的身份写入，引用在原文中找不到，核对会失败
		Requirement r = new Requirement(c, RequirementType.AGE, RequirementOrigin.LLM, "（模拟模型抽取）年龄不超过 24 周岁");
		r.setAnnouncement(a);
		r.setQuote("硕士研究生年龄不超过 24 周岁");
		r.setMaxAge(24);
		requirementService.verifyAndSave(r);
	}

	private Company company(String name, String group, String platform, String summary) {
		Company c = new Company(name);
		c.setGroupName(group);
		c.setPlatform(platform);
		c.setSummary(summary);
		return companies.save(c);
	}

	private Announcement announcement(Company c, String title, String content) {
		Announcement a = new Announcement(c, title, content.strip());
		a.setViewedAt(LocalDate.of(2026, 9, 26));
		a.setCheckedAt(Instant.now());
		return announcements.save(a);
	}

	private Position position(Company c, String title, String location, LocalDate deadline, String jd) {
		Position p = new Position(c, title);
		p.setLocation(location);
		p.setDeadline(deadline);
		p.setJdText(jd.strip());
		p.setViewedAt(LocalDate.of(2026, 9, 26));
		p.setCheckedAt(Instant.now());
		return positions.save(p);
	}

	private static Requirement req(Company c, Announcement a, RequirementType type, String desc, String quote) {
		Requirement r = new Requirement(c, type, RequirementOrigin.DEMO, desc);
		r.setAnnouncement(a);
		r.setQuote(quote);
		r.setAppliesToAllPositions(true);
		return r;
	}
}
