package com.sriman.cowrite.permission;

import com.sriman.cowrite.permission.dto.ShareRequest;
import com.sriman.cowrite.permission.dto.ShareResponse;
import com.sriman.cowrite.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents/{documentId}/share")
@RequiredArgsConstructor
public class DocumentSharingController {

	private final DocumentSharingService documentSharingService;

	@PostMapping
	public ShareResponse share(@AuthenticationPrincipal User currentUser,
							   @PathVariable Long documentId,
							   @Valid @RequestBody ShareRequest request) {
		return documentSharingService.share(documentId, currentUser.getId(), request);
	}

}
