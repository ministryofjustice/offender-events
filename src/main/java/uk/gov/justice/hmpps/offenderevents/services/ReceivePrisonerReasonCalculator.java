package uk.gov.justice.hmpps.offenderevents.services;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
public class ReceivePrisonerReasonCalculator {
    private final PrisonApiService prisonApiService;
    private final CommunityApiService communityApiService;

    public ReceivePrisonerReasonCalculator(PrisonApiService prisonApiService, CommunityApiService communityApiService) {
        this.prisonApiService = prisonApiService;
        this.communityApiService = communityApiService;
    }

    public RecallReason calculateMostLikelyReasonForPrisonerReceive(String offenderNumber) {
        final var prisonerDetails = prisonApiService.getPrisonerDetails(offenderNumber);
        final var details = String.format("%s:%s", prisonerDetails.status(), prisonerDetails.statusReason());
        final var currentLocation = prisonerDetails.currentLocation();
        final var currentPrisonStatus = prisonerDetails.currentPrisonStatus();

        // TODO move to switch for better code reuse
        if (prisonerDetails.typeOfMovement() == MovementType.TEMPORARY_ABSENCE) {
            return new RecallReason(Reason.TEMPORARY_ABSENCE_RETURN, Source.PRISON, details, currentLocation, currentPrisonStatus);
        }
        if (prisonerDetails.typeOfMovement() == MovementType.COURT) {
            return new RecallReason(Reason.RETURN_FROM_COURT, Source.PRISON, details, currentLocation, currentPrisonStatus);
        }
        if (prisonerDetails.typeOfMovement() == MovementType.ADMISSION && prisonerDetails.movementReason() == MovementReason.TRANSFER) {
            return new RecallReason(Reason.TRANSFERRED, Source.PRISON, details, currentLocation, currentPrisonStatus);
        }
        if (prisonerDetails.typeOfMovement() == MovementType.ADMISSION && prisonerDetails.movementReason() == MovementReason.RECALL) {
            return new RecallReason(Reason.RECALL, Source.PRISON, details, currentLocation, currentPrisonStatus);
        }
        if (prisonerDetails.recall()) {
            return new RecallReason(Reason.RECALL, Source.PRISON, details, currentLocation, currentPrisonStatus);
        }

        final Optional<ReasonWithDetailsAndSource> maybeRecallStatusFromProbation = switch (prisonerDetails.legalStatus()) {
            case OTHER, UNKNOWN, CONVICTED_UNSENTENCED, SENTENCED, INDETERMINATE_SENTENCE -> calculateReasonForPrisonerFromProbationOrEmpty(offenderNumber, details);
            default -> Optional.empty();
        };

        final var reasonWithSourceAndDetails = maybeRecallStatusFromProbation.orElseGet(() -> {
            final var reason = switch (prisonerDetails.legalStatus()) {
                case RECALL -> Reason.RECALL;
                case CIVIL_PRISONER, CONVICTED_UNSENTENCED, SENTENCED, INDETERMINATE_SENTENCE -> Reason.CONVICTED;
                case IMMIGRATION_DETAINEE -> Reason.IMMIGRATION_DETAINEE;
                case REMAND -> Reason.REMAND;
                case DEAD, OTHER, UNKNOWN -> Reason.UNKNOWN;
            };
            return new ReasonWithDetailsAndSource(reason, Source.PRISON, details);
        });

        return new RecallReason(reasonWithSourceAndDetails.reason(),
            reasonWithSourceAndDetails.source(),
            reasonWithSourceAndDetails.details(),
            currentLocation,
            currentPrisonStatus);
    }

    private Optional<ReasonWithDetailsAndSource> calculateReasonForPrisonerFromProbationOrEmpty(String offenderNumber, String details) {
        final var maybeRecallList = communityApiService.getRecalls(offenderNumber);
        return maybeRecallList
            .filter(recalls -> recalls.stream().anyMatch(this::hasActiveOrCompletedRecall))
            .map(recalls -> new ReasonWithDetailsAndSource(Reason.RECALL,
                Source.PROBATION,
                String.format("%s Recall referral date %s", details, latestRecallReferralDate(maybeRecallList.get()))));
    }

    private String latestRecallReferralDate(List<Recall> recalls) {
        return recalls
            .stream()
            .filter(this::hasActiveOrCompletedRecall)
            .map(Recall::referralDate)
            .filter(Objects::nonNull)
            .max(LocalDate::compareTo)
            .map(LocalDate::toString)
            .orElse("unknown");
    }

    private boolean hasActiveOrCompletedRecall(Recall recall) {
        if (recall.outcomeRecall() != null) {
            return recall.outcomeRecall();
        }

        if (recall.recallRejectedOrWithdrawn() != null) {
            return !recall.recallRejectedOrWithdrawn();
        }
        return false;
    }

    enum Reason {
        RECALL,
        REMAND,
        CONVICTED,
        IMMIGRATION_DETAINEE,
        UNKNOWN,
        TEMPORARY_ABSENCE_RETURN,
        RETURN_FROM_COURT,
        TRANSFERRED
    }

    enum Source {
        PRISON,
        PROBATION
    }

    record ReasonWithDetailsAndSource(Reason reason, Source source, String details) {

    }

    record RecallReason(Reason reason, Source source, String details, CurrentLocation currentLocation,
                        CurrentPrisonStatus currentPrisonStatus) {
        public RecallReason(Reason reason, Source source, CurrentLocation currentLocation, CurrentPrisonStatus currentPrisonStatus) {
            this(reason, source, null, currentLocation, currentPrisonStatus);
        }
    }
}
