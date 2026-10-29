package com.sriman.cowrite.document;

import com.sriman.cowrite.common.web.PageResponse;
import com.sriman.cowrite.document.dto.DocumentRequest;
import com.sriman.cowrite.document.dto.DocumentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

	private final DocumentService documentService;

	@PostMapping
	public ResponseEntity<DocumentResponse> create(@Valid @RequestBody DocumentRequest request) {
		DocumentResponse created = documentService.create(request);

		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(created.id())
				.toUri();

		return ResponseEntity.created(location).body(created);
	}

	/**
	 * GET /api/documents?page=0&size=10&sort=createdAt,desc
	 */
	@GetMapping
	public PageResponse<DocumentResponse> list(
			@PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return documentService.list(pageable);
	}

	@GetMapping("/{id}")
	public DocumentResponse getById(@PathVariable Long id) {
		return documentService.getById(id);
	}

	@PutMapping("/{id}")
	public DocumentResponse update(@PathVariable Long id, @Valid @RequestBody DocumentRequest request) {
		return documentService.update(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		documentService.delete(id);

		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}

}
