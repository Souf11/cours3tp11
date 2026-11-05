package ma.fstg.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityJpaFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("1. Page de login accessible publiquement (200 OK)")
    void loginPageIsPublic() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("2. Requête non authentifiée vers /home redirigée vers /login (302)")
    void unauthenticatedAccessRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/home"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    @DisplayName("3. Connexion utilisateur valide (user / 1111) -> redirection /home")
    void loginSuccessUser() throws Exception {
        mockMvc.perform(formLogin("/login").user("user").password("1111"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/home"));
    }

    @Test
    @DisplayName("4. Connexion admin valide (admin / 1234) -> redirection /home")
    void loginSuccessAdmin() throws Exception {
        mockMvc.perform(formLogin("/login").user("admin").password("1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/home"));
    }

    @Test
    @DisplayName("5. Connexion invalide -> redirection /login?error=true")
    void loginFailure() throws Exception {
        mockMvc.perform(formLogin("/login").user("user").password("wrongpassword"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=true"));
    }

    @Test
    @DisplayName("6. Utilisateur avec rôle USER accède à /user/dashboard")
    @WithMockUser(username = "user", roles = {"USER"})
    void userCanAccessUserDashboard() throws Exception {
        mockMvc.perform(get("/user/dashboard"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("7. Utilisateur avec rôle USER bloqué sur /admin/dashboard (403 Forbidden)")
    @WithMockUser(username = "user", roles = {"USER"})
    void userForbiddenOnAdminDashboard() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("8. Utilisateur avec rôle ADMIN accède à /admin/dashboard")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void adminCanAccessAdminDashboard() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("9. Déconnexion -> redirection vers /login?logout=true")
    @WithMockUser(username = "user", roles = {"USER"})
    void logoutRedirects() throws Exception {
        mockMvc.perform(post("/logout").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout=true"));
    }
}

