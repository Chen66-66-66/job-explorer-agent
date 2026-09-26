package io.github.jobexplorer.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.jobexplorer.domain.Note;

public interface NoteRepository extends JpaRepository<Note, Long> {

	List<Note> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
}
