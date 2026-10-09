package io.github.jobexplorer.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.jobexplorer.domain.ExtractionRejection;

public interface ExtractionRejectionRepository extends JpaRepository<ExtractionRejection, Long> {

	List<ExtractionRejection> findByPositionIdOrderByRejectedAtDesc(Long positionId);

	/** 公告（公司级）抽取结果的驳回记录。 */
	List<ExtractionRejection> findByAnnouncementIdAndPositionIsNullOrderByRejectedAtDesc(Long announcementId);

	List<ExtractionRejection> findByCompanyIdAndPositionIsNullOrderByRejectedAtDesc(Long companyId);
}
