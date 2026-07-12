package com.crm.service;

import com.crm.model.UserAccount;
import com.crm.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class AuthService {
    private final UserRepository users; private final SecureRandom random = new SecureRandom();
    public AuthService(UserRepository users) { this.users = users; }
    public UserAccount register(String name, String email, String password) {
        validateRegistration(name, email, password); String normalized = email.trim().toLowerCase();
        if (users.findByEmail(normalized).isPresent()) throw new IllegalArgumentException("An account already exists for this email address.");
        UserAccount user = new UserAccount(); user.setId(UUID.randomUUID().toString()); user.setFullName(name.trim()); user.setEmail(normalized); user.setCreatedAt(Instant.now()); setPassword(user, password); users.save(user); return user;
    }
    public UserAccount login(String email, String password) {
        UserAccount user = users.findByEmail(email.trim().toLowerCase()).orElseThrow(() -> new IllegalArgumentException("Incorrect email or password."));
        if (!hash(password, user.getPasswordSalt()).equals(user.getPasswordHash())) throw new IllegalArgumentException("Incorrect email or password."); return user;
    }
    /** The caller would normally email this code; locally it is shown once for testing. */
    public String requestPasswordReset(String email) {
        UserAccount user = users.findByEmail(email.trim().toLowerCase()).orElseThrow(() -> new IllegalArgumentException("No account was found for this email address."));
        String code = String.format("%06d", random.nextInt(1_000_000)); user.setResetCodeHash(hash(code, user.getPasswordSalt())); user.setResetCodeExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES)); users.save(user); return code;
    }
    public void resetPassword(String email, String code, String password) {
        UserAccount user = users.findByEmail(email.trim().toLowerCase()).orElseThrow(() -> new IllegalArgumentException("Invalid recovery request."));
        if (user.getResetCodeExpiresAt() == null || Instant.now().isAfter(user.getResetCodeExpiresAt()) || !hash(code, user.getPasswordSalt()).equals(user.getResetCodeHash())) throw new IllegalArgumentException("The code is invalid or has expired.");
        validatePassword(password); setPassword(user, password); user.setResetCodeHash(null); user.setResetCodeExpiresAt(null); users.save(user);
    }
    public void updateProfile(UserAccount user, String fullName) {
        if (fullName == null || fullName.trim().length() < 2) throw new IllegalArgumentException("Enter your full name.");
        user.setFullName(fullName.trim()); users.save(user);
    }
    public void changePassword(UserAccount user, String currentPassword, String newPassword) {
        if (!hash(currentPassword, user.getPasswordSalt()).equals(user.getPasswordHash())) throw new IllegalArgumentException("The current password is incorrect.");
        validatePassword(newPassword); setPassword(user, newPassword); users.save(user);
    }
    private void validateRegistration(String name, String email, String password) { if (name == null || name.trim().length() < 2) throw new IllegalArgumentException("Enter your full name."); if (email == null || !email.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new IllegalArgumentException("Enter a valid email address."); validatePassword(password); }
    private void validatePassword(String password) { if (password == null || password.length() < 8) throw new IllegalArgumentException("The password must be at least 8 characters long."); }
    private void setPassword(UserAccount user, String password) { byte[] salt = new byte[16]; random.nextBytes(salt); user.setPasswordSalt(Base64.getEncoder().encodeToString(salt)); user.setPasswordHash(hash(password, user.getPasswordSalt())); }
    private String hash(String value, String salt) { try { PBEKeySpec spec = new PBEKeySpec(value.toCharArray(), Base64.getDecoder().decode(salt), 120_000, 256); return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded()); } catch (Exception e) { throw new IllegalStateException("Password protection error", e); } }
}
