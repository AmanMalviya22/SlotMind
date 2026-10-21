package com.slotmind.service;

import com.slotmind.entity.*;
import com.slotmind.enums.*;
import com.slotmind.exception.ApiExceptions.*;
import com.slotmind.repository.*;
import com.slotmind.strategy.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SchedulingService {
    private final SlotRepository slots;
    private final SuggestionRepository suggestions;
    private final SchedulingStrategy rule;
    private final AppointmentRepository appointments;

    public SchedulingService(SlotRepository slots, SuggestionRepository suggestions, SchedulingStrategy rule, AppointmentRepository appointments) {
        this.slots = slots;
        this.suggestions = suggestions;
        this.rule = rule;
        this.appointments = appointments;
    }

    @Transactional
    public Optional<RescheduleSuggestion> generate(Appointment appointment, TriggerReason reason) {
        Appointment lockedAppointment = appointments.lockById(appointment.getId()).orElseThrow(() -> new NotFound("Appointment not found"));
        if (lockedAppointment.getStatus() == AppointmentStatus.RESCHEDULED || lockedAppointment.getStatus() == AppointmentStatus.COMPLETED) return Optional.empty();
        if (suggestions.existsByAppointmentAndTriggerReasonAndStatus(lockedAppointment, reason, SuggestionStatus.PENDING)) return Optional.empty();
        Set<String> proposed = suggestions.findByStatusOrderByCreatedAtDesc(SuggestionStatus.PENDING).stream().map(x -> x.getRecommendedSlot().getId()).collect(Collectors.toSet());
        List<Slot> candidates = slots.findByStatusAndStartTimeAfterOrderByStartTimeAsc(SlotStatus.OPEN, Instant.now()).stream().filter(x -> !proposed.contains(x.getId())).toList();
        Optional<SchedulingStrategy.Recommendation> recommendation = rule.recommend(lockedAppointment, reason, candidates);
        if (recommendation.isEmpty()) return Optional.empty();
        Slot selected = recommendation.get().slot();
        if (selected.getStatus() != SlotStatus.OPEN || selected.getStartTime().isBefore(Instant.now()) || selected.getProvider().getStatus() == ProviderStatus.OFFLINE) return Optional.empty();
        RescheduleSuggestion suggestion = new RescheduleSuggestion(UUID.randomUUID().toString(), lockedAppointment, selected, selected.getProvider(), lockedAppointment.getProvider().getName(), lockedAppointment.getSlot().getStartTime(), recommendation.get().confidence(), recommendation.get().reasoning(), recommendation.get().source(), SuggestionStatus.PENDING, reason, Instant.now(), 0);
        return Optional.of(suggestions.save(suggestion));
    }

    @Transactional
    public void processAutomatic(String appointmentId, TriggerReason reason) {
        Appointment appointment = appointments.lockById(appointmentId).orElseThrow(() -> new NotFound("Appointment not found"));
        if (appointment.getStatus() == AppointmentStatus.CONFIRMED) {
            appointment.setStatus(AppointmentStatus.RESCHEDULE_PENDING);
            appointments.save(appointment);
        }
        generate(appointment, reason);
    }

    public List<RescheduleSuggestion> list(SuggestionStatus status) { return status == null ? suggestions.findAll() : suggestions.findByStatusOrderByCreatedAtDesc(status); }

    @Transactional
    public RescheduleSuggestion decide(String id, String action) {
        if (!action.equals("ACCEPT") && !action.equals("REJECT")) throw new IllegalArgumentException("Action must be ACCEPT or REJECT");
        RescheduleSuggestion selected = suggestions.lockById(id).orElseThrow(() -> new NotFound("Suggestion not found"));
        Appointment appointment = appointments.lockById(selected.getAppointment().getId()).orElseThrow(() -> new NotFound("Appointment not found"));
        selected = suggestions.lockById(id).orElseThrow(() -> new NotFound("Suggestion not found"));
        if (selected.getStatus() != SuggestionStatus.PENDING) throw new Conflict("Suggestion is already resolved");
        if (action.equals("REJECT")) { selected.setStatus(SuggestionStatus.REJECTED); return suggestions.save(selected); }
        if (appointment.getStatus() == AppointmentStatus.RESCHEDULED || appointment.getStatus() == AppointmentStatus.COMPLETED) throw new Conflict("Appointment is already finalized");
        Slot destination = slots.lockById(selected.getRecommendedSlot().getId()).orElseThrow(() -> new NotFound("Destination slot not found"));
        if (destination.getStatus() != SlotStatus.OPEN || destination.getStartTime().isBefore(Instant.now()) || destination.getProvider().getStatus() == ProviderStatus.OFFLINE) throw new Conflict("Recommended slot is no longer available");
        Slot old = appointment.getSlot();
        if (old.getStatus() != SlotStatus.CANCELLED) old.setStatus(SlotStatus.OPEN);
        destination.setStatus(SlotStatus.BOOKED);
        appointment.setSlot(destination);
        appointment.setProvider(destination.getProvider());
        appointment.setStatus(AppointmentStatus.RESCHEDULED);
        selected.setStatus(SuggestionStatus.ACCEPTED);
        String selectedId = selected.getId();
        suggestions.findByAppointmentAndStatus(appointment, SuggestionStatus.PENDING).forEach(other -> { if (!other.getId().equals(selectedId)) other.setStatus(SuggestionStatus.REJECTED); });
        return suggestions.save(selected);
    }
}