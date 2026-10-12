package io.github.jobexplorer.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.jobexplorer.domain.Company;

public interface CompanyRepository extends JpaRepository<Company, Long> {

	/** 按名称找公司，导入样本时用来合并到已有公司。 */
	Optional<Company> findFirstByName(String name);
}
