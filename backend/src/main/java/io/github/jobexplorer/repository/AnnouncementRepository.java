package io.github.jobexplorer.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.jobexplorer.domain.Announcement;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

	List<Announcement> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
}
