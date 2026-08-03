package com.example.collection_service.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private Clock clock;

    @InjectMocks
    private GlobalExceptionHandler globalExceptionHandler;

    @BeforeEach
    void setUp() {
        // Zamanı sabitliyoruz. Böylece LocalDateTime.now(clock) her zaman aynı tarihi dönecek.
        Clock fixedClock = Clock.fixed(Instant.parse("2026-08-03T10:00:00Z"), ZoneId.of("UTC"));
        lenient().when(clock.instant()).thenReturn(fixedClock.instant());
        lenient().when(clock.getZone()).thenReturn(fixedClock.getZone());
    }

    @Test
    void shouldHandleBusinessException_AndReturnCorrectFormat() {
        // Arrange (Hazırlık)
        String expectedMessage = "Geçersiz işlem kuralı ihlali!";
        HttpStatus expectedStatus = HttpStatus.BAD_REQUEST;

        // Fırlatılacak sahte BusinessException nesnemizi oluşturuyoruz
        BusinessException businessException = new BusinessException(expectedMessage, expectedStatus);

        // Act (Eylem)
        ResponseEntity<Map<String, Object>> response = globalExceptionHandler.handleBusinessException(businessException);

        // Assert (Doğrulama)
        assertNotNull(response);
        assertEquals(expectedStatus, response.getStatusCode()); // HTTP Status 400 (BAD_REQUEST) dönmeli

        Map<String, Object> responseBody = response.getBody();
        assertNotNull(responseBody);

        // JSON gövdesindeki (Map) değerleri tek tek doğruluyoruz
        assertEquals(expectedStatus.value(), responseBody.get("status")); // 400 dönmeli
        assertEquals(expectedMessage, responseBody.get("message")); // Mesaj eşleşmeli

        // Sabitlediğimiz zamanın doğru formatta map'e eklendiğini kontrol ediyoruz
        LocalDateTime expectedTimestamp = LocalDateTime.now(Clock.fixed(Instant.parse("2026-08-03T10:00:00Z"), ZoneId.of("UTC")));
        assertEquals(expectedTimestamp, responseBody.get("timestamp"));
    }
}