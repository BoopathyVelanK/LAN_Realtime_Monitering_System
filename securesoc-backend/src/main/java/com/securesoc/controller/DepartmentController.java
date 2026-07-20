package com.securesoc.controller;

import com.securesoc.dto.DepartmentResponse;
import com.securesoc.service.DepartmentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Backs frontend/src/routes/departments.tsx once wired to real data (see
 * FRONTEND_INTEGRATION_AUDIT.md). Read-only: no create/update/delete yet,
 * since no admin UI exists for that either - matches EndpointController's
 * read-only scope. */
@RestController
@RequestMapping("/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    public List<DepartmentResponse> listDepartments() {
        return departmentService.listAll();
    }
}
