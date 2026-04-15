package com.jsonl.viewer.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsonl.viewer.api.dto.PreviewRequest;
import com.jsonl.viewer.config.AppProperties;
import com.jsonl.viewer.config.IngestSourceResolver;
import com.jsonl.viewer.ingest.IngestAdminService;
import com.jsonl.viewer.ingest.IngestPauseState;
import com.jsonl.viewer.repo.IngestStateRepository;
import com.jsonl.viewer.repo.JsonlEntryRepository;
import com.jsonl.viewer.repo.JsonlEntryRepositoryCustom.PreviewCursor;
import com.jsonl.viewer.service.FilterCountCacheService;
import com.jsonl.viewer.service.FilterRequestHasher;
import com.jsonl.viewer.service.FilterService;
import com.jsonl.viewer.service.PreviewCursorCodec;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class JsonlControllerPreviewTest {

  @Test
  void previewReturnsBadRequestWhenCursorSortDoesNotMatchRequest() {
    AppProperties properties = new AppProperties();
    properties.setJsonlFilePath("/tmp/sample.jsonl");

    AtomicBoolean repositoryCalled = new AtomicBoolean(false);
    JsonlEntryRepository repository = proxyRepository(repositoryCalled);
    IngestStateRepository ingestStateRepository = org.mockito.Mockito.mock(IngestStateRepository.class);
    org.mockito.Mockito.when(ingestStateRepository.findById(jsonlFilePath(properties)))
        .thenReturn(Optional.empty());
    FilterService filterService = new FilterService();
    FilterRequestHasher filterRequestHasher = new FilterRequestHasher();
    FilterCountCacheService filterCountCacheService = org.mockito.Mockito.mock(FilterCountCacheService.class);
    PreviewCursorCodec codec = new PreviewCursorCodec(new ObjectMapper());
    IngestSourceResolver sourceResolver = new IngestSourceResolver(properties);

    JsonlController controller = new JsonlController(
        properties,
        sourceResolver,
        repository,
        ingestStateRepository,
        filterService,
        filterRequestHasher,
        filterCountCacheService,
        new NoopIngestAdminService(),
        new IngestPauseState(),
        codec
    );

    PreviewRequest request = new PreviewRequest();
    request.setSortBy("timestamp");
    request.setSortDir("asc");
    request.setCursor(codec.encode(new PreviewCursor("id", "asc", 12L, null, null)));

    ResponseStatusException exception = assertThrows(
        ResponseStatusException.class,
        () -> controller.preview(request)
    );

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    assertFalse(repositoryCalled.get());
  }

  private JsonlEntryRepository proxyRepository(AtomicBoolean calledFlag) {
    return (JsonlEntryRepository) Proxy.newProxyInstance(
        JsonlEntryRepository.class.getClassLoader(),
        new Class<?>[]{JsonlEntryRepository.class},
        (proxy, method, args) -> {
          calledFlag.set(true);
          Class<?> returnType = method.getReturnType();
          if (boolean.class.equals(returnType)) {
            return false;
          }
          if (int.class.equals(returnType)) {
            return 0;
          }
          if (long.class.equals(returnType)) {
            return 0L;
          }
          if (double.class.equals(returnType)) {
            return 0D;
          }
          if (float.class.equals(returnType)) {
            return 0F;
          }
          if (short.class.equals(returnType)) {
            return (short) 0;
          }
          if (byte.class.equals(returnType)) {
            return (byte) 0;
          }
          if (char.class.equals(returnType)) {
            return '\0';
          }
          return null;
        }
    );
  }

  private String jsonlFilePath(AppProperties properties) {
    return properties.getJsonlFilePath();
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
