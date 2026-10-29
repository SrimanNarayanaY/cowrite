package com.sriman.cowrite.document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {

	/**
	 * List with owner in ONE query (plus the count query Spring Data needs for a Page).
	 * Without the entity graph, mapping DTOs triggers one extra SELECT per document (N+1).
	 */
	@EntityGraph(attributePaths = "owner")
	@Query(value = "select d from Document d",
			countQuery = "select count(d) from Document d")
	Page<Document> findAllWithOwner(Pageable pageable);

	@EntityGraph(attributePaths = "owner")
	@Query("select d from Document d where d.id = :id")
	Optional<Document> findWithOwnerById(@Param("id") Long id);


}
