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
	 * Owned OR shared documents, with the owner fetched in ONE query (plus the count query).
	 * Without the entity graph, mapping DTOs triggers one extra SELECT per document (N+1).
	 */
	@EntityGraph(attributePaths = "owner")
	@Query(value = """
			select d from Document d
			where d.owner.id = :userId
			   or exists (select 1 from DocumentPermission p
			              where p.document.id = d.id and p.user.id = :userId)
			""",
			countQuery = """
					select count(d) from Document d
					where d.owner.id = :userId
					   or exists (select 1 from DocumentPermission p
					              where p.document.id = d.id and p.user.id = :userId)
					""")
	Page<Document> findAllAccessibleByUserId(@Param("userId") Long userId, Pageable pageable);

	@EntityGraph(attributePaths = "owner")
	@Query("select d from Document d where d.id = :id")
	Optional<Document> findWithOwnerById(@Param("id") Long id);

	/** Ownership lookup used by the central permission service. */
	boolean existsByIdAndOwnerId(Long id, Long ownerId);

}
