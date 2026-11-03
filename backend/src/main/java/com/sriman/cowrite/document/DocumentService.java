package com.sriman.cowrite.document;

import com.sriman.cowrite.common.web.PageResponse;
import com.sriman.cowrite.document.dto.DocumentRequest;
import com.sriman.cowrite.document.dto.DocumentResponse;
import com.sriman.cowrite.document.exception.DocumentNotFoundException;
import com.sriman.cowrite.permission.DocumentPermissionRepository;
import com.sriman.cowrite.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

	@Transactional
	public DocumentResponse update(Long id, DocumentRequest request) {
		Document document = findDocument(id);
		document.setTitle(request.title());
		document.setContent(request.content());

		return DocumentResponse.from(documentRepository.save(document));
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
