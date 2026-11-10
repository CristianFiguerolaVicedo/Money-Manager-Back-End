package com.cristian.moneymanager.controller;

import com.cristian.moneymanager.dto.ImportDtos.ImportRequest;
import com.cristian.moneymanager.dto.ImportDtos.ImportResult;
import com.cristian.moneymanager.service.ImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;

    @PostMapping("/import")
    public ResponseEntity<ImportResult> importRows(@RequestBody ImportRequest request) {
        return ResponseEntity.ok(importService.importRows(request));
    }
}
