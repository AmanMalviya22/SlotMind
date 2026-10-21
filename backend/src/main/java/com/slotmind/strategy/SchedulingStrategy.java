package com.slotmind.strategy;
import com.slotmind.entity.*; import com.slotmind.enums.*; import java.util.*;
public interface SchedulingStrategy { Optional<Recommendation> recommend(Appointment appointment, TriggerReason reason, List<Slot> candidates); record Recommendation(Slot slot,double confidence,String reasoning,String source) {} }