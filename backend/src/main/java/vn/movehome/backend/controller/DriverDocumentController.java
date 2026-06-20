package vn.movehome.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.movehome.backend.dto.driver.DriverDocumentResponse;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.service.DriverDocumentService;

import java.util.List;

@RestController
@RequestMapping("/api/driver/documents")
@PreAuthorize("hasRole('DRIVER')")
@RequiredArgsConstructor
public class DriverDocumentController {

    private final DriverDocumentService driverDocumentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DriverDocumentResponse upload(
            @AuthenticationPrincipal User currentUser,
            @RequestParam("file") MultipartFile file,
            @RequestParam("doc_type") String docType
    ) {
        return driverDocumentService.upload(currentUser.getId(), docType, file);
    }

    @GetMapping
    public List<DriverDocumentResponse> getMyDocuments(@AuthenticationPrincipal User currentUser) {
        return driverDocumentService.getMyDocuments(currentUser.getId());
    }
}
