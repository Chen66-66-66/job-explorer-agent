package io.github.jobexplorer.domain;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;

/** 招聘公告原文。保存原文是为了让每条抽取出的条件都能回到出处。 */
@Entity
public class Announcement {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private Company company;

	@Column(nullable = false)
	private String title;

	private String sourceUrl;

	/** 用户查看公告的日期；公开信息会过期，必须记录。 */
	private LocalDate viewedAt;

	@Lob
	@Column(nullable = false)
	private String content;

	/** 最近一次完成条件核查（模型抽取成功或人工标记）的时间；为空表示尚未核查。 */
	private Instant checkedAt;

	private Instant createdAt = Instant.now();

	protected Announcement() {
	}

	public Announcement(Company company, String title, String content) {
		this.company = company;
		this.title = title;
		this.content = content;
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

	public String getContent() {
		return content;
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
