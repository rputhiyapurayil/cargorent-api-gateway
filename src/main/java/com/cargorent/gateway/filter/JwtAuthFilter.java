package com.cargorent.gateway.filter;

import com.cargorent.gateway.config.GatewaySecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Validates the Bearer JWT on every request and forwards the caller identity downstream
 * as X-User-Id / X-User-Role / X-User-Name / X-User-Email. Any X-User-* headers sent by
 * the client are removed first, so they cannot be forged.
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final List<String> IDENTITY_HEADERS = List.of("X-User-Id", "X-User-Role", "X-User-Name", "X-User-Email");

    private final SecretKey key;
    private final List<String> publicRoutes;
    private final List<String> internalRoutes;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public JwtAuthFilter(GatewaySecurityProperties props) {
        this.key = Keys.hmacShaKeyFor(props.jwtSecret().getBytes(StandardCharsets.UTF_8));
        this.publicRoutes = props.publicRoutes() == null ? List.of() : props.publicRoutes();
        this.internalRoutes = props.internalRoutes() == null ? List.of() : props.internalRoutes();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        HttpMethod method = request.getMethod();
        String path = request.getPath().value();

        if (HttpMethod.OPTIONS.equals(method)) return chain.filter(exchange);

        if (matchesAny(internalRoutes, method, path)) {
            return error(exchange, HttpStatus.NOT_FOUND, "Not found");
        }

        String auth = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        boolean hasToken = auth != null && auth.startsWith("Bearer ");

        if (!hasToken) {
            if (matchesAny(publicRoutes, method, path)) {
                return chain.filter(exchange.mutate().request(stripIdentity(request)).build());
            }
            return error(exchange, HttpStatus.UNAUTHORIZED, "Please log in to continue");
        }

        Claims claims;
        try {
            claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(auth.substring(7)).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected token on {} {}: {}", method, path, e.getMessage());
            return error(exchange, HttpStatus.UNAUTHORIZED, "Your session has expired. Please log in again.");
        }

        ServerHttpRequest mutated = request.mutate().headers(h -> {
            IDENTITY_HEADERS.forEach(h::remove);
            h.set("X-User-Id", claims.getSubject());
            h.set("X-User-Role", String.valueOf(claims.get("role")));
            h.set("X-User-Name", encode(claims.get("name")));
            h.set("X-User-Email", encode(claims.get("email")));
        }).build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private boolean matchesAny(List<String> rules, HttpMethod method, String path) {
        for (String rule : rules) {
            String[] parts = rule.trim().split("\\s+", 2);
            if (parts.length != 2) continue;
            boolean methodOk = "*".equals(parts[0]) || parts[0].equalsIgnoreCase(method.name());
            if (methodOk && matcher.match(parts[1], path)) return true;
        }
        return false;
    }

    private static ServerHttpRequest stripIdentity(ServerHttpRequest request) {
        return request.mutate().headers(h -> IDENTITY_HEADERS.forEach(h::remove)).build();
    }

    private static String encode(Object value) {
        return value == null ? "" : URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8);
    }

    private static Mono<Void> error(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"timestamp\":\"" + Instant.now() + "\",\"status\":" + status.value()
                + ",\"error\":\"" + status.getReasonPhrase() + "\",\"message\":\"" + message
                + "\",\"path\":\"" + exchange.getRequest().getPath().value() + "\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
