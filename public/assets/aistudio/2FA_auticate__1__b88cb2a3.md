# Enterprise-Grade Secure Login & Two-Factor Authentication (2FA)

**Objective:** Implement a robust, production-ready authentication and identity security system for the application, following modern security standards and OWASP recommendations.

## 1. Primary Login System
Implement a secure login interface supporting:
- Email address or username and password.
- Password visibility toggle.
- Strong password validation.
- Secure password reset functionality.
- Account lockout after repeated failed login attempts.
- Login activity tracking.
- Protection against brute-force attacks and credential stuffing.
- Optional biometric authentication (Fingerprint / Face Unlock).

## 2. Two-Factor Authentication (2FA)

Implement mandatory 2FA enrollment with configurable verification methods:

**Supported methods:**
- Authenticator apps (Google Authenticator, Microsoft Authenticator, Authy) using TOTP.
- Email OTP verification.
- SMS OTP as an optional fallback.
- Backup recovery codes.

**Authentication workflow:**
1. User enters username/email and password.
2. Server validates primary credentials.
3. System generates a short-lived authentication challenge.
4. User is redirected to the 2FA verification screen.
5. User enters the six-digit authenticator code.
6. Server independently validates the TOTP code.
7. If valid, issue the authenticated session.
8. If invalid, deny access and record the security event.

## 3. Authenticator App Enrollment
- Generate a unique cryptographically secure TOTP secret for every account.
- Generate a QR code compatible with standard authenticator applications.
- Support RFC 6238 TOTP.
- Use a 30-second time interval and six-digit verification codes.
- Verify the first OTP before activating 2FA.
- Encrypt stored TOTP secrets using secure key management.
- Never expose secrets in application logs or analytics.

## 4. Backup & Account Recovery
- Generate at least 10 single-use recovery codes.
- Store only securely hashed recovery codes.
- Allow users to regenerate recovery codes after re-authentication.
- Notify users when 2FA settings change.
- Implement secure account recovery with identity verification.
- Prevent recovery workflows from becoming an authentication bypass.

## 5. Session & Device Security
- Secure access-token and refresh-token management.
- Short-lived access tokens and rotating refresh tokens.
- Secure token storage using Android Keystore.
- Automatic session expiration after inactivity.
- Device management dashboard showing active sessions.
- Allow users to revoke individual devices or all sessions.
- Detect suspicious login locations and unfamiliar devices.
- Require re-authentication for sensitive account operations.

## 6. Security Architecture
Follow these requirements:
- OWASP ASVS security recommendations.
- OWASP Mobile Application Security Testing Guide.
- TLS 1.3 for network communication where supported.
- Server-side authentication and authorization.
- Rate limiting for login and OTP verification.
- Cryptographically secure random number generation.
- Protection against replay attacks.
- CSRF protection where applicable.
- Secure error handling without revealing whether an account exists.
- Never store plaintext passwords, OTPs, or authentication secrets.

## 7. Android Jetpack Compose UI
Create a professional authentication experience with:
- Modern institutional-grade login screen.
- Financial Dark and Institutional Light theme support.
- Six-digit OTP input interface.
- Countdown timer for OTP validity.
- Resend verification option with rate limiting.
- QR-code enrollment screen.
- Backup-code management screen.
- Biometric authentication option.
- Security settings and active-device management.
- Accessible UI with responsive layouts.

## 8. Backend & API Integration
Create secure authentication APIs:
- POST /auth/login
- POST /auth/2fa/verify
- POST /auth/2fa/enroll
- POST /auth/2fa/confirm
- POST /auth/2fa/disable
- POST /auth/2fa/recovery
- POST /auth/token/refresh
- POST /auth/logout
- GET /auth/sessions
- DELETE /auth/sessions/{id}

Use server-side validation, proper authorization checks, secure database storage, and audit logging.

## 9. Security Testing
Perform automated and manual testing for:
- Brute-force attacks.
- OTP replay and OTP reuse.
- Session hijacking.
- Token theft.
- Unauthorized API access.
- Recovery-code reuse.
- Rate-limit bypass.
- Authentication bypass.
- Device revocation.
- Password reset vulnerabilities.

## 10. Implementation Requirements
- Integrate with the existing application architecture.
- Do not break existing login, API connections, or user data.
- Keep authentication logic separate from UI components.
- Use reusable Jetpack Compose components.
- Provide clean, maintainable, documented code.
- Never use mock authentication in production.
- Do not consider client-side OTP validation sufficient.
- Implement automated security tests before deployment.

**Final deliverable:** A fully functional, production-ready login and 2FA authentication system with secure backend validation, authenticator app integration, recovery mechanisms, device management, and comprehensive security testing.

Before implementation, inspect the existing codebase, identify the current authentication architecture, and provide an implementation plan. Do not replace existing authentication infrastructure without explicit justification.