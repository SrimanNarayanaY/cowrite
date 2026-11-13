package com.sriman.cowrite.document;

import com.sriman.cowrite.common.web.PageResponse;
import com.sriman.cowrite.document.dto.DocumentContentRequest;
import com.sriman.cowrite.document.dto.DocumentDetailResponse;
import com.sriman.cowrite.document.dto.DocumentRequest;
import com.sriman.cowrite.document.dto.DocumentResponse;
import com.sriman.cowrite.document.exception.DocumentNotFoundException;
import com.sriman.cowrite.document.exception.DocumentVersionConflictException;
import com.sriman.cowrite.permission.DocumentAccess;
import com.sriman.cowrite.permission.DocumentPermissionRepository;
import com.sriman.cowrite.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Data access only — authorization lives in {@link com.sriman.cowrite.permission.PermissionService}
 * and is enforced by the controller (or any other entry point, e.g. a WebSocket handler).
 */
@Service
@RequiredArgsConstructor
public class DocumentService {

	private final DocumentRepository documentRepository;
	private final DocumentPermissionRepository documentPermissionRepository;
	private final UserRepository userRepository;

	@Transactional
	public DocumentResponse create(DocumentRequest request, Long ownerId) {
		Document document = new Document();
		document.setTitle(request.title());
		document.setContent(request.content());
		// Owner always comes from the authenticated principal, never from the request body
		document.setOwner(userRepository.getReferenceById(ownerId));

		return DocumentResponse.from(documentRepository.save(document));
	}

	@Transactional(readOnly = true)
	public PageResponse<DocumentResponse> listAccessible(Long userId, Pageable pageable) {
		return PageResponse.from(
				documentRepository.findAllAccessibleByUserId(userId, pageable),
				DocumentResponse::from
		);
	}

	@Transactional(readOnly = true)
	public DocumentResponse getById(Long id) {
		return DocumentResponse.from(findDocument(id));
	}

	@Transactional(readOnly = true)
	public DocumentDetailResponse getDetail(Long id, DocumentAccess access) {
		return DocumentDetailResponse.from(findDocument(id), access);
	}

	@Transactional
	public DocumentResponse update(Long id, DocumentRequest request) {
		Document document = findDocument(id);
		document.setTitle(request.title());
		document.setContent(request.content());

		return DocumentResponse.from(documentRepository.saveAndFlush(document));
	}

	/**
	 * Saves editor content with optimistic concurrency.
	 *
	 * <p>The explicit version comparison gives the common case a clear 409 with the
	 * versions involved. The {@code @Version} column is still the real guard: if two
	 * requests pass this check at the same time, Hibernate's conditional UPDATE makes
	 * the slower one update zero rows and fail with an optimistic locking exception,
	 * which the global handler also maps to 409.</p>
	 */
	@Transactional
	public DocumentResponse updateContent(Long id, DocumentContentRequest request) {
		Document document = findDocument(id);

		if (!Objects.equals(document.getVersion(), request.version())) {
			throw new DocumentVersionConflictException(id, request.version(), document.getVersion());
		}

		document.setContent(request.content());

		// Flush now: Hibernate increments @Version during the UPDATE, and the response
		// must carry that new version so the client's next save is accepted.
		return DocumentResponse.from(documentRepository.saveAndFlush(document));
	}

	@Transactional
	public void delete(Long id) {
		Document document = findDocument(id);
		// H2 test schema has no FK cascade, so remove share rows explicitly.
		// In PostgreSQL the FK also has ON DELETE CASCADE.
		documentPermissionRepository.deleteByDocumentId(id);
		documentRepository.delete(document);
	}

	private Document findDocument(Long id) {
		return documentRepository.findWithOwnerById(id)
				.orElseThrow(() -> new DocumentNotFoundException(id));
	}

}
