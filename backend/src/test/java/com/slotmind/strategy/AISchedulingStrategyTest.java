package com.slotmind.strategy;

import com.slotmind.entity.Appointment;
import com.slotmind.entity.Provider;
import com.slotmind.entity.Slot;
import com.slotmind.enums.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AISchedulingStrategyTest {
    @Test
    void usesValidatedAiRecommendationAndIncludesTriggerSpecificPromptData() {
        Fixture fixture = fixture();
        FakeGateway gateway = new FakeGateway(new LLMGateway.Recommendation("S2", "P2", .88, "Same specialty is available."));
        Optional<SchedulingStrategy.Recommendation> result = new AISchedulingStrategy(gateway, new RuleBasedSchedulingStrategy())
                .recommend(fixture.appointment(), TriggerReason.PROVIDER_OFFLINE, List.of(fixture.candidate()));

        assertTrue(result.isPresent());
        assertEquals("S2", result.orElseThrow().slot().getId());
        assertEquals("AI", result.orElseThrow().source());
        assertTrue(gateway.prompt.contains("PROVIDER_OFFLINE"));
        assertTrue(gateway.prompt.contains("Do not recommend that provider"));
        assertTrue(gateway.prompt.contains("slotId=S2"));
    }

    @Test
    void fallsBackWhenAiSelectsAnUnknownSlot() {
        Fixture fixture = fixture();
        FakeGateway gateway = new FakeGateway(new LLMGateway.Recommendation("UNKNOWN", "P2", .88, "Invented."));
        Optional<SchedulingStrategy.Recommendation> result = new AISchedulingStrategy(gateway, new RuleBasedSchedulingStrategy())
                .recommend(fixture.appointment(), TriggerReason.SLOT_CANCELLED, List.of(fixture.candidate()));

        assertTrue(result.isPresent());
        assertEquals("S2", result.orElseThrow().slot().getId());
        assertEquals("RULE_BASED", result.orElseThrow().source());
    }

    private static Fixture fixture() {
        Instant time = Instant.now().plusSeconds(3600);
        Provider original = new Provider("P1", "Original", Specialty.GENERAL, ProviderStatus.OFFLINE, 0);
        Provider alternate = new Provider("P2", "Alternate", Specialty.GENERAL, ProviderStatus.AVAILABLE, 0);
        Slot originalSlot = new Slot("S1", original, time, 30, SlotStatus.BOOKED, 0);
        Slot candidate = new Slot("S2", alternate, time.plusSeconds(600), 30, SlotStatus.OPEN, 0);
        return new Fixture(new Appointment("A1", "Patient", AppointmentType.ROUTINE, original, originalSlot, AppointmentStatus.RESCHEDULE_PENDING, Instant.now(), Instant.now(), 0), candidate);
    }

    private record Fixture(Appointment appointment, Slot candidate) {}

    private static class FakeGateway implements LLMGateway {
        private final Recommendation response;
        private String prompt = "";
        private FakeGateway(Recommendation response) { this.response = response; }
        @Override public Optional<Recommendation> recommend(Appointment appointment, TriggerReason reason, List<Slot> candidates) {
            prompt = LiteLLMGateway.prompt(appointment, reason, candidates);
            return Optional.of(response);
        }
    }
}