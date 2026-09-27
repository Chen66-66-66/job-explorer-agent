package io.github.jobexplorer.domain;

import java.time.Instant;

import io.github.jobexplorer.domain.Enums.CompanyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** 招聘单位，是「公司 → 岗位」这棵树的根节点。 */
@Entity
public class Company {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	/** 所属集团，可空。 */
	private String groupName;

	/** 招聘网站地址，用于指引用户自己登录查看。 */
	private String careerSiteUrl;

	/** 招聘平台，如 北森 / 国聘 / 智联 / 自有。 */
	private String platform;

	@Column(length = 2000)
	private String summary;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, columnDefinition = "varchar(40)")
	private CompanyStatus status = CompanyStatus.CANDIDATE;

	private Instant createdAt = Instant.now();

	protected Company() {
	}

	public Company(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getGroupName() {
		return groupName;
	}

	public void setGroupName(String groupName) {
		this.groupName = groupName;
	}

	public String getCareerSiteUrl() {
		return careerSiteUrl;
	}

	public void setCareerSiteUrl(String careerSiteUrl) {
		this.careerSiteUrl = careerSiteUrl;
	}

	public String getPlatform() {
		return platform;
	}

	public void setPlatform(String platform) {
		this.platform = platform;
	}

	public String getSummary() {
		return summary;
	}

	public void setSummary(String summary) {
		this.summary = summary;
	}

	public CompanyStatus getStatus() {
		return status;
	}

	public void setStatus(CompanyStatus status) {
		this.status = status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
