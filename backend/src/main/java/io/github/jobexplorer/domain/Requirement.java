package io.github.jobexplorer.domain;

import java.time.LocalDate;

import io.github.jobexplorer.domain.Enums.RequirementOrigin;
import io.github.jobexplorer.domain.Enums.RequirementType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;

/**
 * 一条报名条件。
 * position 为空表示公司级条件（来自公告，作用于全部岗位，用于剪枝）；
 * 不为空表示岗位级条件（来自岗位 JD）。
 * 结构化参数（level、minScore 等）按类型使用，未用到的留空。
 */
@Entity
public class Requirement {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private Company company;

	@ManyToOne(fetch = FetchType.LAZY)
	private Announcement announcement;

	@ManyToOne(fetch = FetchType.LAZY)
	private Position position;

	// 枚举按字符串存储且不在数据库层固定取值：Hibernate 默认会把当时的取值写进列类型，之后新增取值时旧库会拒绝写入
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, columnDefinition = "varchar(40)")
	private RequirementType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, columnDefinition = "varchar(40)")
	private RequirementOrigin origin;

	@Column(nullable = false, length = 1000)
	private String description;

	/** 原文引用，必须能在来源文本中逐字找到。 */
	@Lob
	private String quote;

	/** 程序核对的结果：引用是否真的出现在原文里。 */
	private boolean quoteVerified;

	/** 学历（本科/硕士/博士）或英语证书类型（CET-4 等）。 */
	private String level;

	private Double minScore;

	/** 英语条件是否接受「同等水平」的其他证书。 */
	private boolean allowEquivalent;

	private LocalDate minDate;

	private LocalDate maxDate;

	private Integer maxAge;

	/** 计算年龄的截止日，公告常写「截至某日不超过 N 周岁」。 */
	private LocalDate ageReferenceDate;

	/** 列表型参数，如专业名单，用顿号或逗号分隔。 */
	@Column(length = 2000)
	private String listValues;

	/** 「未满 N 周岁」为 true，「不超过 N 周岁」为 false。 */
	private boolean ageStrict;

	/** 适用对象，如「硕士」「境外院校毕业生」；为空表示适用于所有人。 */
	private String appliesTo;

	/** 同组条件满足其一即可（如「六级或雅思」）；为空表示单独必须满足。 */
	private String alternativeGroup;

	/** 解析结果是否经人工确认。模型抽取的条件未确认前，不能给出确定结论。 */
	private boolean confirmed;

	/** 公司级条件是否确认适用于本批次全部岗位。只有确认过的，才能用来整家剪枝。 */
	private boolean appliesToAllPositions;

	/** 程序发现的解析问题，如日期无法解析、数值与原文不一致；有问题则只能待核实。 */
	@Column(length = 1000)
	private String parseIssue;

	protected Requirement() {
	}

	public Requirement(Company company, RequirementType type, RequirementOrigin origin, String description) {
		this.company = company;
		this.type = type;
		this.origin = origin;
		this.description = description;
		// 人工录入和演示数据视为已确认；模型抽取的需要人工确认
		this.confirmed = origin != RequirementOrigin.LLM;
	}

	public Long getId() {
		return id;
	}

	public Company getCompany() {
		return company;
	}

	public Announcement getAnnouncement() {
		return announcement;
	}

	public void setAnnouncement(Announcement announcement) {
		this.announcement = announcement;
	}

	public Position getPosition() {
		return position;
	}

	public void setPosition(Position position) {
		this.position = position;
	}

	public RequirementType getType() {
		return type;
	}

	public RequirementOrigin getOrigin() {
		return origin;
	}

	public String getDescription() {
		return description;
	}

	public String getQuote() {
		return quote;
	}

	public void setQuote(String quote) {
		this.quote = quote;
	}

	public boolean isQuoteVerified() {
		return quoteVerified;
	}

	public void setQuoteVerified(boolean quoteVerified) {
		this.quoteVerified = quoteVerified;
	}

	public String getLevel() {
		return level;
	}

	public void setLevel(String level) {
		this.level = level;
	}

	public Double getMinScore() {
		return minScore;
	}

	public void setMinScore(Double minScore) {
		this.minScore = minScore;
	}

	public boolean isAllowEquivalent() {
		return allowEquivalent;
	}

	public void setAllowEquivalent(boolean allowEquivalent) {
		this.allowEquivalent = allowEquivalent;
	}

	public LocalDate getMinDate() {
		return minDate;
	}

	public void setMinDate(LocalDate minDate) {
		this.minDate = minDate;
	}

	public LocalDate getMaxDate() {
		return maxDate;
	}

	public void setMaxDate(LocalDate maxDate) {
		this.maxDate = maxDate;
	}

	public Integer getMaxAge() {
		return maxAge;
	}

	public void setMaxAge(Integer maxAge) {
		this.maxAge = maxAge;
	}

	public LocalDate getAgeReferenceDate() {
		return ageReferenceDate;
	}

	public void setAgeReferenceDate(LocalDate ageReferenceDate) {
		this.ageReferenceDate = ageReferenceDate;
	}

	public String getListValues() {
		return listValues;
	}

	public void setListValues(String listValues) {
		this.listValues = listValues;
	}

	public boolean isAgeStrict() {
		return ageStrict;
	}

	public void setAgeStrict(boolean ageStrict) {
		this.ageStrict = ageStrict;
	}

	public String getAppliesTo() {
		return appliesTo;
	}

	public void setAppliesTo(String appliesTo) {
		this.appliesTo = appliesTo;
	}

	public String getAlternativeGroup() {
		return alternativeGroup;
	}

	public void setAlternativeGroup(String alternativeGroup) {
		this.alternativeGroup = alternativeGroup;
	}

	public boolean isConfirmed() {
		return confirmed;
	}

	public void setConfirmed(boolean confirmed) {
		this.confirmed = confirmed;
	}

	public boolean isAppliesToAllPositions() {
		return appliesToAllPositions;
	}

	public void setAppliesToAllPositions(boolean appliesToAllPositions) {
		this.appliesToAllPositions = appliesToAllPositions;
	}

	public String getParseIssue() {
		return parseIssue;
	}

	public void setParseIssue(String parseIssue) {
		this.parseIssue = parseIssue;
	}

	public boolean isCompanyLevel() {
		return position == null;
	}

	/** 这条条件所依据的原文：岗位级用 JD，公司级用公告。 */
	public String sourceText() {
		if (position != null) {
			return position.getJdText();
		}
		return announcement == null ? null : announcement.getContent();
	}
}
