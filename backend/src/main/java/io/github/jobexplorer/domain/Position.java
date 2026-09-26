package io.github.jobexplorer.domain;

import java.time.Instant;
import java.time.LocalDate;

import io.github.jobexplorer.domain.Enums.PositionStatus;
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

/** 岗位，是「公司 → 岗位」树的叶子节点。内容由用户登录招聘网站后录入。 */
@Entity
public class Position {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private Company company;

	@Column(nullable = false)
	private String title;

	/** 具体用人单位，如某子公司或分行。 */
	private String unit;

	private String location;

	private LocalDate deadline;

	@Lob
	private String jdText;

	private String sourceUrl;

	private LocalDate viewedAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PositionStatus status = PositionStatus.FOUND;

	/** 最近一次完成条件核查（模型抽取成功或人工标记）的时间；为空表示尚未核查。 */
	private Instant checkedAt;

	private Instant createdAt = Instant.now();

	protected Position() {
	}

	public Position(Company company, String title) {
		this.company = company;
		this.title = title;
	}

	public Long getId() {
		return id;
	}

	public Company getCompany() {
		return company;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getUnit() {
		return unit;
	}

	public void setUnit(String unit) {
		this.unit = unit;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public LocalDate getDeadline() {
		return deadline;
	}

	public void setDeadline(LocalDate deadline) {
		this.deadline = deadline;
	}

	public String getJdText() {
		return jdText;
	}

	public void setJdText(String jdText) {
		this.jdText = jdText;
	}

	public String getSourceUrl() {
		return sourceUrl;
	}

	public void setSourceUrl(String sourceUrl) {
		this.sourceUrl = sourceUrl;
	}

	public LocalDate getViewedAt() {
		return viewedAt;
	}

	public void setViewedAt(LocalDate viewedAt) {
		this.viewedAt = viewedAt;
	}

	public PositionStatus getStatus() {
		return status;
	}

	public void setStatus(PositionStatus status) {
		this.status = status;
	}

	public Instant getCheckedAt() {
		return checkedAt;
	}

	public void setCheckedAt(Instant checkedAt) {
		this.checkedAt = checkedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
