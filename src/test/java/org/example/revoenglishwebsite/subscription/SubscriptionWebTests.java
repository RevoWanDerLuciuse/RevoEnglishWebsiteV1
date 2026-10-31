package org.example.revoenglishwebsite.subscription;

import org.example.revoenglishwebsite.account.AccountService;
import org.example.revoenglishwebsite.account.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Subscription management over HTTP: who may do what, and what students see. */
@SpringBootTest(properties = "site.telegram-username=test_teacher")
@AutoConfigureMockMvc
class SubscriptionWebTests {

    private static final String STUDENT_PASSWORD = "Student-Own-Pass-1";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private AccountService accounts;
    @Autowired
    private UserAccountRepository accountRepository;
    @Autowired
    private SubscriptionRepository subscriptionRepository;
    @Autowired
    private LessonCreditService credits;
    @Autowired
    private BusinessCalendar calendar;

    private String adminLogin;
    private String studentLogin;
    private Long studentId;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        adminLogin = unique("teacher");
        accounts.bootstrapAdmin("Эмир", adminLogin, "Admin-Pass-0001");
        studentLogin = unique("anna");
        studentId = newStudent("Анна", studentLogin);
        today = calendar.today();
    }

    @Test
    void administratorAssignsRecordsPaymentAndActivates() throws Exception {
        MockHttpSession admin = login(adminLogin, "Admin-Pass-0001");
        String base = "/admin/students/" + studentId + "/subscriptions";
        String formKey = UUID.randomUUID().toString();

        mvc.perform(post(base).session(admin).with(csrf()).param("planCode", "regular")
                        .param("startsOn", today.toString()).param("expiresOn", today.plusMonths(1).minusDays(1).toString())
                        .param("formKey", formKey))
                .andExpect(redirectedUrl(base));
        assertThat(page(admin, base)).contains("Абонемент назначен");
        // the same form submitted twice: still one subscription
        mvc.perform(post(base).session(admin).with(csrf()).param("planCode", "regular")
                .param("startsOn", today.toString()).param("expiresOn", today.plusMonths(1).minusDays(1).toString())
                .param("formKey", formKey));
        assertThat(subscriptionRepository.findByStudentIdOrderByCreatedAtDesc(studentId)).hasSize(1);
        Long subId = subscriptionRepository.findByStudentIdOrderByCreatedAtDesc(studentId).getFirst().getId();

        assertThat(page(admin, base)).contains("Оплата получена", "ожидается");
        mvc.perform(post(base + "/" + subId + "/confirm-payment").session(admin).with(csrf()));
        assertThat(page(admin, base)).contains("Оплата подтверждена. Абонемент активен, начислено занятий: 8.", "получена:");
        mvc.perform(post("/admin/students/" + studentId + "/lessons/consume").session(admin).with(csrf())
                        .param("lessonDate", today.toString()).param("formKey", UUID.randomUUID().toString()))
                .andExpect(redirectedUrl(base));

        String page = page(admin, base);
        assertThat(page).contains("Занятие списано.", "Regular", "Активен", "11 200 ₽", "Начисление по абонементу");
        assertThat(page(admin, base)).as("a result message is shown once").doesNotContain("Занятие списано.");

        // After a double click the next message is the current one, not a queued older one
        for (int i = 0; i < 2; i++) {
            mvc.perform(post(base + "/" + subId + "/confirm-payment").session(admin).with(csrf()));
        }
        mvc.perform(post(base + "/" + subId + "/adjustments").session(admin).with(csrf())
                .param("delta", "-50").param("reason", "Слишком много").param("formKey", UUID.randomUUID().toString()));
        assertThat(page(admin, base)).contains("Баланс не может стать отрицательным.").doesNotContain("Оплата подтверждена");

        // The student sees their own plan, without prices or payment records
        String dashboard = mvc.perform(get("/dashboard").session(login(studentLogin, STUDENT_PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(dashboard).contains("Тариф <strong>Regular</strong>", "Активен")
                .contains("<dt>Всего занятий</dt><dd>8</dd>", "<dt>Использовано</dt><dd>1</dd>", "<dt>Осталось</dt><dd>7</dd>")
                .doesNotContain("11 200", "Оплачено", "перевод");
    }

    @Test
    void studentsWithoutAnActivePlanAreToldToContactTheTeacher() throws Exception {
        String dashboard = mvc.perform(get("/dashboard").session(login(studentLogin, STUDENT_PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(dashboard).contains("нет активного абонемента", "свяжитесь с&nbsp;преподавателем");
    }

    @Test
    void studentsSeeOnlyTheirOwnSubscription() throws Exception {
        Long admin = accountRepository.findByLogin(adminLogin).orElseThrow().getId();
        String otherLogin = unique("boris");
        Long other = newStudent("Борис", otherLogin);
        subscriptionsService().assign(other, "intensive", today, today.plusDays(30), "Подарок Борису", admin, UUID.randomUUID().toString());

        String dashboard = mvc.perform(get("/dashboard").session(login(studentLogin, STUDENT_PASSWORD)).param("studentId", other.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(dashboard).contains("нет активного абонемента").doesNotContain("Intensive", "Борис");
    }

    @Test
    void studentsCannotManageSubscriptionsOrCredits() throws Exception {
        Long admin = accountRepository.findByLogin(adminLogin).orElseThrow().getId();
        Subscription sub = subscriptionsService().assign(studentId, "start", today, today.plusDays(30), "Тест", admin,
                UUID.randomUUID().toString()).value();
        MockHttpSession student = login(studentLogin, STUDENT_PASSWORD);
        String base = "/admin/students/" + studentId;

        mvc.perform(get(base + "/subscriptions").session(student)).andExpect(status().isForbidden());
        mvc.perform(post(base + "/subscriptions").session(student).with(csrf()).param("planCode", "intensive")
                .param("startsOn", today.toString()).param("expiresOn", today.plusDays(30).toString())
                .param("complimentary", "true").param("complimentaryReason", "сам себе")
                .param("formKey", UUID.randomUUID().toString())).andExpect(status().isForbidden());
        mvc.perform(post(base + "/subscriptions/" + sub.getId() + "/adjustments").session(student).with(csrf())
                .param("delta", "10").param("reason", "сам себе").param("formKey", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
        mvc.perform(post(base + "/lessons/consume").session(student).with(csrf())
                .param("lessonDate", today.toString()).param("formKey", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());

        assertThat(subscriptionRepository.findByStudentIdOrderByCreatedAtDesc(studentId)).hasSize(1);
        assertThat(credits.balanceOf(sub.getId())).isEqualTo(new CreditBalance(4, 0, 0, 4));
    }

    @Test
    void anonymousVisitorsAreRedirectedAndFormsNeedCsrf() throws Exception {
        mvc.perform(get("/admin/students/" + studentId + "/subscriptions")).andExpect(redirectedUrl("/login"));
        MockHttpSession admin = login(adminLogin, "Admin-Pass-0001");
        mvc.perform(post("/admin/students/" + studentId + "/subscriptions").session(admin)
                .param("planCode", "start").param("startsOn", today.toString()).param("expiresOn", today.plusDays(30).toString())
                .param("formKey", UUID.randomUUID().toString())).andExpect(status().isForbidden());
        assertThat(subscriptionRepository.findByStudentIdOrderByCreatedAtDesc(studentId)).isEmpty();
    }

    @Test
    void aSubscriptionIdOnlyWorksTogetherWithItsOwnStudent() throws Exception {
        Long admin = accountRepository.findByLogin(adminLogin).orElseThrow().getId();
        Subscription sub = subscriptionsService().assign(studentId, "start", today, today.plusDays(30), null, admin,
                UUID.randomUUID().toString()).value();
        Long other = newStudent("Чужой", unique("other"));
        MockHttpSession session = login(adminLogin, "Admin-Pass-0001");

        mvc.perform(post("/admin/students/" + other + "/subscriptions/" + sub.getId() + "/cancel").session(session).with(csrf())
                        .param("reason", "не тот ученик"))
;
        assertThat(page(session, "/admin/students/" + other + "/subscriptions")).contains("Абонемент не найден.");
        assertThat(subscriptionRepository.findById(sub.getId()).orElseThrow().getStatus()).isEqualTo(SubscriptionStatus.PENDING_PAYMENT);
        mvc.perform(get("/admin/students/999999/subscriptions").session(session)).andExpect(status().isNotFound());
    }

    @Test
    void studentListShowsSubscriptionStatus() throws Exception {
        Long admin = accountRepository.findByLogin(adminLogin).orElseThrow().getId();
        subscriptionsService().assign(studentId, "regular", today, today.plusDays(30), "Пробный", admin, UUID.randomUUID().toString());
        String list = mvc.perform(get("/admin/students").session(login(adminLogin, "Admin-Pass-0001")))
                .andReturn().getResponse().getContentAsString();
        assertThat(list).contains("Regular", "осталось 8 из 8", "/admin/students/" + studentId + "/subscriptions");
    }

    // ----- helpers -----

    @Autowired
    private SubscriptionService subscriptionService;

    private SubscriptionService subscriptionsService() {
        return subscriptionService;
    }

    /** A student who has already replaced the temporary password. */
    private Long newStudent(String name, String login) {
        Long id = accounts.createStudent(name, login, "Temporary-Pass-1").account().getId();
        accounts.changeOwnPassword(id, "Temporary-Pass-1", STUDENT_PASSWORD);
        return id;
    }

    private String page(MockHttpSession session, String path) throws Exception {
        return mvc.perform(get(path).session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private MockHttpSession login(String login, String password) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/login").user("login", login).password(password))
                .andExpect(authenticated()).andReturn().getRequest().getSession(false);
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
