package com.abhiknow.provider;

import com.abhiknow.config.AbhiKnowProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** The only place outbound provider HTTP happens. Fixed base URLs, size cap, identified User-Agent. */
@Component
public class HttpFetcher {
    static final int MAX_BODY_BYTES = 8 * 1024 * 1024;
    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final String userAgent;

    public HttpFetcher(AbhiKnowProperties props) {
        this.userAgent = props.userAgent();
    }

    public String get(String url, Duration timeout) throws ProviderException {
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder(URI.create(url)).timeout(timeout)
                    .header("User-Agent", userAgent).header("Accept", "application/json, application/atom+xml, */*").GET().build();
        } catch (IllegalArgumentException e) {
            throw new ProviderException(ProviderException.Kind.HTTP_ERROR, "Bad URL", e);
        }
        try {
            HttpResponse<InputStream> res = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = res.body()) {
                int s = res.statusCode();
                if (s == 429) throw new ProviderException(ProviderException.Kind.RATE_LIMITED, "Provider rate limit (HTTP 429)");
                if (s >= 500 || s == 408) throw new ProviderException(ProviderException.Kind.UNAVAILABLE, "Provider error HTTP " + s);
                if (s == 404) throw new ProviderException(ProviderException.Kind.NOT_FOUND, "Not found (HTTP 404)");
                if (s >= 400) throw new ProviderException(ProviderException.Kind.HTTP_ERROR, "Provider rejected request HTTP " + s);
                byte[] body = in.readNBytes(MAX_BODY_BYTES + 1);
                if (body.length > MAX_BODY_BYTES) throw new ProviderException(ProviderException.Kind.PARSE_ERROR, "Response too large");
                return new String(body, StandardCharsets.UTF_8);
            }
        } catch (HttpTimeoutException e) {
            throw new ProviderException(ProviderException.Kind.TIMEOUT, "Provider timed out", e);
        } catch (IOException e) {
            throw new ProviderException(ProviderException.Kind.UNAVAILABLE, "Provider unreachable: " + e.getClass().getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderException(ProviderException.Kind.TIMEOUT, "Interrupted", e);
        }
    }
}
