package io.github.jobexplorer.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.jobexplorer.domain.Company;

public interface CompanyRepository extends JpaRepository<Company, Long> {
}
