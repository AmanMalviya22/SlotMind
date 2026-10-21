package com.slotmind.strategy;

import com.slotmind.entity.Appointment;
import com.slotmind.entity.Slot;
import com.slotmind.enums.ProviderStatus;
import com.slotmind.enums.SlotStatus;
import com.slotmind.enums.TriggerReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Primary
@Component
public class AISchedulingStrategy implements SchedulingStrategy {
    private static final Logger log = LoggerFactory.getLogger(AISchedulingStrategy.class);
    private final LLMGateway gateway;
    private final RuleBasedSchedulingStrategy fallback;

    public AISchedulingStrategy(LLMGateway gateway, RuleBasedSchedulingStrategy fallback) {
        this.gateway = gateway;
        this.fallback = fallback;
    }

    @Override
    public Optional<Recommendation> recommend(Appointment appointment, TriggerReason reason, List<Slot> candidates) {
        try {
            Optional<LLMGateway.Recommendation> answer = gateway.recommend(appointment, reason, candidates);
            if (answer.isPresent()) {
                Optional<Recommendation> validated = validate(answer.get(), appointment, reason, candidates);
                if (validated.isPresent()) return validated;
                log.warn("LLM returned an invalid scheduling recommendation for {} with {} candidates; using rule-based fallback",
                        reason, candidates.size());
            }
        } catch (RuntimeException e) {
            log.warn("LLM recommendation could not be used; using rule-based fallback: {}", e.getClass().getSimpleName());
        }
        return fallback.recommend(appointment, reason, candidates);
    }

    private Optional<Recommendation> validate(LLMGateway.Recommendation answer, Appointment appointment, TriggerReason reason, List<Slot> candidates) {
        if (answer.slotId() == null || answer.slotId().isBlank() || !Double.isFinite(answer.confidence()) || answer.confidence() < 0 || answer.confidence() > 1 || answer.reasoning() == null || answer.reasoning().isBlank() || answer.reasoning().length() > 1000) return Optional.empty();
        Optional<Slot> selected = candidates.stream().filter(slot -> slot.getId().equals(answer.slotId())).findFirst();
        if (selected.isEmpty()) return Optional.empty();
        Slot slot = selected.get();
        if (slot.getStatus() != SlotStatus.OPEN || slot.getStartTime().isBefore(Instant.now()) || slot.getProvider().getStatus() == ProviderStatus.OFFLINE) return Optional.empty();
        if (reason == TriggerReason.PROVIDER_OFFLINE && slot.getProvider().getId().equals(appointment.getProvider().getId())) return Optional.empty();
        String providerId = slot.getProvider().getId();
        if (answer.alternateProviderId() != null && !answer.alternateProviderId().equals(providerId)) return Optional.empty();
        return Optional.of(new Recommendation(slot, answer.confidence(), answer.reasoning(), "AI"));
    }
}