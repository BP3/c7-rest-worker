package com.bp3.camunda.camunda7;

import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.camunda.bpm.engine.variable.VariableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static com.bp3.camunda.camunda7.C7RestConnector.PARAM_HTTP_METHOD;
import static com.bp3.camunda.camunda7.C7RestConnector.PARAM_HTTP_PAYLOAD;
import static com.bp3.camunda.camunda7.C7RestConnector.PARAM_HTTP_URL;
import static com.bp3.camunda.camunda7.C7RestConnector.PARAM_OUTPUT_VARIABLE;
import static com.bp3.camunda.camunda7.C7RestConnector.PARAM_STATUS_CODE_VARIABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class C7RestConnectorRealServiceCallTest {
    private ExternalTask externalTask;
    private ExternalTaskService externalTaskService;

    private C7RestConnector connector;

    @BeforeEach
    public void init() {
        externalTaskService = mock(ExternalTaskService.class);
        externalTask = mock(ExternalTask.class);

        connector = new C7RestConnector();
    }

    @Test
    void givenHttpBinStatusServiceCallWhenExecutedStatusCode200IsReturned() {
        when(externalTask.getVariable(PARAM_HTTP_METHOD))
                .thenReturn("GET");

        when(externalTask.getVariable(PARAM_HTTP_URL))
                .thenReturn("http://httpbin.org/status/200");

        when(externalTask.getVariable(PARAM_STATUS_CODE_VARIABLE))
                .thenReturn("statusCode");

        connector.execute(externalTask, externalTaskService);

        ArgumentCaptor<VariableMap> variablesCaptor = ArgumentCaptor.forClass(VariableMap.class);
        verify(externalTaskService).complete(eq(externalTask), variablesCaptor.capture());

        VariableMap variables = variablesCaptor.getValue();
        assertTrue(variables.containsKey("statusCode"));
        assertEquals(200, variables.get("statusCode"));
    }

    @Test
    void givenHttpBinEchoServiceCallWhenExecutedPayloadIsReturned() {
        when(externalTask.getVariable(PARAM_HTTP_METHOD))
                .thenReturn("POST");

        when(externalTask.getVariable(PARAM_HTTP_URL))
                .thenReturn("http://httpbin.org/anything");

        String uuid = UUID.randomUUID().toString();
        when(externalTask.getVariable(PARAM_HTTP_PAYLOAD))
                .thenReturn(uuid);

        when(externalTask.getVariable(PARAM_OUTPUT_VARIABLE))
                .thenReturn("result");

        connector.execute(externalTask, externalTaskService);

        ArgumentCaptor<VariableMap> variablesCaptor = ArgumentCaptor.forClass(VariableMap.class);
        verify(externalTaskService).complete(eq(externalTask), variablesCaptor.capture());

        VariableMap variables = variablesCaptor.getValue();
        assertTrue(variables.containsKey("result"));
        assertTrue(variables.getValue("result", String.class).contains(uuid));
    }
}
