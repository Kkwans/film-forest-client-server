package com.filmforest.content.service;

import com.filmforest.common.exception.BusinessException;
import com.filmforest.content.entity.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class RegistrationInvitationService {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private final JdbcTemplate jdbcTemplate;
    private final UserService userService;
    private final Clock clock;

    public RegistrationInvitationService(JdbcTemplate jdbcTemplate, UserService userService) {
        this(jdbcTemplate, userService, Clock.systemDefaultZone());
    }

    RegistrationInvitationService(JdbcTemplate jdbcTemplate, UserService userService, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.userService = userService;
        this.clock = clock;
    }

    public InvitationState validate(String rawToken) {
        if (!isTokenShapeValid(rawToken)) return InvitationState.invalid();
        List<InvitationRow> rows = query(hash(rawToken), false);
        if (rows.isEmpty()) return InvitationState.invalid();
        InvitationRow invitation = rows.get(0);
        if (!"ACTIVE".equals(invitation.status())
                || !invitation.expiresAt().isAfter(LocalDateTime.now(clock))) {
            return InvitationState.invalid();
        }
        return new InvitationState(true, invitation.expiresAt());
    }

    @Transactional(rollbackFor = Exception.class)
    public User register(String rawToken, String username, String password, String email) {
        if (!isTokenShapeValid(rawToken)) throw invalidInvitation();
        List<InvitationRow> rows = query(hash(rawToken), true);
        if (rows.isEmpty()) throw invalidInvitation();
        InvitationRow invitation = rows.get(0);
        if (!"ACTIVE".equals(invitation.status())
                || !invitation.expiresAt().isAfter(LocalDateTime.now(clock))) {
            throw invalidInvitation();
        }

        User user = userService.register(username.trim(), password, normalizeOptional(email));
        int updated = jdbcTemplate.update("""
                UPDATE registration_invitation
                   SET status = 'USED', used_by = ?, used_at = CURRENT_TIMESTAMP
                 WHERE id = ? AND status = 'ACTIVE' AND expires_at > CURRENT_TIMESTAMP
                """, user.getId(), invitation.id());
        if (updated != 1) throw invalidInvitation();
        return user;
    }

    private List<InvitationRow> query(String tokenHash, boolean lock) {
        String sql = """
                SELECT id, status, expires_at
                  FROM registration_invitation
                 WHERE token_hash = ?
                """ + (lock ? " FOR UPDATE" : "");
        return jdbcTemplate.query(sql, (rs, rowNum) -> new InvitationRow(
                rs.getLong("id"), rs.getString("status"),
                rs.getTimestamp("expires_at").toLocalDateTime()), tokenHash);
    }

    private static boolean isTokenShapeValid(String token) {
        return token != null && TOKEN_PATTERN.matcher(token).matches();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BusinessException invalidInvitation() {
        return new BusinessException("邀请无效、已使用或已过期");
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private record InvitationRow(Long id, String status, LocalDateTime expiresAt) {}

    public record InvitationState(boolean valid, LocalDateTime expiresAt) {
        static InvitationState invalid() {
            return new InvitationState(false, null);
        }
    }
}
