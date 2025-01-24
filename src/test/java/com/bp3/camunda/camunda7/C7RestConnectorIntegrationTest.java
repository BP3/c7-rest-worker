package com.bp3.camunda.camunda7;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = { "REST_TOPIC_NAME=my-test-topic" })
class C7RestConnectorIntegrationTest {
    @Autowired
    private C7RestConnector connector;

    @Test
    void customTopicNamePropagatesToSubscription() {
        assertEquals("my-test-topic", connector.topicSubscription.getTopicName());
    }
}
