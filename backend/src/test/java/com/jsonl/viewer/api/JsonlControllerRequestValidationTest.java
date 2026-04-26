package com.jsonl.viewer.api;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsonl.viewer.config.AppProperties;
import com.jsonl.viewer.config.IngestSourceResolver;
import com.jsonl.viewer.ingest.IngestAdminService;
import com.jsonl.viewer.ingest.IngestPauseState;
import com.jsonl.viewer.repo.IngestStateRepository;
import com.jsonl.viewer.repo.JsonlEntryRepository;
import com.jsonl.viewer.service.FilterCountCacheService;
import com.jsonl.viewer.service.FilterRequestHasher;
import com.jsonl.viewer.service.FilterService;
import com.jsonl.viewer.service.PreviewCursorCodec;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class JsonlControllerRequestValidationTest {

  @Test
  void countReturnsBadRequestForRemovedTopLevelFieldPayload() throws Exception {
    MockMvc mockMvc = mockMvc();

    mockMvc.perform(post("/api/filters/count")
            .contentType(APPLICATION_JSON)
            .content("""
                {
                  "filtersOp": "and",
                  "fieldPath": "level",
                  "valueContains": "error"
                }
                """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void previewReturnsBadRequestForRemovedFieldFilterMembers() throws Exception {
    MockMvc mockMvc = mockMvc();

    mockMvc.perform(post("/api/filters/preview")
            .contentType(APPLICATION_JSON)
            .content("""
                {
                  "filtersOp": "and",
                  "filters": [
                    {
                      "type": "field",
                      "fieldPath": "level",
                      "op": "contains",
                      "valueContains": "error"
                    }
                  ]
                }
                """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void previewReturnsBadRequestForFieldFilterTypeEvenWithoutRemovedMembers() throws Exception {
    MockMvc mockMvc = mockMvc();

    mockMvc.perform(post("/api/filters/preview")
            .contentType(APPLICATION_JSON)
            .content("""
                {
                  "filtersOp": "and",
                  "filters": [
                    {
                      "type": "field",
                      "query": "error"
                    }
                  ]
                }
                """))
        .andExpect(status().isBadRequest());
  }

  private MockMvc mockMvc() {
    AppProperties properties = new AppProperties();
    properties.setJsonlFilePath("/tmp/sample.jsonl");

    IngestStateRepository ingestStateRepository = mock(IngestStateRepository.class);
    when(ingestStateRepository.findById(anyString())).thenReturn(Optional.empty());

    JsonlController controller = new JsonlController(
        properties,
        new IngestSourceResolver(properties),
        mock(JsonlEntryRepository.class),
        ingestStateRepository,
        new FilterService(),
        new FilterRequestHasher(),
        mock(FilterCountCacheService.class),
        new NoopIngestAdminService(),
        new IngestPauseState(),
        new PreviewCursorCodec(new ObjectMapper())
    );

    return MockMvcBuilders.standaloneSetup(controller).build();
  }

  private static class NoopIngestAdminService implements IngestAdminService {
    @Override
    public void reset() {}

    @Override
    public void reload() {}

    @Override
    public void pause() {}

    @Override
    public void resume() {}
  }
}
