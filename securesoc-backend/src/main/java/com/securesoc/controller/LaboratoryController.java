package com.securesoc.controller;

import com.securesoc.dto.LaboratoryResponse;
import com.securesoc.security.SecurityUserDetails;
import com.securesoc.service.LaboratoryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Backs frontend/src/routes/laboratories.tsx once wired to real data (see
 * FRONTEND_INTEGRATION_AUDIT.md). Read-only, same rationale as
 * DepartmentController.
 *
 * Faculty scope (which laboratories a given Faculty caller may see) is
 * enforced in LaboratoryService via FacultyScopeService - this class only
 * gates the coarse role check and resolves the authenticated caller. */
@RestController
@RequestMapping("/laboratories")
@PreAuthorize("hasAnyRole('ADMIN','FACULTY')")
public class LaboratoryController {

    private final LaboratoryService laboratoryService;

    public LaboratoryController(LaboratoryService laboratoryService) {
        this.laboratoryService = laboratoryService;
    }

    @GetMapping
    public List<LaboratoryResponse> listLaboratories(@AuthenticationPrincipal SecurityUserDetails userDetails) {
        return laboratoryService.listAll(userDetails.getId());
    }
}
