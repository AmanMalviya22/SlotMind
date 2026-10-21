package com.slotmind.service;

import com.slotmind.entity.Slot;
import com.slotmind.enums.SlotStatus;
import com.slotmind.enums.TriggerReason;
import com.slotmind.event.WorkflowEvent;
import com.slotmind.exception.ApiExceptions.Conflict;
import com.slotmind.exception.ApiExceptions.NotFound;
import com.slotmind.repository.SlotRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SlotService {
    private final SlotRepository repo;
    private final ApplicationEventPublisher events;

    public SlotService(SlotRepository repo, ApplicationEventPublisher events) {
        this.repo = repo;
        this.events = events;
    }

    public List<Slot> list() { return repo.findAll(); }

    @Transactional
    public Slot status(String id, SlotStatus status) {
        Slot slot = repo.lockById(id).orElseThrow(() -> new NotFound("Slot not found"));
        if (status == SlotStatus.OPEN && slot.getStatus() == SlotStatus.CANCELLED) throw new Conflict("Cancelled slot cannot be reopened");
        if (status == SlotStatus.BOOKED && slot.getStatus() != SlotStatus.BOOKED) throw new Conflict("Only the booking workflow can book a slot");
        if (slot.getStatus() == SlotStatus.CANCELLED && status != SlotStatus.CANCELLED) throw new Conflict("Cancelled slot cannot change status");
        slot.setStatus(status);
        Slot saved = repo.save(slot);
        if (status == SlotStatus.CANCELLED) events.publishEvent(new WorkflowEvent(TriggerReason.SLOT_CANCELLED, saved.getId()));
        return saved;
    }
}