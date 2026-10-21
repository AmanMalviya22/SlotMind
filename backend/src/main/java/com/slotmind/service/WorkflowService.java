package com.slotmind.service;

import com.slotmind.entity.Provider;
import com.slotmind.entity.Slot;
import com.slotmind.enums.AppointmentStatus;
import com.slotmind.enums.TriggerReason;
import com.slotmind.repository.AppointmentRepository;
import com.slotmind.repository.SlotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class WorkflowService {
    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);
    private final AppointmentRepository appointments;
    private final SchedulingService scheduling;
    private final SlotRepository slots;

    public WorkflowService(AppointmentRepository appointments, SchedulingService scheduling, SlotRepository slots) {
        this.appointments = appointments;
        this.scheduling = scheduling;
        this.slots = slots;
    }

    @Async
    public void providerOffline(Provider provider) {
        appointments.findByProviderAndStatus(provider, AppointmentStatus.CONFIRMED)
                .forEach(appointment -> processSafely(appointment.getId(), TriggerReason.PROVIDER_OFFLINE,
                        "provider-offline", provider.getId()));
    }

    @Async
    public void slotCancelled(String slotId) {
        try {
            slots.findById(slotId)
                    .flatMap(appointments::findBySlot)
                    .filter(appointment -> appointment.getStatus() == AppointmentStatus.CONFIRMED)
                    .ifPresent(appointment -> processSafely(appointment.getId(), TriggerReason.SLOT_CANCELLED,
                            "slot-cancelled", slotId));
        } catch (RuntimeException e) {
            log.error("Cancelled-slot lookup failed for {}: {}", slotId, e.getClass().getSimpleName(), e);
        }
    }

    private void processSafely(String appointmentId, TriggerReason reason, String workflow, String subjectId) {
        try {
            scheduling.processAutomatic(appointmentId, reason);
        } catch (RuntimeException e) {
            log.error("{} rescheduling failed for {} (appointment {}): {}", workflow, subjectId, appointmentId,
                    e.getClass().getSimpleName(), e);
        }
    }
}