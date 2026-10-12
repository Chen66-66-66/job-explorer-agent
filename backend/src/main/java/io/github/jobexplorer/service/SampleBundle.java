package io.github.jobexplorer.service;

import java.util.List;

/**
 * 本地样本的导入格式：一家公司及其公告、岗位。格式说明见仓库 samples/README.md。
 * 日期用 yyyy-MM-dd 字符串，由程序解析；解析失败会在导入报告里说明，而不是悄悄当作没有。
 * 以后各招聘平台的解析适配器，把平台原始数据转换成这个格式再导入。
 */
public record SampleBundle(CompanyPart company, List<AnnouncementPart> announcements, List<PositionPart> positions) {

	public record CompanyPart(String name, String groupName, String careerSiteUrl, String platform) {
	}

	public record AnnouncementPart(String title, String sourceUrl, String viewedAt, String content) {
	}

	public record PositionPart(String title, String unit, String location, String deadline, String sourceUrl,
			String viewedAt, String jdText) {
	}
}
