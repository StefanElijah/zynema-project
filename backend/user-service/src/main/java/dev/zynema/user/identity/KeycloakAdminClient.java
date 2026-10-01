package dev.zynema.user.identity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * The Keycloak Admin API, used only by the orchestrated saga's role step.
 *
 * <p>The platform's roles live in the IdP, not in this database: granting one
 * means calling Keycloak, and the reply event says "the role is on the token
 * now" because that is what actually happened. The client authenticates as
 * itself (client credentials) with the {@code zynema-user-service} machine
 * account, which holds {@code manage-users} and nothing else.
 *
 * <p>A token per call is enough in development and keeps the client stateless;
 * a deployment would cache it until expiry (the same pattern the other machine
 * integrations use).
 */
@Slf4j
@Component
public class KeycloakAdminClient {

    private final RestClient http;
    private final String serverUrl;
    private final String realm;
    private final String clientId;
    private final String clientSecret;

    @Autowired
    public KeycloakAdminClient(
        @Value("${zynema.identity.admin.server-url:http://localhost:8180}") String serverUrl,
        @Value("${zynema.identity.admin.realm:zynema}") String realm,
        @Value("${zynema.identity.admin.client-id:zynema-user-service}") String clientId,
        @Value("${zynema.identity.admin.client-secret:zynema-user-service-dev-secret}") String clientSecret) {
        this(RestClient.create(), serverUrl, realm, clientId, clientSecret);
    }

    KeycloakAdminClient(RestClient http, String serverUrl, String realm, String clientId, String clientSecret) {
        this.http = http;
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public void grantRealmRole(String userSubject, String role) {
        try {
            String token = clientCredentialsToken();
            @SuppressWarnings("unchecked")
            Map<String, Object> roleRepresentation = http.get()
                .uri(adminUrl() + "/roles/" + role)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(Map.class);

            http.post()
                .uri(adminUrl() + "/users/" + userSubject + "/role-mappings/realm")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(roleRepresentation))
                .retrieve()
                .toBodilessEntity();
            log.info("Granted realm role '{}' to Keycloak user {}", role, userSubject);
        } catch (RestClientException ex) {
            throw new IllegalStateException(
                "Keycloak refused to grant role '%s' to user %s: %s".formatted(role, userSubject, ex.getMessage()), ex);
        }
    }

    public void revokeRealmRole(String userSubject, String role) {
        try {
            String token = clientCredentialsToken();
            @SuppressWarnings("unchecked")
            Map<String, Object> roleRepresentation = http.get()
                .uri(adminUrl() + "/roles/" + role)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(Map.class);

            http.method(org.springframework.http.HttpMethod.DELETE)
                .uri(adminUrl() + "/users/" + userSubject + "/role-mappings/realm")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(roleRepresentation))
                .retrieve()
                .toBodilessEntity();
            log.info("Revoked realm role '{}' from Keycloak user {}", role, userSubject);
        } catch (RestClientException ex) {
            throw new IllegalStateException(
                "Keycloak refused to revoke role '%s' from user %s: %s".formatted(role, userSubject, ex.getMessage()), ex);
        }
    }

    private String clientCredentialsToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = http.post()
            .uri(serverUrl + "/realms/" + realm + "/protocol/openid-connect/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(Map.class);
        return (String) response.get("access_token");
    }

    private String adminUrl() {
        return serverUrl + "/admin/realms/" + realm;
    }
}
