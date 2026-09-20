package com.anil.logging.filter;

import com.anil.logging.context.MdcKeys;
import com.anil.logging.logging.HttpLogWriter;
import com.anil.logging.model.HttpLogEvent;
import com.anil.logging.model.LogCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = SecurityFilterOrderIntegrationTest.Config.class)
@AutoConfigureMockMvc
class SecurityFilterOrderIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    RecordingWriter writer;

    @Autowired
    SecureController controller;

    @BeforeEach
    void reset() {
        writer.events.clear();
        controller.mdcUser = null;
    }

    @Test
    void authenticatedIdentityIsAvailableToHttpEventsAndApplicationMdc() throws Exception {
        String credentials = java.util.Base64.getEncoder()
                .encodeToString("demo-user:password".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/secure").header("Authorization", "Basic " + credentials))
                .andExpect(status().isOk());

        assertThat(controller.mdcUser).isEqualTo("demo-user");
        assertThat(writer.events).hasSize(2);
        assertThat(writer.events).allSatisfy(event -> assertThat(event.getFields())
                .containsEntry("authenticated", true)
                .containsEntry("user_id", "demo-user")
                .containsEntry("role_ids", List.of("ADMIN")));
        assertThat(writer.events).extracting(HttpLogEvent::getType)
                .containsExactly(LogCategory.HTTP_REQUEST, LogCategory.HTTP_RESPONSE);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class Config {
        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults())
                    .build();
        }

        @Bean
        UserDetailsService users() {
            return new InMemoryUserDetailsManager(User.withUsername("demo-user")
                    .password("{noop}password")
                    .roles("ADMIN")
                    .build());
        }

        @Bean
        RecordingWriter recordingWriter() {
            return new RecordingWriter();
        }

        @Bean
        SecureController secureController() {
            return new SecureController();
        }
    }

    @RestController
    static class SecureController {
        volatile String mdcUser;

        @GetMapping("/secure")
        String secure() {
            mdcUser = MDC.get(MdcKeys.USER_ID);
            return "ok";
        }
    }

    static class RecordingWriter implements HttpLogWriter {
        private final List<HttpLogEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void write(HttpLogEvent event) {
            events.add(event);
        }
    }
}
