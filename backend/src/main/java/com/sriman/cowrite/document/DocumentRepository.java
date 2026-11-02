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
	 * Only the caller's documents, with owner fetched in ONE query (plus the count query).
	 * Without the entity graph, mapping DTOs triggers one extra SELECT per document (N+1).
	 */
	@EntityGraph(attributePaths = "owner")
	@Query(value = "select d from Document d where d.owner.id = :ownerId",
			countQuery = "select count(d) from Document d where d.owner.id = :ownerId")
	Page<Document> findAllByOwnerId(@Param("ownerId") Long ownerId, Pageable pageable);

	/**
	 * Ownership is part of the lookup: another user's document simply does not exist (-> 404),
	 * so the API never reveals that the id exists.
	 */
	@EntityGraph(attributePaths = "owner")
	@Query("select d from Document d where d.id = :id and d.owner.id = :ownerId")
	Optional<Document> findByIdAndOwnerId(@Param("id") Long id, @Param("ownerId") Long ownerId);

}
