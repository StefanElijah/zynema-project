package dev.zynema.common.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zynema.events.UserEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxSerializerTest {

    private final OutboxSerializer serializer =
        new OutboxSerializer(new ObjectMapper().findAndRegisterModules());

    private final UserEvent.RoleGranted payload =
        new UserEvent.RoleGranted(UUID.randomUUID(), UUID.randomUUID(), "subscriber", Instant.now());

    @Test
    void roundTripsARegisteredPayloadThroughItsType() {
        String json = serializer.write(payload);

        Object read = serializer.read("user.role-granted", json);

        assertThat(read).isInstanceOf(UserEvent.RoleGranted.class).isEqualTo(payload);
    }

    @Test
    void readsStoredStateByClass() {
        String json = serializer.write(payload);

        assertThat(serializer.read(json, UserEvent.RoleGranted.class)).isEqualTo(payload);
    }

    @Test
    void refusesAnUnregisteredType() {
        assertThatThrownBy(() -> serializer.read("user.nope", "{}"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("user.nope");
    }

    @Test
    void refusesAPayloadThatIsNotJson() {
        assertThatThrownBy(() -> serializer.read("{not-json", UserEvent.RoleGranted.class))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("RoleGranted");
    }

    @Test
    void refusesToSerialiseSomethingJacksonCannot() {
        assertThatThrownBy(() -> serializer.write(new Object()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("outbox payload");
    }
}
