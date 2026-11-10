package com.cristian.moneymanager.controller;

import com.cristian.moneymanager.dto.GoalDtos.ContributionRequest;
import com.cristian.moneymanager.dto.GoalDtos.GoalDto;
import com.cristian.moneymanager.dto.GoalDtos.GoalRequest;
import com.cristian.moneymanager.service.GoalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/goals")
public class GoalController {

    private final GoalService goalService;

    @GetMapping
    public ResponseEntity<List<GoalDto>> list() {
        return ResponseEntity.ok(goalService.list());
    }

    @PostMapping
    public ResponseEntity<GoalDto> create(@RequestBody GoalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(goalService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GoalDto> update(@PathVariable Long id, @RequestBody GoalRequest request) {
        return ResponseEntity.ok(goalService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        goalService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/contributions")
    public ResponseEntity<GoalDto> addContribution(@PathVariable Long id, @RequestBody ContributionRequest request) {
        return ResponseEntity.ok(goalService.addContribution(id, request));
    }

    @DeleteMapping("/{id}/contributions/{contributionId}")
    public ResponseEntity<GoalDto> deleteContribution(@PathVariable Long id, @PathVariable Long contributionId) {
        return ResponseEntity.ok(goalService.deleteContribution(id, contributionId));
    }
}
