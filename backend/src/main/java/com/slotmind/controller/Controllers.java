package com.slotmind.controller;
import com.slotmind.dto.Dtos.*; import com.slotmind.entity.*; import com.slotmind.enums.*; import com.slotmind.service.*; import jakarta.validation.Valid; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api") public class Controllers { final AppointmentService as; final ProviderService ps; final SlotService ss; final SchedulingService gs; final MappingService map; public Controllers(AppointmentService a,ProviderService p,SlotService s,SchedulingService g,MappingService m){as=a;ps=p;ss=s;gs=g;map=m;}
 @PostMapping("/appointments") public AppointmentView create(@Valid @RequestBody CreateAppointment r){return map.appointment(as.create(r));}
 @GetMapping("/appointments") public List<AppointmentView> appointments(@RequestParam(required=false) AppointmentStatus status){return as.list(status).stream().map(map::appointment).toList();}
 @GetMapping("/providers") public List<ProviderView> providers(){return ps.list().stream().map(map::provider).toList();}
 @PatchMapping("/providers/{id}/status") public ProviderView providerStatus(@PathVariable String id,@Valid @RequestBody StatusRequest r){return map.provider(ps.status(id,parseProviderStatus(r.status())));}
 @GetMapping("/slots") public List<SlotView> slots(){return ss.list().stream().map(map::slot).toList();}
 @PatchMapping("/slots/{id}/status") public SlotView slotStatus(@PathVariable String id,@Valid @RequestBody StatusRequest r){return map.slot(ss.status(id,parseSlotStatus(r.status())));}
 @PostMapping("/appointments/{id}/suggest") public SuggestionView suggest(@PathVariable String id){Appointment a=as.findById(id);return gs.generate(a,TriggerReason.INITIAL).map(map::suggestion).orElseThrow(()->new com.slotmind.exception.ApiExceptions.Conflict("No suitable slot or pending suggestion already exists"));}
 @GetMapping("/suggestions") public List<SuggestionView> suggestions(@RequestParam(required=false) SuggestionStatus status){return gs.list(status).stream().map(map::suggestion).toList();}
 @PatchMapping("/suggestions/{id}") public SuggestionView decide(@PathVariable String id,@Valid @RequestBody SuggestionAction r){return map.suggestion(gs.decide(id,r.action().toUpperCase()));}
 private ProviderStatus parseProviderStatus(String value){try{return ProviderStatus.valueOf(value.trim().toUpperCase());}catch(Exception e){throw new IllegalArgumentException("Status must be AVAILABLE, BUSY, or OFFLINE");}}
 private SlotStatus parseSlotStatus(String value){try{return SlotStatus.valueOf(value.trim().toUpperCase());}catch(Exception e){throw new IllegalArgumentException("Status must be OPEN, BOOKED, or CANCELLED");}}
}