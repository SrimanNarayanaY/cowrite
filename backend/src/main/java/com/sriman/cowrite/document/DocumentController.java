package com.sriman.cowrite.document;

import com.sriman.cowrite.common.web.PageResponse;
import com.sriman.cowrite.document.dto.DocumentRequest;
import com.sriman.cowrite.document.dto.DocumentResponse;
import com.sriman.cowrite.permission.PermissionService;
import com.sriman.cowrite.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
	private final PermissionService permissionService;

	@PostMapping
	public ResponseEntity<DocumentResponse> create(@AuthenticationPrincipal User currentUser,
												   @Valid @RequestBody DocumentRequest request) {
		DocumentResponse created = documentService.create(request, currentUser.getId());

		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(created.id())
				.toUri();

		return ResponseEntity.created(location).body(created);
	}

	/**
	 * GET /api/documents?page=0&size=10&sort=createdAt,desc — owned plus shared documents.
	 */
	@GetMapping
	public PageResponse<DocumentResponse> list(
			@AuthenticationPrincipal User currentUser,
			@PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return documentService.listAccessible(currentUser.getId(), pageable);
	}

	@GetMapping("/{id}")
	public DocumentResponse getById(@AuthenticationPrincipal User currentUser, @PathVariable Long id) {
		permissionService.requireView(id, currentUser.getId());

		return documentService.getById(id);
	}

	@PutMapping("/{id}")
	public DocumentResponse update(@AuthenticationPrincipal User currentUser,
								   @PathVariable Long id,
								   @Valid @RequestBody DocumentRequest request) {
		permissionService.requireEdit(id, currentUser.getId());

		return documentService.update(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@AuthenticationPrincipal User currentUser, @PathVariable Long id) {
		permissionService.requireOwner(id, currentUser.getId());
		documentService.delete(id);

		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}

}
