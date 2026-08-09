package com.filmforest.content.service;

import com.filmforest.common.exception.BusinessException;
import com.filmforest.content.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistrationInvitationServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-10T01:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private static final String TOKEN = "abcdefghijklmnopqrstuvwxyzABCDEFGH123456789";

    @Test
    void springContextUsesTheProductionConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class));
            context.registerBean(UserService.class, () -> mock(UserService.class));
            context.register(RegistrationInvitationService.class);
            context.refresh();

            assertThat(context.getBean(RegistrationInvitationService.class)).isNotNull();
        }
    }

    @Test
    void rejectsMalformedTokenWithoutDatabaseLookup() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UserService userService = mock(UserService.class);
        RegistrationInvitationService service = new RegistrationInvitationService(
                jdbcTemplate, userService, FIXED_CLOCK);

        assertThat(service.validate("short").valid()).isFalse();
        assertThatThrownBy(() -> service.register("short", "family", "secret12", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("邀请无效");
        verify(jdbcTemplate, never()).query(anyString(), any(RowMapper.class), any(Object[].class));
        verify(userService, never()).register(anyString(), anyString(), any());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void consumesActiveInvitationAfterUserIsCreated() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UserService userService = mock(UserService.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("id")).thenReturn(17L);
        when(resultSet.getString("status")).thenReturn("ACTIVE");
        when(resultSet.getTimestamp("expires_at"))
                .thenReturn(Timestamp.valueOf("2026-08-11 09:00:00"));
        doAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(resultSet, 0));
        }).when(jdbcTemplate).query(anyString(), any(RowMapper.class), any(Object[].class));
        User user = new User();
        user.setId(23L);
        user.setUsername("family");
        when(userService.register("family", "secret12", null)).thenReturn(user);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        RegistrationInvitationService service = new RegistrationInvitationService(
                jdbcTemplate, userService, FIXED_CLOCK);

        User created = service.register(TOKEN, " family ", "secret12", " ");

        assertThat(created.getId()).isEqualTo(23L);
        verify(userService).register("family", "secret12", null);
        verify(jdbcTemplate).update(anyString(), any(Object[].class));
    }
}
