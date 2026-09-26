package io.github.jobexplorer.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/** 用户在探索过程中的想法和记录，挂在公司下，可选关联到某个岗位。 */
@Entity
public class Note {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private Company company;

	@ManyToOne(fetch = FetchType.LAZY)
	private Position position;

	@Column(nullable = false, length = 4000)
	private String content;

	private Instant createdAt = Instant.now();

	protected Note() {
	}

	public Note(Company company, Position position, String content) {
		this.company = company;
		this.position = position;
		this.content = content;
	}

	public Long getId() {
		return id;
	}

	public Company getCompany() {
		return company;
	}

	public Position getPosition() {
		return position;
	}

	public String getContent() {
		return content;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
