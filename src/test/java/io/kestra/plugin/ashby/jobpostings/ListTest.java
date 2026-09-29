package io.kestra.plugin.ashby.jobpostings;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.models.tasks.common.FetchOutput;
import io.kestra.core.models.tasks.common.FetchType;
import org.junit.jupiter.api.Assertions;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@KestraTest
@WireMockTest
class ListTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void run(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        stubFor(
            post(urlEqualTo("/jobPosting.list"))
                .withHeader("Authorization", matching("Basic .*"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"results\": [{\"id\": \"123\", \"title\": \"Software Engineer\"}]}")
                )
        );

        List task = List.builder()
            .id(IdUtils.create())
            .type(List.class.getName())
            .apiKey(Property.ofValue("dummy-api-key"))
            .baseUrl(Property.ofValue(wmRuntimeInfo.getHttpBaseUrl()))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .build();

        RunContext runContext = runContextFactory.of(ImmutableMap.of());
        FetchOutput output = task.run(runContext);

        assertThat(output.getRows(), notNullValue());
        assertThat(output.getSize(), is(1L));
        
        java.util.List<Object> results = output.getRows();
        Map<String, Object> firstRow = (Map<String, Object>) results.get(0);
        assertThat(firstRow.get("title"), is("Software Engineer"));
    }

    @Test
    void run_empty(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        stubFor(
            post(urlEqualTo("/jobPosting.list"))
                .withHeader("Authorization", matching("Basic .*"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"results\": []}")
                )
        );

        List task = List.builder()
            .id(IdUtils.create())
            .type(List.class.getName())
            .apiKey(Property.ofValue("dummy-api-key"))
            .baseUrl(Property.ofValue(wmRuntimeInfo.getHttpBaseUrl()))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .build();

        RunContext runContext = runContextFactory.of(ImmutableMap.of());
        FetchOutput output = task.run(runContext);

        assertThat(output.getRows(), notNullValue());
        assertThat(output.getSize(), is(0L));
        assertThat(output.getRows().size(), is(0));
    }

    @Test
    void run_error(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        stubFor(
            post(urlEqualTo("/jobPosting.list"))
                .withHeader("Authorization", matching("Basic .*"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\": false, \"errorInfo\": {\"message\": \"Invalid API Key\"}}")
                )
        );

        List task = List.builder()
            .id(IdUtils.create())
            .type(List.class.getName())
            .apiKey(Property.ofValue("dummy-api-key"))
            .baseUrl(Property.ofValue(wmRuntimeInfo.getHttpBaseUrl()))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .build();

        RunContext runContext = runContextFactory.of(ImmutableMap.of());
        
        IllegalStateException exception = Assertions.assertThrows(IllegalStateException.class, () -> {
            task.run(runContext);
        });

        assertThat(exception.getMessage(), is("Ashby API request failed: Invalid API Key"));
    }
}
