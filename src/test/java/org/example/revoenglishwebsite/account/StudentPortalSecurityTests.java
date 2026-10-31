package org.example.revoenglishwebsite.account;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(properties = "site.telegram-username=test_teacher")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class StudentPortalSecurityTests {

    private static final String PASSWORD = "Initial-Pass-123";
    private static final String NEW_PASSWORD = "Brand-New-Pass-456";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AccountService accounts;

    @Autowired
    private UserAccountRepository repository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ----- anonymous access -----

    @Test
    void anonymousVisitorsAreRedirectedToLogin() throws Exception {
        for (String path : new String[]{"/dashboard", "/admin", "/admin/students", "/account/password"}) {
            mvc.perform(get(path))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login"));
        }
    }

    @Test
    void publicPagesAndLoginStayPublic() throws Exception {
        for (String path : new String[]{"/", "/english", "/it-english", "/login", "/robots.txt"}) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
    }

    @Test
    void thereIsNoSelfRegistration() throws Exception {
        for (String path : new String[]{"/register", "/signup", "/registration"}) {
            mvc.perform(get(path)).andExpect(status().isNotFound());
        }
        mvc.perform(post("/register").with(csrf()).param("login", "x")).andExpect(status().isNotFound());
        String loginPage = mvc.perform(get("/login")).andReturn().getResponse().getContentAsString();
        assertThat(loginPage).contains("самостоятельной регистрации нет").doesNotContain("/register");
    }

    // ----- login, forced password change, logout -----

    @Test
    void studentLogsInChangesTemporaryPasswordAndLogsOut() throws Exception {
        String login = unique("anna");
        accounts.createStudent("Анна", login, PASSWORD);

        MockHttpSession session = login(login, PASSWORD, "/dashboard");

        // A temporary password must be changed before the dashboard opens
        mvc.perform(get("/dashboard").session(session)).andExpect(redirectedUrl("/account/password"));
        mvc.perform(post("/account/password").session(session).with(csrf())
                        .param("currentPassword", PASSWORD).param("newPassword", NEW_PASSWORD).param("confirmPassword", NEW_PASSWORD))
                .andExpect(redirectedUrl("/dashboard"));

        mvc.perform(get("/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Здравствуйте, <em>Анна</em>!")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Вы вошли как <strong>" + login + "</strong>")));

        mvc.perform(post("/logout").session(session).with(csrf())).andExpect(redirectedUrl("/login?logout")).andExpect(unauthenticated());
        mvc.perform(get("/dashboard").session(session)).andExpect(redirectedUrl("/login"));

        // The new password works, the old one does not
        login(login, NEW_PASSWORD, "/dashboard");
        mvc.perform(formLogin("/login").user("login", login).password(PASSWORD))
                .andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
    }

    @Test
    void afterLoginAPreviouslyRequestedPageIsOnlyReusedIfTheRoleMayOpenIt() throws Exception {
        // An admin who first tried /dashboard (students only) lands on the admin home, not a 403 page
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/dashboard").session(session)).andExpect(redirectedUrl("/login"));
        String adminLogin = unique("returning-admin");
        accounts.bootstrapAdmin("Админ", adminLogin, "Admin-Pass-0001");
        mvc.perform(post("/login").session(session).with(csrf()).param("login", adminLogin).param("password", "Admin-Pass-0001"))
                .andExpect(redirectedUrl("/admin/students"));

        // A saved page the role may open is honoured
        MockHttpSession second = new MockHttpSession();
        mvc.perform(get("/admin/students").session(second)).andExpect(redirectedUrl("/login"));
        mvc.perform(post("/login").session(second).with(csrf()).param("login", adminLogin).param("password", "Admin-Pass-0001"))
                .andExpect(redirectedUrl("http://localhost/admin/students?continue"));
    }

    @Test
    void loginIsCaseInsensitiveForTheIdentifier() throws Exception {
        String login = unique("case");
        accounts.createStudent("Кейс", login, PASSWORD);
        login(login.toUpperCase(), PASSWORD, "/dashboard");
    }

    @Test
    void wrongPasswordAndUnknownLoginGiveTheSameGenericError() throws Exception {
        String login = unique("boris");
        accounts.createStudent("Борис", login, PASSWORD);

        mvc.perform(formLogin("/login").user("login", login).password("wrong-password-1"))
                .andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
        mvc.perform(formLogin("/login").user("login", "nobody-" + login).password(PASSWORD))
                .andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
    }

    @Test
    void loginAndLogoutRequireCsrfToken() throws Exception {
        String login = unique("csrf");
        accounts.createStudent("Csrf", login, PASSWORD);

        mvc.perform(post("/login").param("login", login).param("password", PASSWORD))
                .andExpect(status().isForbidden()).andExpect(unauthenticated());
        MockHttpSession session = login(login, PASSWORD, "/dashboard");
        mvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void disabledAccountsCannotLogIn() throws Exception {
        String login = unique("disabled");
        var created = accounts.createStudent("Отключённый", login, PASSWORD);
        accounts.setStudentActive(created.account().getId(), false);

        mvc.perform(formLogin("/login").user("login", login).password(PASSWORD))
                .andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
    }

    @Test
    void deactivationEndsAnExistingSession() throws Exception {
        String login = unique("kicked");
        var created = accounts.createStudent("Сеанс", login, PASSWORD);
        MockHttpSession session = login(login, PASSWORD, "/dashboard");

        accounts.setStudentActive(created.account().getId(), false);

        mvc.perform(get("/dashboard").session(session)).andExpect(redirectedUrl("/login?ended"));
        mvc.perform(get("/dashboard").session(session)).andExpect(redirectedUrl("/login"));
    }

    // ----- students vs. administration -----

    @Test
    void studentsCannotAccessAdministration() throws Exception {
        MockHttpSession student = activeStudentSession("eve");
        long before = repository.count();

        mvc.perform(get("/admin/students").session(student)).andExpect(status().isForbidden());
        mvc.perform(post("/admin/students").session(student).with(csrf())
                        .param("displayName", "Хакер").param("login", unique("hacker")).param("passwordMode", "generate"))
                .andExpect(status().isForbidden());
        assertThat(repository.count()).isEqualTo(before);
    }

    @Test
    void studentsCannotChangeRolesOrOtherAccounts() throws Exception {
        String login = unique("mallory");
        var created = accounts.createStudent("Мэллори", login, PASSWORD);
        MockHttpSession session = login(login, PASSWORD, "/dashboard");
        mvc.perform(post("/account/password").session(session).with(csrf())
                .param("currentPassword", PASSWORD).param("newPassword", NEW_PASSWORD).param("confirmPassword", NEW_PASSWORD)
                .param("role", "ADMIN"));
        Long id = created.account().getId();

        mvc.perform(post("/admin/students/" + id + "/status").session(session).with(csrf()).param("active", "false"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/students/" + id + "/password-reset").session(session).with(csrf()))
                .andExpect(status().isForbidden());

        UserAccount after = repository.findById(id).orElseThrow();
        assertThat(after.getRole()).isEqualTo(Role.STUDENT);
        assertThat(after.isActive()).isTrue();
    }

    @Test
    void dashboardShowsOnlyTheSignedInStudent() throws Exception {
        accounts.createStudent("Чужой Ученик", unique("other"), PASSWORD);
        MockHttpSession session = activeStudentSession("self");

        String page = mvc.perform(get("/dashboard").session(session).param("id", "1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(page).doesNotContain("Чужой Ученик");
    }

    @Test
    void administratorsCannotOpenTheStudentDashboard() throws Exception {
        mvc.perform(get("/dashboard").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
    }

    // ----- account management by the administrator -----

    @Test
    void administratorCreatesStudentWithHashedTemporaryPassword(CapturedOutput output) throws Exception {
        MockHttpSession admin = adminSession();
        String login = unique("NewStudent");

        MvcResult result = mvc.perform(post("/admin/students").session(admin).with(csrf())
                        .param("displayName", "  Новый   Ученик ").param("login", login).param("passwordMode", "generate")
                        .param("role", "ADMIN")) // must be ignored
                .andExpect(status().isOk())
                .andExpect(view().name("admin/credentials"))
                .andExpect(model().attributeExists("temporaryPassword"))
                .andReturn();
        String temporaryPassword = (String) result.getModelAndView().getModel().get("temporaryPassword");
        assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");

        UserAccount stored = repository.findByLogin(login.toLowerCase()).orElseThrow();
        assertThat(stored.getRole()).isEqualTo(Role.STUDENT);
        assertThat(stored.getDisplayName()).isEqualTo("Новый Ученик");
        assertThat(stored.isActive()).isTrue();
        assertThat(stored.isPasswordChangeRequired()).isTrue();
        assertThat(stored.getPasswordHash()).startsWith("{bcrypt}$2").doesNotContain(temporaryPassword);
        assertThat(passwordEncoder.matches(temporaryPassword, stored.getPasswordHash())).isTrue();
        assertThat(output.getAll()).doesNotContain(temporaryPassword);

        // The student can sign in with the temporary password
        login(login, temporaryPassword, "/dashboard");
    }

    @Test
    void administratorCanSetTheInitialPasswordWhichIsNeverEchoed(CapturedOutput output) throws Exception {
        MockHttpSession admin = adminSession();
        String login = unique("manual");
        String chosen = "Chosen-Pass-789";

        String page = mvc.perform(post("/admin/students").session(admin).with(csrf())
                        .param("displayName", "Ручной").param("login", login)
                        .param("passwordMode", "manual").param("initialPassword", chosen))
                .andExpect(view().name("admin/credentials"))
                .andReturn().getResponse().getContentAsString();

        assertThat(page).doesNotContain(chosen);
        assertThat(output.getAll()).doesNotContain(chosen);
        assertThat(repository.findByLogin(login).orElseThrow().getPasswordHash()).doesNotContain(chosen);
        login(login, chosen, "/dashboard");
    }

    @Test
    void duplicateLoginsAreRejectedCaseInsensitively() throws Exception {
        MockHttpSession admin = adminSession();
        String login = unique("dup");
        accounts.createStudent("Первый", login, PASSWORD);

        mvc.perform(post("/admin/students").session(admin).with(csrf())
                        .param("displayName", "Второй").param("login", "  " + login.toUpperCase() + " ").param("passwordMode", "generate"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/students"))
                .andExpect(model().attributeHasFieldErrors("form", "login"));

        assertThat(repository.findAll()).filteredOn(a -> a.getLogin().equals(login)).hasSize(1);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> accounts.createStudent("Третий", login, PASSWORD))
                .isInstanceOf(AccountException.class).hasMessageContaining("уже занят");
    }

    @Test
    void invalidInputIsRejectedWithFieldErrors() throws Exception {
        MockHttpSession admin = adminSession();
        long before = repository.count();

        mvc.perform(post("/admin/students").session(admin).with(csrf())
                        .param("displayName", "").param("login", "a b").param("passwordMode", "generate"))
                .andExpect(model().attributeHasFieldErrors("form", "displayName"));
        mvc.perform(post("/admin/students").session(admin).with(csrf())
                        .param("displayName", "Имя").param("login", unique("weak")).param("passwordMode", "manual").param("initialPassword", "short"))
                .andExpect(model().attributeHasFieldErrors("form", "initialPassword"));
        mvc.perform(post("/admin/students").session(admin).with(csrf())
                        .param("displayName", "Имя").param("login", "<script>").param("passwordMode", "generate"))
                .andExpect(model().attributeHasFieldErrors("form", "login"));

        assertThat(repository.count()).isEqualTo(before);
    }

    @Test
    void administratorFormsRequireCsrfToken() throws Exception {
        MockHttpSession admin = adminSession();
        mvc.perform(post("/admin/students").session(admin)
                        .param("displayName", "Без токена").param("login", unique("nocsrf")).param("passwordMode", "generate"))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorDeactivatesAndReactivatesStudents() throws Exception {
        MockHttpSession admin = adminSession();
        String login = unique("toggle");
        Long id = accounts.createStudent("Переключатель", login, PASSWORD).account().getId();

        mvc.perform(post("/admin/students/" + id + "/status").session(admin).with(csrf()).param("active", "false"))
                .andExpect(redirectedUrl("/admin/students"));
        assertThat(repository.findById(id).orElseThrow().isActive()).isFalse();
        mvc.perform(formLogin("/login").user("login", login).password(PASSWORD)).andExpect(unauthenticated());

        mvc.perform(post("/admin/students/" + id + "/status").session(admin).with(csrf()).param("active", "true"));
        assertThat(repository.findById(id).orElseThrow().isActive()).isTrue();
        login(login, PASSWORD, "/dashboard");
    }

    @Test
    void administratorAccountsCannotBeChangedThroughStudentManagement() throws Exception {
        String adminLogin = unique("admin");
        accounts.bootstrapAdmin("Админ", adminLogin, "Admin-Pass-0001");
        Long adminId = repository.findByLogin(adminLogin).orElseThrow().getId();
        MockHttpSession admin = login(adminLogin, "Admin-Pass-0001", "/admin/students");

        mvc.perform(post("/admin/students/" + adminId + "/status").session(admin).with(csrf()).param("active", "false"))
                .andExpect(redirectedUrl("/admin/students"));
        mvc.perform(post("/admin/students/" + adminId + "/password-reset").session(admin).with(csrf()))
                .andExpect(redirectedUrl("/admin/students"));

        UserAccount stored = repository.findById(adminId).orElseThrow();
        assertThat(stored.isActive()).isTrue();
        assertThat(passwordEncoder.matches("Admin-Pass-0001", stored.getPasswordHash())).isTrue();
        String list = mvc.perform(get("/admin/students").session(admin)).andReturn().getResponse().getContentAsString();
        assertThat(list).doesNotContain(adminLogin);
    }

    @Test
    void passwordResetIssuesANewTemporaryPassword() throws Exception {
        MockHttpSession admin = adminSession();
        String login = unique("reset");
        Long id = accounts.createStudent("Сброс", login, PASSWORD).account().getId();

        MvcResult result = mvc.perform(post("/admin/students/" + id + "/password-reset").session(admin).with(csrf()))
                .andExpect(view().name("admin/credentials")).andReturn();
        String temporary = (String) result.getModelAndView().getModel().get("temporaryPassword");

        mvc.perform(formLogin("/login").user("login", login).password(PASSWORD)).andExpect(unauthenticated());
        login(login, temporary, "/dashboard");
        assertThat(repository.findById(id).orElseThrow().isPasswordChangeRequired()).isTrue();
    }

    // ----- helpers -----

    private MockHttpSession login(String login, String password, String expectedTarget) throws Exception {
        MvcResult result = mvc.perform(formLogin("/login").user("login", login).password(password))
                .andExpect(authenticated())
                .andExpect(redirectedUrl(expectedTarget))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    /** A student who has already replaced the temporary password. */
    private MockHttpSession activeStudentSession(String prefix) throws Exception {
        String login = unique(prefix);
        accounts.createStudent("Ученик " + prefix, login, PASSWORD);
        MockHttpSession session = login(login, PASSWORD, "/dashboard");
        mvc.perform(post("/account/password").session(session).with(csrf())
                        .param("currentPassword", PASSWORD).param("newPassword", NEW_PASSWORD).param("confirmPassword", NEW_PASSWORD))
                .andExpect(redirectedUrl("/dashboard"));
        return session;
    }

    private MockHttpSession adminSession() throws Exception {
        String login = unique("teacher");
        accounts.bootstrapAdmin("Преподаватель", login, "Admin-Pass-0001");
        return login(login, "Admin-Pass-0001", "/admin/students");
    }

    private static String unique(String prefix) {
        return prefix.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
