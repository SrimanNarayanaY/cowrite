package com.sriman.cowrite.permission;

import com.sriman.cowrite.document.Document;
import com.sriman.cowrite.document.DocumentRepository;
import com.sriman.cowrite.permission.dto.ShareRequest;
import com.sriman.cowrite.permission.dto.ShareResponse;
import com.sriman.cowrite.permission.exception.CannotShareWithSelfException;
import com.sriman.cowrite.user.User;
import com.sriman.cowrite.user.UserRepository;
import com.sriman.cowrite.user.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DocumentSharingService {

	private final PermissionService permissionService;
	private final DocumentPermissionRepository documentPermissionRepository;
	private final DocumentRepository documentRepository;
	private final UserRepository userRepository;

	/**
	 * Only the owner may share. Re-sharing with the same email updates the role (idempotent upsert).
	 */
	@Transactional
	public ShareResponse share(Long documentId, Long ownerId, ShareRequest request) {
		permissionService.requireOwner(documentId, ownerId);

		String email = request.email().trim().toLowerCase(Locale.ROOT);
		User target = userRepository.findByEmail(email)
				.orElseThrow(() -> new UserNotFoundException(email));

		if (target.getId().equals(ownerId)) {
			throw new CannotShareWithSelfException();
		}

		DocumentPermission permission = documentPermissionRepository
				.findByDocumentIdAndUserId(documentId, target.getId())
				.orElseGet(() -> newPermission(documentId, target));

		permission.setRole(request.role());
		documentPermissionRepository.save(permission);

		return new ShareResponse(documentId, target.getId(), target.getEmail(), permission.getRole());
	}

	private DocumentPermission newPermission(Long documentId, User target) {
		DocumentPermission permission = new DocumentPermission();
		permission.setDocument(documentRepository.getReferenceById(documentId));
		permission.setUser(target);
		return permission;
	}

}
