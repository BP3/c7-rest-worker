package com.bp3.camunda.camunda7;

import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests;
import org.camunda.bpm.engine.test.junit5.ProcessEngineExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.mock.mockito.SpyBean;

import static java.util.Map.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@Deployment(resources = { "bpmn/rest-worker-test.bpmn" })
@ExtendWith(ProcessEngineExtension.class)
public class ProcessIntegrationTest extends BpmnAwareTests {
    private static final String PROCESS_DEFINITION = "Process_RESTWorkerTest";
    private static final String BUSINESS_KEY = "1";

    @SpyBean
    private C7RestConnector c7RestConnector;

    @Test
    public void processStartsAndFinishes() {
        ProcessInstance processInstance = runtimeService().startProcessInstanceByKey(PROCESS_DEFINITION, BUSINESS_KEY);

        assertThat(processInstance)
                .hasPassed("StartEvent")
                .hasPassed("Activity_Status200");
        assertThat(processInstance).variables().contains(entry("statusCode", 200));
        assertThat(processInstance).isEnded();

        verify(c7RestConnector).execute(any(), any());
    }
}
