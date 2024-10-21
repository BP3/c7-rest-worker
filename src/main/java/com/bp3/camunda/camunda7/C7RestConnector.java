package com.bp3.camunda.camunda7;

import com.fasterxml.jackson.core.type.TypeReference;
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
import org.camunda.connect.ConnectorException;
import org.camunda.connect.httpclient.HttpConnector;
import org.camunda.connect.httpclient.HttpRequest;
import org.camunda.connect.httpclient.HttpResponse;
import org.camunda.connect.httpclient.impl.HttpConnectorImpl;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
// TODO this doesn't seem like the best topic name. Maybe "bp3-rest-connector"?
@ExternalTaskSubscription("bp3-http-json")
@Slf4j
public final class C7RestConnector implements ExternalTaskHandler {
    static final String PARAM_HTTP_METHOD = "httpMethod";
    static final String PARAM_HTTP_URL = "httpURL";
    static final String PARAM_HTTP_HEADERS = "httpHeaders";
    static final String PARAM_HTTP_PARAMETERS = "httpQueryParams";
    static final String PARAM_HTTP_PAYLOAD = "httpPayload";
    static final String PARAM_OUTPUT_VARIABLE = "httpOutParameter";
    static final String PARAM_ERROR_HANDLING_METHOD = "errorHandlingMethod";
    static final String PARAM_RETRIES = "retries";
    static final String PARAM_RETRY_BACKOFF = "retryBackoff";
    static final String ERROR_METHOD_BPMN_ERROR = "BPMNError";
    static final String ERROR_METHOD_FAILURE = "Failure";

    private static final Set<String> PAYLOAD_VALID_FOR_METHODS = Set.of("POST", "PUT", "PATCH");

    private final HttpConnector httpConnector;
    private final ObjectMapper mapper;

    public C7RestConnector() {
        this(new HttpConnectorImpl());
    }

    public C7RestConnector(final HttpConnector httpConnector) {
        this.httpConnector = httpConnector;
        this.mapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .registerModule(new JavaTimeModule());
    }

    @Override
    public void execute(final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
        log.debug("EXECUTE EXTERNAL TASK: {} / {}", externalTask.getActivityId(), externalTask.getExecutionId());
        log.debug("ALL VARIABLES: {}", externalTask.getAllVariables());

        String httpMethod = externalTask.getVariable(PARAM_HTTP_METHOD);
        if (httpMethod == null || httpMethod.isBlank()) {
            httpMethod = "GET";
        }
        String httpURL = externalTask.getVariable(PARAM_HTTP_URL);
        if (httpURL == null || httpURL.isBlank()) {
            throw new RuntimeException("HTTP URL must not be null");
        }

        HttpRequest request = httpConnector.createRequest()
                .url(httpURL)
                .method(httpMethod);
        setHeaders(request, asMap(externalTask.getVariable(PARAM_HTTP_HEADERS)));
        setQueryParams(request, asMap(externalTask.getVariable(PARAM_HTTP_PARAMETERS)));
        setPayload(request, httpMethod, externalTask.getVariable(PARAM_HTTP_PAYLOAD));

        // call the REST service
        try {
            HttpResponse response = request.execute();

            // set the output variable
            log.debug("RESPONSE: {}", response.getResponse());
            VariableMap variables = Variables.createVariables();
            String outputVariableName = externalTask.getVariable(PARAM_OUTPUT_VARIABLE);
            if (outputVariableName != null) {
                variables.putValue(outputVariableName, response.getResponse());
            }

            // complete the external task
            externalTaskService.complete(externalTask, variables);
        } catch (ConnectorException e) {
            log.debug("CONNECTOR_ERROR", e);

            String errorHandlingMethod = externalTask.getVariable(PARAM_ERROR_HANDLING_METHOD);

            if (errorHandlingMethod != null) {
                log.debug("CONNECTOR_ERROR: Handling as '{}'", errorHandlingMethod);

                switch (errorHandlingMethod) {
                    // TODO should the user be able to provide the errorCode?
                    case ERROR_METHOD_BPMN_ERROR -> externalTaskService.handleBpmnError(externalTask, "CONNECTOR_ERROR",
                            e.getLocalizedMessage());
                    case ERROR_METHOD_FAILURE -> {
                        int retriesLeft;
                        Integer retries = externalTask.getRetries();
                        if (retries == null) {
                            String retriesParam = externalTask.getVariable(PARAM_RETRIES);
                            if (retriesParam == null) {
                                retriesLeft = 0; // Default to zero if not set
                            } else {
                                retriesLeft = Integer.parseInt(retriesParam);
                            }
                        } else {
                            retriesLeft = retries - 1;
                        }

                        long retryBackoff; // Default to zero if not set
                        String retryBackoffParam = externalTask.getVariable(PARAM_RETRY_BACKOFF);
                        if (retryBackoffParam != null) {
                            retryBackoff = Long.parseLong(retryBackoffParam);
                        } else {
                            retryBackoff = 0; // Default to zero if not set
                        }

                        log.debug("HANDLE_FAILURE: Handling failure with '{}' retry(s) left"
                                        + " and a backoff of '{}' second(s)",
                                retriesLeft, retryBackoff);
                        externalTaskService.handleFailure(externalTask, "HTTP request failed",
                                e.getLocalizedMessage(), retriesLeft, Duration.ofSeconds(retryBackoff).toMillis());
                    }
                    default -> log.warn("Invalid error handing method {}, error will be ignored",
                            errorHandlingMethod, e);
                }
            } else {
                log.warn("No error handing method specified, error will be ignored", e);
            }
        }

        log.debug("EXTERNAL TASK EXECUTED: {} / {}", externalTask.getActivityId(), externalTask.getExecutionId());
    }

    private void setPayload(final HttpRequest request, final String httpMethod, final String httpPayload) {
        if (httpPayload == null) {
            return;
        }
        if (PAYLOAD_VALID_FOR_METHODS.contains(httpMethod)) {
            request.payload(httpPayload);
        } else {
            log.warn("Ignoring payload because the http method is not one of: {}", PAYLOAD_VALID_FOR_METHODS);
        }
    }

    private void setQueryParams(final HttpRequest request, final Map<String, String> params) {
        if (params != null) {
            params.forEach(request::setRequestParameter);
        }
    }

    private void setHeaders(final HttpRequest request, final Map<String, String> headers) {
        if (headers != null) {
            headers.forEach((key, value) -> request.header(StringUtils.trimAllWhitespace(key), value));
        }
    }

    Map<String, String> asMap(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.startsWith("{")) {
            try {
                return mapper.readValue(new BufferedReader(new StringReader(value)), new TypeReference<>() { });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            Map<String, String> map = new HashMap<>();
            for (String pair : value.trim().split("\\s*;\\s*")) {
                String[] parts = pair.split("\\s*=\\s*");
                if (parts.length == 2) {
                    map.put(parts[0], parts[1]);
                } else {
                    throw new RuntimeException("Invalid key/value pair: " + pair);
                }
            }
            return map;
        }
    }
}
