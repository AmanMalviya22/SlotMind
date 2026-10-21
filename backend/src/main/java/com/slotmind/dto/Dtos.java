package com.slotmind.dto;
import com.slotmind.enums.*; import jakarta.validation.constraints.*; import java.time.Instant;
public final class Dtos { private Dtos() {} 
 public record CreateAppointment(@NotBlank String patientName,@NotNull AppointmentType type,@NotBlank String providerId,@NotBlank String slotId) {}
 public record StatusRequest(@NotNull String status) {}
 public record SuggestionAction(@NotNull String action) {}
 public record ProviderView(String id,String name,Specialty specialty,ProviderStatus status) {}
 public record SlotView(String id,String providerId,String providerName,Instant startTime,int durationMinutes,SlotStatus status) {}
 public record AppointmentView(String id,String patientName,AppointmentType type,String providerId,String providerName,String slotId,Instant startTime,AppointmentStatus status) {}
 public record SuggestionView(String id,String appointmentId,String patientName,String originalProviderName,Instant originalTime,String slotId,String providerName,Instant recommendedTime,double confidence,String reasoning,SuggestionStatus status,TriggerReason triggerReason,String source) {}
}