package uk.gov.justice.hmpps.offenderevents.model;

import lombok.*;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "POLL_AUDIT")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"pollName", "nextStartTime", "numberOfRecords"})
@ToString
public class PollAudit {

    @Id
    private String pollName;

    @Column(name = "NEXT_RUN_TIME")
    private LocalDateTime nextStartTime;

    private int numberOfRecords;

}
