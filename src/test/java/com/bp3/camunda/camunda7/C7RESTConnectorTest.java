package com.bp3.camunda.camunda7;

import com.bp3.camunda.camunda7.C7RESTConnector;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.connect.httpclient.HttpConnector;
import org.camunda.connect.httpclient.HttpRequest;
import org.camunda.connect.httpclient.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static com.bp3.camunda.camunda7.C7RESTConnector.PARAM_HTTP_HEADERS;
import static com.bp3.camunda.camunda7.C7RESTConnector.PARAM_HTTP_METHOD;
import static com.bp3.camunda.camunda7.C7RESTConnector.PARAM_HTTP_PAYLOAD;
import static com.bp3.camunda.camunda7.C7RESTConnector.PARAM_HTTP_URL;
import static com.bp3.camunda.camunda7.C7RESTConnector.PARAM_OUTPUT_VARIABLE;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;

class C7RESTConnectorTest {

    @Mock
    HttpConnector httpConnector;
    @Mock
    ExternalTask externalTask;
    @Mock
    ExternalTaskService externalTaskService;
    @Mock
    HttpRequest request;
    @Mock
    HttpResponse response;
    @Mock
    VariableMap variables;

    ObjectMapper mapper;
    C7RESTConnector connector;

    @BeforeEach
    public void init() {
        MockitoAnnotations.openMocks(this);
        Mockito.when(this.httpConnector.createRequest()).thenReturn(this.request);
        Mockito.when(this.request.execute()).thenReturn(this.response);
        Mockito.when(this.response.getResponse()).thenReturn("TEST RESPONSE");
        this.mapper = new ObjectMapper();
        this.mapper.disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES);
        this.mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.mapper.registerModule(new JavaTimeModule());
        this.connector = new C7RESTConnector(this.httpConnector, this.mapper);
    }

    @Test
    void execute() {
        // execute should complete the task successfully
        Mockito.when(this.externalTask.getVariable(PARAM_HTTP_METHOD)).thenReturn("get");
        Mockito.when(this.externalTask.getVariable(PARAM_HTTP_URL)).thenReturn("http://test.com");
        this.connector.execute(this.externalTask, this.externalTaskService);
        Mockito.verify(this.request, never()).payload(any());
        Mockito.verify(this.externalTaskService).complete(any(),any());
    }

    @Test
    void execute_Fail_NoHttpMethod() {
        // HTTP method not specified
        assertThrows(java.lang.AssertionError.class, () -> this.connector.execute(this.externalTask, this.externalTaskService));
    }

    @Test
    void execute_Fail_NoHttpURL() {
        Mockito.when(this.externalTask.getVariable(PARAM_HTTP_METHOD)).thenReturn("GET");
        assertThrows(java.lang.AssertionError.class, () -> this.connector.execute(this.externalTask, this.externalTaskService));
    }

    @Test
    void execute_ParseList() {
        Mockito.when(this.externalTask.getVariable(PARAM_HTTP_METHOD)).thenReturn("GET");
        Mockito.when(this.externalTask.getVariable(PARAM_HTTP_URL)).thenReturn("http://example.com");
        Mockito.when(this.externalTask.getVariable(PARAM_HTTP_HEADERS)).thenReturn("header1=abc; header2=xyz;");
        this.connector.execute(this.externalTask, this.externalTaskService);
        Mockito.verify(request).header("header1","abc");
        Mockito.verify(request).header("header2","xyz");
    }

    @Test
    void execute_Fail_ParseList() {
        // badly formed list of headers... headers should not be setup
        Mockito.when(this.externalTask.getVariable(PARAM_HTTP_METHOD)).thenReturn("GET");
        Mockito.when(this.externalTask.getVariable(PARAM_HTTP_URL)).thenReturn("http://example.com");
        Mockito.when(this.externalTask.getVariable(PARAM_HTTP_HEADERS)).thenReturn("header1");
        this.connector.execute(this.externalTask, this.externalTaskService);
        Mockito.verify(request, never()).header(any(), any());
    }

    @Test
    void execute_ParseJSONMap() {
        Mockito.when(this.externalTask.getVariable(PARAM_HTTP_METHOD)).thenReturn("GET");
        Mockito.when(this.externalTask.getVariable(PARAM_HTTP_URL)).thenReturn("http://example.com");
        Mockito.when(this.externalTask.getVariable(PARAM_HTTP_HEADERS)).thenReturn("{\"header1\": \"abc\", \"header2\": \"xyz\"}");
        this.connector.execute(this.externalTask, this.externalTaskService);
        Mockito.verify(request).header("header1","abc");
        Mockito.verify(request).header("header2","xyz");
    }

    @Test
    void execute_Fail_ParseJSONMap() {
        // throw exception if HTTP headers defined with invalid JSON
        Mockito.when(this.externalTask.getVariable(PARAM_HTTP_METHOD)).thenReturn("GET");
        Mockito.when(this.externalTask.getVariable(PARAM_HTTP_URL)).thenReturn("http://example.com");
        Mockito.when(this.externalTask.getVariable(PARAM_HTTP_HEADERS)).thenReturn("{\"header1\": \"abc, \"header2\": \"xyz\"}");
        assertThrows(RuntimeException.class, () -> this.connector.execute(this.externalTask, this.externalTaskService));
    }

    @Test
    void execute_POST_with_Payload() {
        // execute should complete the task successfully
        String payload = "TEST PAYLOAD";
        Mockito.when(this.externalTask.getVariable(PARAM_HTTP_METHOD)).thenReturn("post");
        Mockito.when(this.externalTask.getVariable(PARAM_HTTP_URL)).thenReturn("http://test.com");
        Mockito.when(this.externalTask.getVariable(PARAM_HTTP_PAYLOAD)).thenReturn(payload);
        this.connector.execute(this.externalTask, this.externalTaskService);
        Mockito.verify(this.request).payload(payload);
        Mockito.verify(this.externalTaskService).complete(any(),any());
    }

    @Test
    void execute_withReturnVariable() {
        // execute should complete the task successfully
        String responseVarName = "response";
        String responseData = "Hello World!";
        MockedStatic<Variables> mockedStatic = Mockito.mockStatic(Variables.class);
        Mockito.when(this.externalTask.getVariable(PARAM_HTTP_METHOD)).thenReturn("get");
        Mockito.when(this.externalTask.getVariable(PARAM_HTTP_URL)).thenReturn("http://test.com");
        Mockito.when(this.externalTask.getVariable(PARAM_OUTPUT_VARIABLE)).thenReturn(responseVarName);
        mockedStatic.when(Variables::createVariables).thenReturn(this.variables);
        Mockito.when(this.response.getResponse()).thenReturn(responseData);
        this.connector.execute(this.externalTask, this.externalTaskService);
        Mockito.verify(this.request, never()).payload(any());
        Mockito.verify(this.variables).putValue(responseVarName, responseData);
        Mockito.verify(this.externalTaskService).complete(any(),any());
    }

}
