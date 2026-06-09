package com.asuka.filelist.infrastructure.driver.baidu;

import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * 百度 access_token 管理：用 refresh_token 换取并按 expires_in 内存缓存，提前 60s 刷新。
 *
 * <p>refresh_token 长期有效、可重复使用，无需回写 addition。多实例各自持有互不冲突。
 */
public class BaiduTokenManager {

    private static final long REFRESH_SKEW_SECONDS = 60;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final BaiduDriverAddition addition;
    private final String oauthBase;

    private volatile String cachedToken;
    private volatile Instant expiresAt = Instant.EPOCH;

    public BaiduTokenManager(HttpClient httpClient, ObjectMapper objectMapper,
                             BaiduDriverAddition addition, String oauthBase) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.addition = addition;
        this.oauthBase = oauthBase;
    }

    /**
     * 返回有效 access_token，过期或未取时刷新。
     */
    public synchronized String getAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(expiresAt)) {
            return cachedToken;
        }
        refresh();
        return cachedToken;
    }

    /**
     * 用 refresh_token 换取新 access_token 并刷新缓存。
     */
    private void refresh() {
        String url = oauthBase + "/oauth/2.0/token"
                + "?grant_type=refresh_token"
                + "&refresh_token=" + encode(addition.refreshToken())
                + "&client_id=" + encode(addition.clientId())
                + "&client_secret=" + encode(addition.clientSecret());
        try {
            HttpResponse<String> resp = httpClient.send(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "Baidu token refresh failed with status " + resp.statusCode());
            }
            JsonNode node = objectMapper.readTree(resp.body());
            JsonNode token = node.get("access_token");
            if (token == null || token.asText().isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "Baidu token refresh returned no access_token");
            }
            long expiresIn = node.path("expires_in").asLong(3600);
            this.cachedToken = token.asText();
            this.expiresAt = Instant.now().plusSeconds(Math.max(0, expiresIn - REFRESH_SKEW_SECONDS));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Baidu token refresh failed: " + ex.getMessage());
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
