package com.securesoc.controller;

import com.securesoc.dto.LaboratoryResponse;
import com.securesoc.service.LaboratoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Backs frontend/src/routes/laboratories.tsx once wired to real data (see
 * FRONTEND_INTEGRATION_AUDIT.md). Read-only, same rationale as
 * DepartmentController. */
@RestController
@RequestMapping("/laboratories")
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
