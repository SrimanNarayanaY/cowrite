package com.sriman.cowrite.document;

import com.sriman.cowrite.common.web.PageResponse;
import com.sriman.cowrite.document.dto.DocumentRequest;
import com.sriman.cowrite.document.dto.DocumentResponse;
import com.sriman.cowrite.document.exception.DocumentNotFoundException;
import com.sriman.cowrite.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentService {

	private final DocumentRepository documentRepository;
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
	public PageResponse<DocumentResponse> list(Long ownerId, Pageable pageable) {
		Page<Document> page = documentRepository.findAllByOwnerId(ownerId, pageable);

		return PageResponse.from(page, DocumentResponse::from);
	}

	@Transactional(readOnly = true)
	public DocumentResponse getById(Long id, Long ownerId) {
		return DocumentResponse.from(findOwnedDocument(id, ownerId));
	}

	@Transactional
	public DocumentResponse update(Long id, DocumentRequest request, Long ownerId) {
		Document document = findOwnedDocument(id, ownerId);
		document.setTitle(request.title());
		document.setContent(request.content());

		return DocumentResponse.from(documentRepository.save(document));
	}

	@Transactional
	public void delete(Long id, Long ownerId) {
		documentRepository.delete(findOwnedDocument(id, ownerId));
	}

	private Document findOwnedDocument(Long id, Long ownerId) {
		return documentRepository.findByIdAndOwnerId(id, ownerId)
				.orElseThrow(() -> new DocumentNotFoundException(id));
	}

}
