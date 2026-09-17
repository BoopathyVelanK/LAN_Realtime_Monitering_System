package com.securesoc.controller;

import com.securesoc.dto.DepartmentResponse;
import com.securesoc.security.SecurityUserDetails;
import com.securesoc.service.DepartmentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Backs frontend/src/routes/departments.tsx once wired to real data (see
 * FRONTEND_INTEGRATION_AUDIT.md). Read-only: no create/update/delete yet,
 * since no admin UI exists for that either - matches EndpointController's
 * read-only scope.
 *
 * Faculty scope (which departments a given Faculty caller may see) is
 * enforced in DepartmentService via FacultyScopeService - this class only
 * gates the coarse role check and resolves the authenticated caller. */
@RestController
@RequestMapping("/departments")
@PreAuthorize("hasAnyRole('ADMIN','FACULTY')")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    public List<DepartmentResponse> listDepartments(@AuthenticationPrincipal SecurityUserDetails userDetails) {
        return departmentService.listAll(userDetails.getId());
    }
}
