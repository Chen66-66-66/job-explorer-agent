package io.github.jobexplorer.config;

import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 求职者的硬条件档案。
 * 默认读取 classpath 下的虚构示例（profile-example.yml）；
 * 本地放一份 data/profile.local.yml 即可覆盖，该文件不进 Git。
 * overseasCertPlanned：境外学历本人是否计划按要求办理留服认证。
 */
@ConfigurationProperties(prefix = "candidate")
public record CandidateProfile(
		String name,
		String degree,
		List<String> majors,
		LocalDate graduationDate,
		LocalDate birthDate,
		boolean overseasDegree,
		boolean overseasCertPlanned,
		List<EnglishCert> english) {

	public CandidateProfile {
		majors = majors == null ? List.of() : List.copyOf(majors);
		english = english == null ? List.of() : List.copyOf(english);
	}

	/** 一张英语证书：类型如 CET-4 / CET-6 / IELTS，分数可空。 */
	public record EnglishCert(String type, Double score) {
	}
}
