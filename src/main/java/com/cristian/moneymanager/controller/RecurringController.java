package com.cristian.moneymanager.controller;

import com.cristian.moneymanager.dto.RecurringDto;
import com.cristian.moneymanager.service.RecurringService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/recurring")
public class RecurringController {

    private final RecurringService recurringService;

    /** ?type=income|expense to filter. */
    @GetMapping
    public ResponseEntity<List<RecurringDto>> list(@RequestParam(required = false) String type) {
        return ResponseEntity.ok(recurringService.list(type));
    }

    @PostMapping
    public ResponseEntity<RecurringDto> create(@RequestBody RecurringDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(recurringService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RecurringDto> update(@PathVariable Long id, @RequestBody RecurringDto request) {
        return ResponseEntity.ok(recurringService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        recurringService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
