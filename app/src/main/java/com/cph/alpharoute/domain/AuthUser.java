package com.cph.alpharoute.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Who is signed in (Version 14).
 *
 * <p>Plain values rather than a {@code FirebaseUser}, so the screens and the rules that read them are not
 * tied to Firebase - and so anything that needs to reason about the signed-in user is testable on the JVM
 * (AD-11).
 *
 * <p><b>There is no "signed out" instance with empty fields.</b> Signed-out is {@code null}, and the
 * callers treat it as such. An AuthUser with a blank uid would be a value that looks like a user and
 * identifies nobody, and that is exactly the kind of thing that ends up as a Firestore path.
 */
public final class AuthUser {

    private final String uid;
    private final String displayName;
    private final String email;
    @Nullable
    private final String photoUrl;

    /**
     * @param uid         Firebase's stable id for this account. The Firestore document path is built from
     *                    it, so it is never blank
     * @param displayName the name from the Google account, or an empty string when the account has none
     * @param email       the address, or an empty string
     * @param photoUrl    the avatar URL, or {@code null}. A Google account without a picture is ordinary,
     *                    so this is the one field that is genuinely optional
     */
    public AuthUser(@Nullable String uid,
                    @Nullable String displayName,
                    @Nullable String email,
                    @Nullable String photoUrl) {
        this.uid = uid == null ? "" : uid;
        this.displayName = displayName == null ? "" : displayName;
        this.email = email == null ? "" : email;
        this.photoUrl = (photoUrl == null || photoUrl.trim().isEmpty()) ? null : photoUrl;
    }

    /** Firebase's stable id for this account. Never blank for a real user. */
    @NonNull
    public String uid() {
        return uid;
    }

    /** The name to show, or an empty string. */
    @NonNull
    public String displayName() {
        return displayName;
    }

    /** The email address, or an empty string. */
    @NonNull
    public String email() {
        return email;
    }

    /** The avatar URL, or {@code null} when the account has no picture. */
    @Nullable
    public String photoUrl() {
        return photoUrl;
    }

    /**
     * What to put on the profile card's title line.
     *
     * <p>Falls back to the email and then to "Signed in" rather than showing a blank line. A Google account
     * can genuinely have no display name, and a profile card with an empty heading looks broken.
     */
    @NonNull
    public String label() {
        if (!displayName.isEmpty()) {
            return displayName;
        }
        if (!email.isEmpty()) {
            return email;
        }
        return "Signed in";
    }

    /** Whether this identifies a real account. */
    public boolean isValid() {
        return !uid.isEmpty();
    }

    @NonNull
    @Override
    public String toString() {
        // Deliberately omits the email and the uid: this ends up in logcat, and a log is a place a
        // person's address should not be.
        return "AuthUser(" + (displayName.isEmpty() ? "unnamed" : displayName) + ")";
    }
}
