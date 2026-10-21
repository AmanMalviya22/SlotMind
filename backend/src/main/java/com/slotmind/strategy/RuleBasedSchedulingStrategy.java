package com.slotmind.strategy;
import com.slotmind.entity.*; import com.slotmind.enums.*; import org.springframework.stereotype.Component; import java.util.*;
@Component public class RuleBasedSchedulingStrategy implements SchedulingStrategy {
 public Optional<Recommendation> recommend(Appointment a,TriggerReason reason,List<Slot> candidates){
  List<Slot> eligible=candidates.stream().filter(s->s.getStatus()==SlotStatus.OPEN && s.getProvider().getStatus()!=ProviderStatus.OFFLINE).filter(s->reason!=TriggerReason.PROVIDER_OFFLINE || !s.getProvider().getId().equals(a.getProvider().getId())).sorted(Comparator.comparing(Slot::getStartTime).thenComparing(Slot::getId)).toList();
  if(reason==TriggerReason.SLOT_CANCELLED){ Optional<Slot> same=eligible.stream().filter(s->s.getProvider().getId().equals(a.getProvider().getId())).findFirst(); if(same.isPresent()) return Optional.of(result(a,same.get(),"same provider")); }
  Optional<Slot> specialty=eligible.stream().filter(s->s.getProvider().getSpecialty()==a.getProvider().getSpecialty()).findFirst();
  return specialty.map(s->result(a,s,"matching specialty")).or(()->eligible.stream().findFirst().map(s->result(a,s,"earliest available slot")));
 }
 private Recommendation result(Appointment a,Slot s,String why){return new Recommendation(s,0.92,"We found the "+why+" next available appointment with "+s.getProvider().getName()+".","RULE_BASED");}
}