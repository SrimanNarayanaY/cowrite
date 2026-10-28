package com.sriman.cowrite.document;

import com.sriman.cowrite.document.dto.DocumentRequest;
import com.sriman.cowrite.document.dto.DocumentResponse;
import com.sriman.cowrite.document.exception.DocumentNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentService {

	private final DocumentRepository documentRepository;

	@Transactional
	public DocumentResponse create(DocumentRequest request) {
		Document document = new Document();
		document.setTitle(request.title());
		document.setContent(request.content());

		return DocumentResponse.from(documentRepository.save(document));
	}

	@Transactional(readOnly = true)
	public List<DocumentResponse> list() {
		return documentRepository.findAll()
				.stream()
				.map(DocumentResponse::from)
				.toList();
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
		documentRepository.delete(document);
	}

	private Document findDocument(Long id) {
		return documentRepository.findById(id)
				.orElseThrow(() -> new DocumentNotFoundException(id));
	}

}
