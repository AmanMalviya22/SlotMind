# Architecture Decision Record

## 1. Scheduling logic
**Context:** Controllers and asynchronous workers need identical scheduling behavior. **Decision:** Keep it behind `SchedulingStrategy` and `SchedulingService`. **Tradeoff:** More classes than embedding rules in a controller, but rules remain testable and replaceable.

## 2. Strategy selection
**Context:** AI may be unavailable. **Decision:** Attempt the configured company LiteLLM-compatible gateway through `AISchedulingStrategy`, validate every response against application candidates, and use the rule-based strategy as the reliable fallback. **Tradeoff:** External recommendations add latency and failure modes, so no AI response can finalize a booking.

## 3. Atomic acceptance
**Context:** Two coordinators can compete for one slot. **Decision:** Accept uses one transaction and pessimistic locks for suggestion, appointment, and destination slot, then revalidates state. **Tradeoff:** Locked rows reduce concurrency briefly but preserve booking integrity.

## 4. Automatic workflow
**Context:** Provider events can affect multiple appointments. **Decision:** Status endpoints persist quickly and delegate processing to Spring `@Async` workers; pending suggestions are checked before creation. **Tradeoff:** The UI polls rather than receiving push events.

## 5. Extensibility
Room/equipment constraints can be added as another `SchedulingStrategy`. Authentication, notifications, waitlists, billing, and patient portals are intentionally deferred from this sprint.