package edu.northeastern.cs6650.chat_server.metrics;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class MetricsControllerTest {

  private MetricsRepository repo;
  private MetricsController controller;

  @BeforeEach
  void setUp() {
    repo = mock(MetricsRepository.class);
    controller = new MetricsController(repo);

    // Default stub values
    when(repo.mostActiveRoomId()).thenReturn("room-1");
    when(repo.mostActiveUserId()).thenReturn("user-1");
    when(repo.minTimestamp()).thenReturn("2026-03-01T00:00:00Z");
    when(repo.maxTimestamp()).thenReturn("2026-03-01T01:00:00Z");
    when(repo.totalMessageCount()).thenReturn(1000L);
    when(repo.roomMessagesInTimeRange(anyString(), anyString(), anyString()))
        .thenReturn(List.of());
    when(repo.userMessageHistory(anyString(), anyString(), anyString()))
        .thenReturn(List.of());
    when(repo.activeUserCount(anyString(), anyString())).thenReturn(50L);
    when(repo.userRoomsParticipated(anyString())).thenReturn(List.of());
    when(repo.messagesPerMinute()).thenReturn(List.of());
    when(repo.mostActiveUsers(anyInt())).thenReturn(List.of());
    when(repo.mostActiveRooms(anyInt())).thenReturn(List.of());
    when(repo.userParticipationPatterns(anyInt())).thenReturn(List.of());
  }

  @Test
  void getMetrics_noParams_usesAutoSelectedDefaults() {
    controller.getMetrics(null, null, null, null, 10);

    verify(repo).mostActiveRoomId();
    verify(repo).mostActiveUserId();
    verify(repo).minTimestamp();
    verify(repo).maxTimestamp();
    verify(repo).roomMessagesInTimeRange("room-1", "2026-03-01T00:00:00Z", "2026-03-01T01:00:00Z");
    verify(repo).userMessageHistory("user-1", "2026-03-01T00:00:00Z", "2026-03-01T01:00:00Z");
    verify(repo).activeUserCount("2026-03-01T00:00:00Z", "2026-03-01T01:00:00Z");
    verify(repo).userRoomsParticipated("user-1");
  }

  @Test
  void getMetrics_explicitParams_passedThroughToRepo() {
    controller.getMetrics("room-5", "user-99",
        "2026-03-10T08:00:00Z", "2026-03-10T09:00:00Z", 5);

    verify(repo, never()).mostActiveRoomId();
    verify(repo, never()).mostActiveUserId();
    verify(repo).roomMessagesInTimeRange(
        "room-5", "2026-03-10T08:00:00Z", "2026-03-10T09:00:00Z");
    verify(repo).userMessageHistory(
        "user-99", "2026-03-10T08:00:00Z", "2026-03-10T09:00:00Z");
    verify(repo).activeUserCount("2026-03-10T08:00:00Z", "2026-03-10T09:00:00Z");
    verify(repo).userRoomsParticipated("user-99");
  }

  @Test
  void getMetrics_responseContainsAllSections() {
    ResponseEntity<Map<String, Object>> response =
        controller.getMetrics(null, null, null, null, 10);

    assertEquals(200, response.getStatusCode().value());
    Map<String, Object> body = response.getBody();
    assertNotNull(body);
    assertTrue(body.containsKey("totalMessages"));
    assertTrue(body.containsKey("coreQueryInputs"));
    assertTrue(body.containsKey("coreQueries"));
    assertTrue(body.containsKey("analytics"));
  }

  @Test
  void getMetrics_coreQueryInputsEchoesResolvedValues() {
    ResponseEntity<Map<String, Object>> response =
        controller.getMetrics("room-5", "user-99",
            "2026-03-10T08:00:00Z", "2026-03-10T09:00:00Z", 10);

    @SuppressWarnings("unchecked")
    Map<String, Object> inputs =
        (Map<String, Object>) response.getBody().get("coreQueryInputs");
    assertEquals("room-5", inputs.get("roomId"));
    assertEquals("user-99", inputs.get("userId"));
    assertEquals("2026-03-10T08:00:00Z", inputs.get("startTime"));
    assertEquals("2026-03-10T09:00:00Z", inputs.get("endTime"));
  }

  @Test
  void getMetrics_topNPassedToAnalyticsQueries() {
    controller.getMetrics(null, null, null, null, 5);

    verify(repo).mostActiveUsers(5);
    verify(repo).mostActiveRooms(5);
    verify(repo).userParticipationPatterns(5);
  }
}
