package dev.sergiomarin.sitewatch.verification;

/** Published inside the transaction that creates the token; the email goes out only after it commits. */
record VerificationRequested(String email, String token) {
}
