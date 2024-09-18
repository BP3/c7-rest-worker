package com.bp3.camunda.camunda7;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.connect.httpclient.HttpConnector;
import org.camunda.connect.httpclient.HttpRequest;
import org.camunda.connect.httpclient.HttpResponse;
import org.camunda.connect.httpclient.impl.HttpConnectorImpl;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

@Component
@ExternalTaskSubscription("bp3-http-json")
@Slf4j
public class C7RESTConnector implements ExternalTaskHandler {

    public static final String PARAM_HTTP_METHOD = "httpMethod";
    public static final String PARAM_HTTP_URL = "httpURL";
    public static final String PARAM_HTTP_HEADERS = "httpHeaders";
    public static final String PARAM_HTTP_PARAMETERS = "httpQueryParams";
    public static final String PARAM_HTTP_PAYLOAD = "httpPayload";
    public static final String PARAM_OUTPUT_VARIABLE = "httpOutParameter";

    private final HttpConnector httpConnector;
    private final ObjectMapper mapper;

    public C7RESTConnector() {
        this.httpConnector = new HttpConnectorImpl();
        this.mapper = new ObjectMapper();
        this.mapper.disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES);
        this.mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.mapper.registerModule(new JavaTimeModule());
    }

    public C7RESTConnector(HttpConnector httpConnector, ObjectMapper mapper) {
        this.httpConnector = httpConnector;
        this.mapper = mapper;
    }

    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        log.debug("EXECUTE EXTERNAL TASK: {} / {}", externalTask.getActivityId(), externalTask.getExecutionId());
        log.debug("ALL VARIABLES: {}", externalTask.getAllVariables());

        // extract the configuration from the external task variables
        String httpMethod = (String) getVariable(externalTask, PARAM_HTTP_METHOD, String.class);
        String httpURL = (String) getVariable(externalTask, PARAM_HTTP_URL, String.class);
        String httpPayload = (String) getVariable(externalTask, PARAM_HTTP_PAYLOAD, String.class);
        Map<String, String> httpHeaders = (Map<String, String>) getVariable(externalTask, PARAM_HTTP_HEADERS, Map.class);
        Map<String, String> httpQueryParams = (Map<String, String>) getVariable(externalTask, PARAM_HTTP_PARAMETERS, Map.class);
        String outputVariableName = (String) getVariable(externalTask, PARAM_OUTPUT_VARIABLE, String.class);

        // validate configuration...
        assert httpMethod != null : "HTTP method must not be null";
        assert httpURL != null : "HTTP URL must not be null";

        httpMethod = httpMethod.toUpperCase();

        // setup the REST request
        HttpRequest request = this.httpConnector.createRequest();
        request.url(httpURL);
        request.method(httpMethod);
        setHeaders(request, httpHeaders);
        setQueryParams(request, httpQueryParams);
        setPayload(request, httpMethod, httpPayload);

        // call the REST service
        HttpResponse response = request.execute();

        // set the output variable
        log.debug("RESPONSE: {}", response.getResponse());
        VariableMap variables = Variables.createVariables();
        if (outputVariableName != null) {
            variables.putValue(outputVariableName, response.getResponse());
        }

        // complete the external task
        externalTaskService.complete(externalTask, variables);

        log.debug("EXTERNAL TASK EXECUTED: {} / {}", externalTask.getActivityId(), externalTask.getExecutionId());
    }

    private void setPayload(HttpRequest request, String httpMethod, String httpPayload) {
        if (httpPayload == null) {
            return;
        }
        // payload is only allowed for certain HTTP methods
        if (httpMethod.equals("POST") || httpMethod.equals("PUT") || httpMethod.equals("PATCH")) {
            request.payload(httpPayload);
        }
    }

    private void setQueryParams(HttpRequest request, Map<String, String> httpQueryParams) {
        if (httpQueryParams == null) {
            return;
        }
        for (String key : httpQueryParams.keySet()) {
            Object value = httpQueryParams.get(key);
            request.setRequestParameter(key, value);
        }
    }

    private void setHeaders(HttpRequest request, Map<String, String> httpHeaders) {
        if (httpHeaders == null) {
            return;
        }
        for (String key : httpHeaders.keySet()) {
            String value = httpHeaders.get(key);
            request.header(StringUtils.trimAllWhitespace(key), value);
        }
    }

    private Object getVariable(ExternalTask externalTask, String paramName, Class<?> clazz) {
        String var = externalTask.getVariable(paramName);
        if (var == null) {
            // variable not supplied
            return null;
        }
        if (clazz.equals(String.class)) {
            return var;
        }
        if (clazz.equals(Integer.class)) {
            return Integer.parseInt(var);
        }
        if (clazz.equals(Map.class)) {
            if (var.startsWith("{")) {
                // map specified as a JSON object
                try {
                    return this.mapper.readValue(new BufferedReader(new StringReader(var)), Map.class);
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            else {
                Map<String,String> map = new HashMap<>();
                String[] keyValuePairs = var.split(";");
                for (String kvp : keyValuePairs) {
                    String[] parts = kvp.split("=");
                    if (parts.length == 2) {
                        map.put(parts[0], parts[1]);
                    }
                }
                return map;
            }
        }
        // unsupported variable type... return null
        return null;
    }

}
