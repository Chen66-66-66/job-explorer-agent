package io.github.jobexplorer.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.jobexplorer.domain.Position;

public interface PositionRepository extends JpaRepository<Position, Long> {

	List<Position> findByCompanyIdOrderByCreatedAtAsc(Long companyId);
}
