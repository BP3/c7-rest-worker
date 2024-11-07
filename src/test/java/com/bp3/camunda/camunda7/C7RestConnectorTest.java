package com.bp3.camunda.camunda7;

import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.connect.ConnectorException;
import org.camunda.connect.httpclient.HttpConnector;
import org.camunda.connect.httpclient.HttpRequest;
import org.camunda.connect.httpclient.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Set;

import static com.bp3.camunda.camunda7.C7RestConnector.ERROR_METHOD_BPMN_ERROR;
import static com.bp3.camunda.camunda7.C7RestConnector.PARAM_ERROR_HANDLING_METHOD;
import static com.bp3.camunda.camunda7.C7RestConnector.PARAM_HTTP_HEADERS;
import static com.bp3.camunda.camunda7.C7RestConnector.PARAM_HTTP_METHOD;
import static com.bp3.camunda.camunda7.C7RestConnector.PARAM_HTTP_PARAMETERS;
import static com.bp3.camunda.camunda7.C7RestConnector.PARAM_HTTP_PAYLOAD;
import static com.bp3.camunda.camunda7.C7RestConnector.PARAM_HTTP_URL;
import static com.bp3.camunda.camunda7.C7RestConnector.PARAM_OUTPUT_VARIABLE;
import static com.bp3.camunda.camunda7.C7RestConnector.PARAM_RETRIES;
import static com.bp3.camunda.camunda7.C7RestConnector.PARAM_RETRY_BACKOFF;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class C7RestConnectorTest {
    private ExternalTask externalTask;
    private ExternalTaskService externalTaskService;
    private HttpRequest request;
    private HttpResponse response;

    private C7RestConnector connector;

    @BeforeEach
    public void init() {
        response = mock(HttpResponse.class);
        when(response.getResponse())
                .thenReturn("TEST RESPONSE");

        request = mock(HttpRequest.class);
        when(request.execute())
                .thenReturn(response);
        when(request.url(any()))
                .thenReturn(request);
        when(request.method(any()))
                .thenReturn(request);

        HttpConnector httpConnector = mock(HttpConnector.class);
        when(httpConnector.createRequest())
                .thenReturn(request);

        externalTaskService = mock(ExternalTaskService.class);
        externalTask = mock(ExternalTask.class);
        when(externalTask.getVariable(PARAM_HTTP_URL))
                .thenReturn("http://example.com");
        when(externalTask.getRetries())
                .thenReturn(null);

        connector = new C7RestConnector(httpConnector);
    }

    @Test
    void executeShouldCompleteTheTaskSuccessfully() {
        connector.execute(externalTask, externalTaskService);

        verify(request, never()).payload(any());
        verify(request).execute();
        verify(externalTaskService).complete(any(), any());
    }

    @Test
    void givenNoHttpMethodSpecifiedWhenExecutedThenMethodDefaultsToGet() {
        connector.execute(externalTask, externalTaskService);

        verify(request).method("GET");
        verify(request).url("http://example.com");
        verify(request).execute();
    }

    @Test
    void givenNoHttpUrlSpecifiedWhenExecutedThenHandledAsBpmnError() {
        when(externalTask.getVariable(PARAM_HTTP_URL))
                .thenReturn(null);

        when(externalTask.getVariable(PARAM_ERROR_HANDLING_METHOD))
                .thenReturn(ERROR_METHOD_BPMN_ERROR);

        connector.execute(externalTask, externalTaskService);

        verify(externalTaskService, times(1))
                .handleBpmnError(externalTask, "CONNECTOR_ERROR", "HTTP URL must not be null");
    }

    @Test
    void givenHttpHeadersSpecifiedAsKeyValuePairsWhenExecutedThenHeadersAreCorrectlySet() {
        when(externalTask.getVariable(PARAM_HTTP_HEADERS))
                .thenReturn("header1=abc");

        connector.execute(externalTask, externalTaskService);

        verify(request).header("header1", "abc");
    }

    @Test
    void givenHttpHeadersSpecifiedWithSpacesInNameWhenExecutedThenHeadersAreSetWithSpacesRemoved() {
        when(externalTask.getVariable(PARAM_HTTP_HEADERS))
                .thenReturn("header 1=abc");

        connector.execute(externalTask, externalTaskService);

        verify(request).header("header1", "abc");
    }

    @Test
    void givenHttpRequestParamsSpecifiedWhenExecutedThenParamsAreCorrectlySet() {
        when(externalTask.getVariable(PARAM_HTTP_PARAMETERS))
                .thenReturn("{\"param1\":\"abc\"}");

        connector.execute(externalTask, externalTaskService);

        verify(request).setRequestParameter("param1", "abc");
    }

    @Test
    void givenValidJsonWhenConvertedToMapThenAllValuesAreSet() {
        Map<String, String> map = connector.asMap("{\"key1\": \"value1\", \"key2\": \"value2\"}");

        assertEquals(Set.of("key1", "key2"), map.keySet());
        assertEquals("value1", map.get("key1"));
        assertEquals("value2", map.get("key2"));
    }

    @Test
    void givenKeyValuePairsWhenConvertedToMapThenAllValuesAreSet() {
        Map<String, String> map = connector.asMap("key1=value1;key2=value2;");

        assertEquals(Set.of("key1", "key2"), map.keySet());
        assertEquals("value1", map.get("key1"));
        assertEquals("value2", map.get("key2"));
    }

    @Test
    void givenKeyValuePairsWhenConvertedToMapThenAllValuesAreSetWithWhitespaceRemoved() {
        Map<String, String> map = connector.asMap("\tkey1\t=\tvalue1\t;  key2  =  value2  ;");

        assertEquals(Set.of("key1", "key2"), map.keySet());
        assertEquals("value1", map.get("key1"));
        assertEquals("value2", map.get("key2"));
    }

    @Test
    void givenInvalidJsonWhenConvertedToMapThenAnExceptionIsThrown() {
        assertThrows(RuntimeException.class, () -> connector.asMap("{\"key1}"));
    }

    @Test
    void givenInvalidDataWhenConvertedToMapThenAnExceptionIsThrown() {
        assertThrows(RuntimeException.class, () -> connector.asMap("invalidData"));
    }

    @Test
    void givenHttpMethodIsPostAndAPayloadIsProvidedWhenExecutedThenThePayloadIsSetOnTheRequest() {
        String payload = "TEST PAYLOAD";
        when(externalTask.getVariable(PARAM_HTTP_METHOD))
                .thenReturn("POST");
        when(externalTask.getVariable(PARAM_HTTP_PAYLOAD))
                .thenReturn(payload);

        connector.execute(externalTask, externalTaskService);

        verify(request).payload(payload);
        verify(externalTaskService).complete(any(), any());
    }

    @Test
    void givenHttpMethodIsGetAndAPayloadIsProvidedWhenExecutedThenThePayloadIsNotSetOnTheRequest() {
        when(externalTask.getVariable(PARAM_HTTP_METHOD))
                .thenReturn("GET");
        when(externalTask.getVariable(PARAM_HTTP_PAYLOAD))
                .thenReturn("TEST PAYLOAD");

        connector.execute(externalTask, externalTaskService);

        verify(request, never()).payload(any());
        verify(request).execute();
        verify(externalTaskService).complete(any(), any());
    }

    @Test
    void givenAnOutputVariableNameIsProvidedWhenExecutedThenResultIsStoredInTheVariable() {
        String responseVarName = "response";
        when(externalTask.getVariable(PARAM_OUTPUT_VARIABLE))
                .thenReturn(responseVarName);
        String responseData = "Hello World!";
        when(response.getResponse())
                .thenReturn(responseData);

        connector.execute(externalTask, externalTaskService);

        verify(request, never()).payload(any());
        ArgumentCaptor<VariableMap> variablesCaptor = ArgumentCaptor.forClass(VariableMap.class);
        verify(externalTaskService).complete(any(), variablesCaptor.capture());
        VariableMap variables = variablesCaptor.getValue();
        assertEquals(responseData, variables.getValue(responseVarName, String.class));
    }

    @Test
    void givenErrorMethodBpmnErrorWhenExecutedAConnectorExceptionOccursThenABpmnErrorWithCodeConnectorErrorIsThrown() {
        when(externalTask.getVariable(PARAM_ERROR_HANDLING_METHOD))
                .thenReturn(C7RestConnector.ERROR_METHOD_BPMN_ERROR);
        when(request.execute())
                .thenThrow(new ConnectorException("test"));

        connector.execute(externalTask, externalTaskService);

        verify(externalTaskService, never()).complete(any(), any());
        verify(externalTaskService).handleBpmnError(externalTask, "CONNECTOR_ERROR", "test");
    }

    @Test
    void givenErrorMethodFailureWhenExecutedAConnectorExceptionOccursThenABpmnErrorWithCodeConnectorErrorIsThrown() {
        when(externalTask.getVariable(PARAM_ERROR_HANDLING_METHOD))
                .thenReturn(C7RestConnector.ERROR_METHOD_FAILURE);
        when(request.execute())
                .thenThrow(new ConnectorException("errorDetails"));

        connector.execute(externalTask, externalTaskService);

        verify(externalTaskService, never()).complete(any(), any());
        verify(externalTaskService).handleFailure(externalTask, "HTTP request failed", "errorDetails", 0, 0L);
    }

    @Test
    void givenErrorMethodFailureAndRetriesSetWhenExecutedAndAConnectorExceptionOccursThenABpmnErrorIsHandled() {
        when(externalTask.getVariable(PARAM_ERROR_HANDLING_METHOD))
                .thenReturn(C7RestConnector.ERROR_METHOD_FAILURE);
        when(externalTask.getVariable(PARAM_RETRIES))
                .thenReturn("1");
        when(request.execute())
                .thenThrow(new ConnectorException("errorDetails"));

        connector.execute(externalTask, externalTaskService);

        verify(externalTaskService, never()).complete(any(), any());
        verify(externalTaskService).handleFailure(externalTask, "HTTP request failed", "errorDetails", 1, 0L);
    }

    @Test
    void givenErrorMethodFailureAndRetryTimeoutSetWhenExecutedAndAConnectorExceptionOccursThenAFailureIsHandled() {
        when(externalTask.getVariable(PARAM_ERROR_HANDLING_METHOD))
                .thenReturn(C7RestConnector.ERROR_METHOD_FAILURE);
        when(externalTask.getVariable(PARAM_RETRY_BACKOFF))
                .thenReturn("1");
        when(request.execute())
                .thenThrow(new ConnectorException("errorDetails"));

        connector.execute(externalTask, externalTaskService);

        verify(externalTaskService, never()).complete(any(), any());
        verify(externalTaskService).handleFailure(externalTask, "HTTP request failed", "errorDetails", 0, 1000);
    }

    @Test
    void givenErrorMethodFailureAndPreviousRetryWhenExecutedAndAConnectorExceptionOccursThenAFailureIsHandled() {
        when(externalTask.getVariable(PARAM_ERROR_HANDLING_METHOD))
                .thenReturn(C7RestConnector.ERROR_METHOD_FAILURE);
        when(externalTask.getRetries())
                .thenReturn(2);
        when(request.execute())
                .thenThrow(new ConnectorException("errorDetails"));

        connector.execute(externalTask, externalTaskService);

        verify(externalTaskService, never()).complete(any(), any());
        verify(externalTaskService).handleFailure(externalTask, "HTTP request failed", "errorDetails", 1, 0);
    }

    @Test
    void givenErrorMethodNotDefinedWhenExecutedAndAConnectorExceptionOccursThenTheExceptionIsIgnored() {
        when(externalTask.getVariable(PARAM_ERROR_HANDLING_METHOD))
                .thenReturn(null);
        when(request.execute())
                .thenThrow(new ConnectorException("test"));

        connector.execute(externalTask, externalTaskService);

        verify(externalTaskService, never()).complete(any(), any());
    }

    @Test
    void givenErrorMethodNotRecognizedWhenExecutedAndAConnectorExceptionOccursThenTheExceptionIsIgnored() {
        when(externalTask.getVariable(PARAM_ERROR_HANDLING_METHOD))
                .thenReturn("test");
        when(request.execute())
                .thenThrow(new ConnectorException("test"));

        connector.execute(externalTask, externalTaskService);

        verify(externalTaskService, never()).complete(any(), any());
    }
}
