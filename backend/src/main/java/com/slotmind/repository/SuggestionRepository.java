package com.slotmind.repository;

import com.slotmind.entity.*;
import com.slotmind.enums.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface SuggestionRepository extends JpaRepository<RescheduleSuggestion, String> {
    List<RescheduleSuggestion> findByStatusOrderByCreatedAtDesc(SuggestionStatus status);
    boolean existsByAppointmentAndTriggerReasonAndStatus(Appointment appointment, TriggerReason reason, SuggestionStatus status);
    List<RescheduleSuggestion> findByAppointmentAndStatus(Appointment appointment, SuggestionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RescheduleSuggestion s where s.id = :id")
    Optional<RescheduleSuggestion> lockById(String id);
}