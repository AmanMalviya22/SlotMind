package com.slotmind.strategy;

import com.slotmind.entity.Appointment;
import com.slotmind.entity.Provider;
import com.slotmind.entity.Slot;
import com.slotmind.enums.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RuleBasedSchedulingStrategyTest {
    private final RuleBasedSchedulingStrategy strategy = new RuleBasedSchedulingStrategy();

    @Test
    void prefersMatchingSpecialtyAndExcludesOfflineOrCancelledCandidates() {
        Instant originalTime = Instant.now().plusSeconds(3600);
        Provider original = new Provider("P1", "Original", Specialty.GENERAL, ProviderStatus.BUSY, 0);
        Provider matching = new Provider("P2", "Matching", Specialty.GENERAL, ProviderStatus.AVAILABLE, 0);
        Provider offline = new Provider("P3", "Offline", Specialty.GENERAL, ProviderStatus.OFFLINE, 0);
        Slot originalSlot = new Slot("S1", original, originalTime, 30, SlotStatus.BOOKED, 0);
        Appointment appointment = new Appointment("A1", "Patient", AppointmentType.ROUTINE, original, originalSlot, AppointmentStatus.RESCHEDULE_PENDING, Instant.now(), Instant.now(), 0);
        Slot cancelled = new Slot("S2", matching, originalTime.plusSeconds(300), 30, SlotStatus.CANCELLED, 0);
        Slot offlineSlot = new Slot("S3", offline, originalTime.plusSeconds(600), 30, SlotStatus.OPEN, 0);
        Slot valid = new Slot("S4", matching, originalTime.plusSeconds(900), 30, SlotStatus.OPEN, 0);

        var recommendation = strategy.recommend(appointment, TriggerReason.PROVIDER_OFFLINE, List.of(cancelled, offlineSlot, valid));

        assertTrue(recommendation.isPresent());
        assertEquals("S4", recommendation.orElseThrow().slot().getId());
        assertEquals("RULE_BASED", recommendation.orElseThrow().source());
    }
}