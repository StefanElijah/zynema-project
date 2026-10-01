package dev.zynema.user.identity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The saga's role step talks to the Admin API with a client-credentials token;
 * the tests pin the three-call shape (token, role, mapping) and that refusals
 * become the exception the saga can compensate on.
 */
class KeycloakAdminClientTest {

    private static final String SERVER = "http://keycloak:8080";

    private MockRestServiceServer server;
    private KeycloakAdminClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KeycloakAdminClient(builder.build(), SERVER, "zynema", "zynema-user-service", "secret");
    }

    @Test
    void grantsARealmRoleWithAClientCredentialsToken() {
        expectToken();
        server.expect(requestTo(SERVER + "/admin/realms/zynema/roles/subscriber"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer test-token"))
            .andRespond(withSuccess("{\"id\":\"role-1\",\"name\":\"subscriber\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(SERVER + "/admin/realms/zynema/users/user-1/role-mappings/realm"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json("[{\"id\":\"role-1\",\"name\":\"subscriber\"}]"))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        client.grantRealmRole("user-1", "subscriber");

        server.verify();
    }

    @Test
    void revokesARealmRole() {
        expectToken();
        server.expect(requestTo(SERVER + "/admin/realms/zynema/roles/subscriber"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"id\":\"role-1\",\"name\":\"subscriber\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(SERVER + "/admin/realms/zynema/users/user-1/role-mappings/realm"))
            .andExpect(method(HttpMethod.DELETE))
            .andExpect(content().json("[{\"id\":\"role-1\",\"name\":\"subscriber\"}]"))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        client.revokeRealmRole("user-1", "subscriber");

        server.verify();
    }

    @Test
    void normalisesAServerUrlWithATrailingSlash() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer bound = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminClient slashed =
            new KeycloakAdminClient(builder.build(), SERVER + "/", "zynema", "svc", "secret");
        bound.expect(requestTo(SERVER + "/realms/zynema/protocol/openid-connect/token"))
            .andRespond(withSuccess("{\"access_token\":\"test-token\"}", MediaType.APPLICATION_JSON));
        bound.expect(requestTo(SERVER + "/admin/realms/zynema/roles/subscriber"))
            .andRespond(withSuccess("{\"id\":\"role-1\"}", MediaType.APPLICATION_JSON));
        bound.expect(requestTo(SERVER + "/admin/realms/zynema/users/user-1/role-mappings/realm"))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        slashed.grantRealmRole("user-1", "subscriber");

        bound.verify();
    }

    @Test
    void wrapsARefusedGrant() {
        server.expect(requestTo(SERVER + "/realms/zynema/protocol/openid-connect/token"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.grantRealmRole("user-1", "subscriber"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Keycloak refused to grant role 'subscriber' to user user-1");
    }

    @Test
    void wrapsARefusedRevoke() {
        expectToken();
        server.expect(requestTo(SERVER + "/admin/realms/zynema/roles/subscriber"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.revokeRealmRole("user-1", "subscriber"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Keycloak refused to revoke role 'subscriber' from user user-1");
    }

    private void expectToken() {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", "zynema-user-service");
        form.add("client_secret", "secret");
        server.expect(requestTo(SERVER + "/realms/zynema/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().formData(form))
            .andRespond(withSuccess("{\"access_token\":\"test-token\"}", MediaType.APPLICATION_JSON));
    }
}
