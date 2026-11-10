package com.cristian.moneymanager.service;

import com.cristian.moneymanager.dto.GoalDtos.ContributionDto;
import com.cristian.moneymanager.dto.GoalDtos.ContributionRequest;
import com.cristian.moneymanager.dto.GoalDtos.GoalDto;
import com.cristian.moneymanager.dto.GoalDtos.GoalRequest;
import com.cristian.moneymanager.entity.GoalContributionEntity;
import com.cristian.moneymanager.entity.GoalEntity;
import com.cristian.moneymanager.entity.ProfileEntity;
import com.cristian.moneymanager.repository.GoalContributionRepository;
import com.cristian.moneymanager.repository.GoalRepository;
import com.cristian.moneymanager.util.MonthParam;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Savings goals (F14): a target amount, optional deadline, and contributions/withdrawals. */
@Service
@RequiredArgsConstructor
public class GoalService {

    static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000");
    static final int MAX_GOALS = 50;
    static final int RECENT_CONTRIBUTIONS = 10;

    private final ProfileService profileService;
    private final GoalRepository goalRepository;
    private final GoalContributionRepository contributionRepository;

    @Transactional(readOnly = true)
    public List<GoalDto> list() {
        Long profileId = profileService.getCurrentProfile().getId();
        LocalDate today = today();
        return goalRepository.findByProfileIdOrderByCreatedAtAsc(profileId).stream()
                .map(goal -> toDto(goal, today))
                .toList();
    }

    @Transactional
    public GoalDto create(GoalRequest request) {
        ProfileEntity profile = profileService.getCurrentProfile();
        if (goalRepository.countByProfileId(profile.getId()) >= MAX_GOALS) {
            throw badRequest("Has llegado al máximo de " + MAX_GOALS + " objetivos");
        }
        GoalEntity goal = GoalEntity.builder().profile(profile).build();
        apply(goal, request);
        return toDto(goalRepository.save(goal), today());
    }

    @Transactional
    public GoalDto update(Long id, GoalRequest request) {
        GoalEntity goal = find(id);
        apply(goal, request);
        return toDto(goalRepository.save(goal), today());
    }

    @Transactional
    public void delete(Long id) {
        GoalEntity goal = find(id);
        contributionRepository.deleteByGoalId(goal.getId());
        goalRepository.delete(goal);
    }

    /** Positive amount = money saved; negative = money taken out (never below 0 saved). */
    @Transactional
    public GoalDto addContribution(Long goalId, ContributionRequest request) {
        GoalEntity goal = find(goalId);
        if (request == null || request.amount() == null || request.amount().signum() == 0) {
            throw badRequest("Escribe un importe distinto de 0");
        }
        BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);
        if (amount.abs().compareTo(MAX_AMOUNT) > 0) {
            throw badRequest("El importe es demasiado grande");
        }
        BigDecimal saved = contributionRepository.sumByGoalId(goal.getId());
        if (saved.add(amount).signum() < 0) {
            throw badRequest("No puedes retirar más de lo ahorrado (" + saved.setScale(2, RoundingMode.HALF_UP).toPlainString() + " €)");
        }
        LocalDate date = request.date() != null ? request.date() : today();
        if (date.isAfter(today().plusDays(1))) {
            throw badRequest("La fecha no puede ser futura");
        }
        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();
        if (note != null && note.length() > 255) {
            throw badRequest("La nota es demasiado larga");
        }
        contributionRepository.save(GoalContributionEntity.builder().goal(goal).amount(amount).date(date).note(note).build());
        return toDto(goal, today());
    }

    @Transactional
    public GoalDto deleteContribution(Long goalId, Long contributionId) {
        GoalEntity goal = find(goalId);
        GoalContributionEntity contribution = contributionRepository.findByIdAndGoalId(contributionId, goal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aportación no encontrada"));
        BigDecimal savedAfter = contributionRepository.sumByGoalId(goal.getId()).subtract(contribution.getAmount());
        if (savedAfter.signum() < 0) {
            throw badRequest("No se puede borrar: lo ahorrado quedaría en negativo. Borra antes las retiradas.");
        }
        contributionRepository.delete(contribution);
        return toDto(goal, today());
    }

    // ------------------------------------------------------------------ helpers

    private GoalEntity find(Long id) {
        Long profileId = profileService.getCurrentProfile().getId();
        return goalRepository.findByIdAndProfileId(id, profileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Objetivo no encontrado"));
    }

    private static void apply(GoalEntity goal, GoalRequest request) {
        if (request == null) {
            throw badRequest("Faltan los datos del objetivo");
        }
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty()) {
            throw badRequest("Ponle un nombre al objetivo");
        }
        if (name.length() > 100) {
            throw badRequest("El nombre es demasiado largo");
        }
        BigDecimal target = request.targetAmount();
        if (target == null || target.signum() <= 0) {
            throw badRequest("El objetivo debe ser un importe mayor que 0");
        }
        if (target.compareTo(MAX_AMOUNT) > 0) {
            throw badRequest("El importe es demasiado grande");
        }
        goal.setName(name);
        goal.setIcon(request.icon() == null || request.icon().isBlank() ? null : request.icon());
        goal.setTargetAmount(target.setScale(2, RoundingMode.HALF_UP));
        goal.setDeadline(request.deadline());
    }

    private GoalDto toDto(GoalEntity goal, LocalDate today) {
        BigDecimal saved = contributionRepository.sumByGoalId(goal.getId()).setScale(2, RoundingMode.HALF_UP);
        List<ContributionDto> recent = contributionRepository.findByGoalIdOrderByDateDescIdDesc(goal.getId()).stream()
                .limit(RECENT_CONTRIBUTIONS)
                .map(c -> new ContributionDto(c.getId(), c.getAmount(), c.getDate(), c.getNote()))
                .toList();
        return progress(goal, saved, recent, today);
    }

    /** Pure progress maths (unit-tested). */
    static GoalDto progress(GoalEntity goal, BigDecimal saved, List<ContributionDto> recent, LocalDate today) {
        BigDecimal target = goal.getTargetAmount();
        BigDecimal remaining = target.subtract(saved).max(BigDecimal.ZERO);
        boolean achieved = saved.compareTo(target) >= 0;
        int percent = saved.multiply(BigDecimal.valueOf(100)).divide(target, 0, RoundingMode.DOWN).intValue();

        Integer monthsLeft = null;
        BigDecimal monthlyNeeded = null;
        if (goal.getDeadline() != null) {
            if (goal.getDeadline().isBefore(today)) {
                monthsLeft = 0;
            } else {
                // Months still available, counting the current one
                monthsLeft = (int) ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(goal.getDeadline())) + 1;
                if (!achieved) {
                    monthlyNeeded = remaining.divide(BigDecimal.valueOf(monthsLeft), 2, RoundingMode.UP);
                }
            }
        }
        return new GoalDto(goal.getId(), goal.getName(), goal.getIcon(), target, saved, remaining, percent,
                goal.getDeadline(), monthsLeft, monthlyNeeded, achieved, recent);
    }

    private static LocalDate today() {
        return LocalDate.now(MonthParam.APP_ZONE);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
