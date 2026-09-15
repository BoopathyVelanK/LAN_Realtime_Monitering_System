package com.securesoc.controller;

import com.securesoc.dto.LaboratoryResponse;
import com.securesoc.service.LaboratoryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Backs frontend/src/routes/laboratories.tsx once wired to real data (see
 * FRONTEND_INTEGRATION_AUDIT.md). Read-only, same rationale as
 * DepartmentController.
 *
 * Phase-1 role gate only (Admin/Faculty, not agents) - this list is lab
 * names only, not scoped to a Faculty caller's assigned labs. Scoping
 * this to "assigned labs only" is a known remaining gap; see the RBAC
 * audit report rather than a TODO here. */
@RestController
@RequestMapping("/laboratories")
@PreAuthorize("hasAnyRole('ADMIN','FACULTY')")
public class LaboratoryController {

    private final LaboratoryService laboratoryService;

    public LaboratoryController(LaboratoryService laboratoryService) {
        this.laboratoryService = laboratoryService;
    }

    @GetMapping
    public List<LaboratoryResponse> listLaboratories() {
        return laboratoryService.listAll();
    }
}
