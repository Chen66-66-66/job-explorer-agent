package io.github.jobexplorer.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.jobexplorer.domain.Enums.RequirementOrigin;
import io.github.jobexplorer.domain.Requirement;

public interface RequirementRepository extends JpaRepository<Requirement, Long> {

	/** 公司级条件：来自公告、不挂在具体岗位上。 */
	List<Requirement> findByCompanyIdAndPositionIsNullOrderByIdAsc(Long companyId);

	List<Requirement> findByPositionIdOrderByIdAsc(Long positionId);

	void deleteByAnnouncementIdAndPositionIsNullAndOrigin(Long announcementId, RequirementOrigin origin);

	void deleteByPositionIdAndOrigin(Long positionId, RequirementOrigin origin);
}
