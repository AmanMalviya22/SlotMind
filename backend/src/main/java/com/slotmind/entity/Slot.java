package com.slotmind.entity;
import com.slotmind.enums.*; import jakarta.persistence.*; import java.time.Instant;
@Entity
public class Slot { @Id private String id; @ManyToOne(fetch=FetchType.EAGER,optional=false) private Provider provider; @Column(nullable=false) private Instant startTime; private int durationMinutes; @Enumerated(EnumType.STRING) private SlotStatus status; @Version private long version;
 public Slot(){} public Slot(String id,Provider p,Instant t,int d,SlotStatus s,long v){this.id=id;provider=p;startTime=t;durationMinutes=d;status=s;version=v;} public String getId(){return id;} public Provider getProvider(){return provider;} public Instant getStartTime(){return startTime;} public int getDurationMinutes(){return durationMinutes;} public SlotStatus getStatus(){return status;} public void setStatus(SlotStatus s){status=s;}
}