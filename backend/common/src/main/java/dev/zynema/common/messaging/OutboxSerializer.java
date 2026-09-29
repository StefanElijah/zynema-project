package dev.zynema.common.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zynema.events.EventTypes;

/**
 * JSON conversion for the outbox, in one place so the recorder and the relay
 * cannot disagree about how a stored payload looks.
 */
public class OutboxSerializer {

    private final ObjectMapper objectMapper;

    public OutboxSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialise the outbox payload", ex);
        }
    }

    public Object read(String type, String json) {
        try {
            return objectMapper.readValue(json, EventTypes.classOf(type));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not read an outbox payload of type " + type, ex);
        }
    }
}
