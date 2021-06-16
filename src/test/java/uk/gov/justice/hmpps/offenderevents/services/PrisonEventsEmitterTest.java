package uk.gov.justice.hmpps.offenderevents.services;

import com.amazonaws.services.sns.AmazonSNSAsync;
import com.amazonaws.services.sns.model.PublishRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.applicationinsights.TelemetryClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.justice.hmpps.offenderevents.model.OffenderEvent;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class PrisonEventsEmitterTest {

    @Mock
    private AmazonSNSAsync amazonSns;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private PrisonEventsEmitter service;

    @Mock
    private TelemetryClient telemetryClient;

    @Captor
    private ArgumentCaptor<Map<String, String>> telemetryAttributesCaptor;

    @Before
    public void setup() {
        service = new PrisonEventsEmitter(amazonSns, "topicARN",  objectMapper, telemetryClient);
    }

    @Test
    public void testSendEvent() {

        var payload = OffenderEvent.builder()
                .eventType("my-event-type")
                .alertCode("alert-code")
                .bookingId(12345L)
                .build();
        service.sendEvent(payload);

        ArgumentCaptor<PublishRequest> argumentCaptor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(amazonSns).publish(argumentCaptor.capture());
        PublishRequest request = argumentCaptor.getValue();

        assertThat(request.getMessageAttributes().get("eventType").getStringValue()).isEqualTo("my-event-type");
        assertThat(request.getMessageAttributes().get("code").getStringValue()).isEqualTo("alert-code");
        assertThat(request).extracting("message").isEqualTo("{\"eventType\":\"my-event-type\",\"bookingId\":12345,\"alertCode\":\"alert-code\"}");

        verify(telemetryClient).trackEvent(eq("my-event-type"), telemetryAttributesCaptor.capture(), isNull());
        assertThat(telemetryAttributesCaptor.getValue()).containsAllEntriesOf(Map.of("eventType", "my-event-type", "bookingId", "12345", "alertCode", "alert-code"));
    }
}
