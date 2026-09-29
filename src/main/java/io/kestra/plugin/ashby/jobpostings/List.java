package io.kestra.plugin.ashby.jobpostings;

import io.kestra.core.http.HttpResponse;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.common.FetchOutput;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.serializers.FileSerde;
import io.kestra.plugin.ashby.AbstractAshbyConnection;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Retrieve a list of Job Postings from Ashby",
    description = "Retrieves all job postings."
)
@Plugin(
    examples = {
        @Example(
            title = "Fetch all job postings and store them as internal storage",
            full = true,
            code = """
                id: fetch_job_postings
                namespace: company.team
                
                tasks:
                  - id: list_job_postings
                    type: io.kestra.plugin.ashby.jobpostings.List
                    apiKey: "{{ secret('ASHBY_API_KEY') }}"
                    fetchType: STORE
                """
        )
    }
)
public class List extends AbstractAshbyConnection implements RunnableTask<FetchOutput> {

    @Builder.Default
    @Schema(
        title = "The way you want to store the data",
        description = "FETCH_ONE outputs the first row, "
            + "FETCH outputs all the rows, "
            + "STORE stores all rows in a file, "
            + "NONE does nothing."
    )
    private Property<FetchType> fetchType = Property.ofValue(FetchType.STORE);

    @Override
    public FetchOutput run(RunContext runContext) throws Exception {
        FetchType rFetchType = runContext.render(this.fetchType).as(FetchType.class).orElse(FetchType.STORE);

        FetchOutput.FetchOutputBuilder outputBuilder = FetchOutput.builder();
        java.util.List<Object> allResults = new java.util.ArrayList<>();
        long size = 0;

        File tempFile = null;
        OutputStream outputStream = null;
        if (rFetchType == FetchType.STORE) {
            tempFile = runContext.workingDir().createTempFile(".ion").toFile();
            outputStream = new FileOutputStream(tempFile);
        }

        try {
            String cursor = null;
            boolean moreDataAvailable = true;
            TypeReference<Map<String, Object>> typeRef = new TypeReference<>() {};

            while (moreDataAvailable) {
                Map<String, Object> requestBody = new java.util.HashMap<>();
                if (cursor != null) {
                    requestBody.put("cursor", cursor);
                }

                HttpResponse<String> response = this.request(runContext, "POST", "/jobPosting.list", requestBody, String.class);

                if (response.getBody() == null || response.getBody().trim().isEmpty()) {
                    throw new IllegalStateException("Empty response from Ashby API");
                }

                Map<String, Object> body = JacksonMapper.ofJson().readValue(response.getBody(), typeRef);

                Boolean success = (Boolean) body.get("success");
                if (success != null && !success) {
                    Map<String, Object> errorInfo = (Map<String, Object>) body.get("errorInfo");
                    String errorMsg = errorInfo != null && errorInfo.containsKey("message") 
                        ? (String) errorInfo.get("message") 
                        : "Unknown error from Ashby API";
                    throw new IllegalStateException("Ashby API request failed: " + errorMsg);
                }

                java.util.List<Map<String, Object>> results = (java.util.List<Map<String, Object>>) body.get("results");
                if (results == null || results.isEmpty()) {
                    break;
                }

                size += results.size();

                switch (rFetchType) {
                    case FETCH_ONE:
                        if (allResults.isEmpty()) {
                            outputBuilder.row(results.get(0));
                            allResults.add(results.get(0));
                        }
                        break;
                    case FETCH:
                        allResults.addAll(results);
                        break;
                    case STORE:
                        for (Map<String, Object> row : results) {
                            FileSerde.write(outputStream, row);
                        }
                        break;
                    case NONE:
                        break;
                }

                if (rFetchType == FetchType.FETCH_ONE) {
                    break;
                }

                Boolean hasMore = (Boolean) body.get("moreDataAvailable");
                moreDataAvailable = hasMore != null ? hasMore : false;
                cursor = (String) body.get("nextCursor");
            }
        } finally {
            if (outputStream != null) {
                outputStream.close();
            }
        }

        outputBuilder.size(size);
        
        if (rFetchType == FetchType.FETCH) {
            outputBuilder.rows(allResults);
        } else if (rFetchType == FetchType.STORE && tempFile != null) {
            outputBuilder.uri(runContext.storage().putFile(tempFile));
        } else if (rFetchType == FetchType.FETCH_ONE) {
            outputBuilder.size(allResults.isEmpty() ? 0L : 1L);
        }

        return outputBuilder.build();
    }
}
