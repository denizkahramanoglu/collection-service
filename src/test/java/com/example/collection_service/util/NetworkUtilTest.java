package com.example.collection_service.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NetworkUtilTest {

    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        // Her testten önce sahte bir HTTP Request oluşturup Spring'in Context'ine yerleştiriyoruz
        request = mock(HttpServletRequest.class);
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attributes);
    }

    @AfterEach
    void tearDown() {
        // Diğer testleri etkilememesi için Context'i temizliyoruz
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldReturnDefaultIp_WhenRequestContextIsNull() {
        // Senaryo 1: Ortada bir HTTP isteği yoksa (Örn: Arka plan Job'ı çalışıyorsa)
        RequestContextHolder.resetRequestAttributes();

        String ip = NetworkUtil.getClientIp();

        assertEquals("127.0.0.1", ip);
    }

    @Test
    void shouldReturnRemoteAddr_WhenHeaderIsNull() {
        // Senaryo 2: X-Forwarded-For başlığı hiç gönderilmemişse
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.10");

        String ip = NetworkUtil.getClientIp();

        assertEquals("192.168.1.10", ip);
    }

    @Test
    void shouldReturnRemoteAddr_WhenHeaderIsEmpty() {
        // Senaryo 3: Başlık var ama içi boş string gelmişse
        when(request.getHeader("X-Forwarded-For")).thenReturn("");
        when(request.getRemoteAddr()).thenReturn("192.168.1.20");

        String ip = NetworkUtil.getClientIp();

        assertEquals("192.168.1.20", ip);
    }

    @Test
    void shouldReturnFirstIp_WhenHeaderHasMultipleIps() {
        // Senaryo 4: İstek birden fazla Proxy/Load Balancer üzerinden sekerek gelmişse (Virgüllü durum)
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.195, 70.41.3.18, 150.172.238.178");

        String ip = NetworkUtil.getClientIp();

        // Sadece en baştaki asıl müşteri IP'sini almalı
        assertEquals("203.0.113.195", ip);
    }

    @Test
    void shouldReturnSingleIp_WhenHeaderHasSingleIp() {
        // Senaryo 5: Başlıkta sadece tek bir IP varsa
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.50");

        String ip = NetworkUtil.getClientIp();

        assertEquals("198.51.100.50", ip);
    }
}