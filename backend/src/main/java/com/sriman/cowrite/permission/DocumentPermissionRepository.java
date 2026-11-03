package com.sriman.cowrite.permission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentPermissionRepository extends JpaRepository<DocumentPermission, Long> {

	Optional<DocumentPermission> findByDocumentIdAndUserId(Long documentId, Long userId);

	boolean existsByDocumentIdAndUserId(Long documentId, Long userId);

	void deleteByDocumentId(Long documentId);

}
