package io.github.jobexplorer.domain;

import java.time.Instant;

import io.github.jobexplorer.domain.Enums.RejectionReason;
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
 * 一次被本人驳回的模型抽取结果。删除抽错的条件时留下记录，而不是让它直接消失：
 * 可以回看抽取错在哪里、按原因统计；同一原文重新抽取时，完全相同的错误抽取不再出现；驳回错了可以恢复。
 * 只对驳回时的原文有效：原文改动后不再据此跳过。
 */
@Entity
public class ExtractionRejection {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private Company company;

	@ManyToOne(fetch = FetchType.LAZY)
	private Announcement announcement;

	@ManyToOne(fetch = FetchType.LAZY)
	private Position position;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, columnDefinition = "varchar(40)")
	private RequirementType type;

	@Column(nullable = false, length = 1000)
	private String description;

	@Lob
	private String quote;

	/** 解析出的参数，供人阅读，如「最低分 425；接受同等水平」。 */
	@Column(length = 1000)
	private String params;

	/** 被驳回的抽取结果（JSON），用于恢复。 */
	@Lob
	@Column(nullable = false)
	private String snapshot;

	/** 抽取结果的指纹：类型、引用、解析参数相同即视为同一个抽取（不含模型每次措辞不同的说明）。 */
	@Column(nullable = false, length = 64)
	private String fingerprint;

	/** 驳回时原文的指纹；原文改动后，这条驳回不再用于跳过重新抽取的结果。 */
	@Column(nullable = false, length = 64)
	private String sourceHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, columnDefinition = "varchar(40)")
	private RejectionReason reason;

	@Column(length = 1000)
	private String note;

	private Instant rejectedAt = Instant.now();

	protected ExtractionRejection() {
	}

	public ExtractionRejection(Requirement r, RejectionReason reason, String note, String params, String snapshot,
			String fingerprint, String sourceHash) {
		this.company = r.getCompany();
		this.announcement = r.getAnnouncement();
		this.position = r.getPosition();
		this.type = r.getType();
		this.description = r.getDescription();
		this.quote = r.getQuote();
		this.reason = reason;
		this.note = note;
		this.params = params;
		this.snapshot = snapshot;
		this.fingerprint = fingerprint;
		this.sourceHash = sourceHash;
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

	public Position getPosition() {
		return position;
	}

	public RequirementType getType() {
		return type;
	}

	public String getDescription() {
		return description;
	}

	public String getQuote() {
		return quote;
	}

	public String getParams() {
		return params;
	}

	public String getSnapshot() {
		return snapshot;
	}

	public String getFingerprint() {
		return fingerprint;
	}

	public String getSourceHash() {
		return sourceHash;
	}

	public RejectionReason getReason() {
		return reason;
	}

	public String getNote() {
		return note;
	}

	public Instant getRejectedAt() {
		return rejectedAt;
	}

	/** 被驳回条件所依据的原文：岗位级用 JD，公司级用公告。 */
	public String sourceText() {
		if (position != null) {
			return position.getJdText();
		}
		return announcement == null ? null : announcement.getContent();
	}
}
