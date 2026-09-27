package com.cph.alpharoute.data.auth;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cph.alpharoute.BuildConfig;
import com.cph.alpharoute.domain.AuthUser;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.OAuthProvider;

/**
 * Who is signed in (Version 15).
 *
 * <p><b>This class is why the app does not depend on being signed in.</b> Every other feature works with no
 * account at all - the app is local-first (AD-24), and an account adds sync rather than unlocking the app.
 * Nothing here is on the path of a write, a completion or a route.
 *
 * <h3>The signed-in user is LiveData, and it is the only source of truth about it</h3>
 *
 * <p>{@link FirebaseAuth#addAuthStateListener} fires once immediately with the current state and again on
 * every change, so a screen that observes {@link #observeUser()} needs no separate "is anyone signed in?"
 * check and cannot disagree with the profile card.
 *
 * <h3>Version 15 - sign-in moved to the browser</h3>
 *
 * <p>Versions up to 14 used Credential Manager, which asks the on-device Google account manager
 * (Play Services) for a token silently, in-app. That path turned out to be the least reliable part of
 * this feature in practice: it depends on Play Services having a healthy, already-authenticated cached
 * credential for the chosen account, and a device where that cache is stale or corrupted - a common,
 * ordinary state, not a misconfiguration - fails with {@code NoCredentialException} or a
 * {@code BAD_AUTHENTICATION / Long live credential not available} error from inside Play Services
 * itself. Neither is something this app's code can fix; both are the account manager's own state.
 *
 * <p>{@link #signIn} now opens Google's own sign-in page in a Chrome Custom Tab and lets the person log
 * in there, the same way a website would. That page is Google's, running on Google's servers - it does
 * not touch the device's cached account credentials at all, so it works whether or not Play Services'
 * local cache is in good shape. Firebase runs this end to end through
 * {@link FirebaseAuth#startActivityForSignInWithProvider}; nothing else in this class changed.
 *
 * <h3>Why the web client ID is read from BuildConfig</h3>
 *
 * <p>Google Sign-In needs the <b>web</b> OAuth client id, not the Android one - Firebase exchanges a Google
 * ID token for a session and only the web client can mint one. That value appears in
 * {@code google-services.json} only after the app's SHA-1 fingerprint has been registered in the Firebase
 * console.
 *
 * <p>So {@link #isConfigured()} can be false, and that is an ordinary state rather than an error:
 * <b>the app builds and runs before the console setup is finished</b>, and the profile card says sign-in is
 * not set up yet instead of throwing DEVELOPER_ERROR at a user who cannot do anything about it.
 */
public class AuthRepository {

    private static final String TAG = "AlphaRouteAuth";

    /** Firebase's own error code for "the person closed the browser tab before finishing". */
    private static final String ERROR_CODE_WEB_CONTEXT_CANCELED = "ERROR_WEB_CONTEXT_CANCELED";

    private final FirebaseAuth firebaseAuth;
    private final MutableLiveData<AuthUser> user = new MutableLiveData<>();

    public AuthRepository(@NonNull Context appContext) {
        // The Context parameter is kept for callers that construct this the way they always have
        // (AlphaRouteApp passes itself) - nothing here holds onto it any more, since the browser flow
        // needs only the Activity handed to signIn(), never a stored application context.
        this.firebaseAuth = FirebaseAuth.getInstance();

        // Fires immediately with the current state, so the LiveData is correct before any screen observes
        // it - a restored session is signed in from the first frame, with no flash of "signed out".
        firebaseAuth.addAuthStateListener(auth -> user.setValue(toAuthUser(auth.getCurrentUser())));
    }

    /**
     * Who is signed in, observed.
     *
     * <p>{@code null} means signed out. There is no "empty user" - see {@link AuthUser}.
     */
    @NonNull
    public LiveData<AuthUser> observeUser() {
        return user;
    }

    /** The signed-in user right now, or {@code null}. */
    @Nullable
    public AuthUser currentUser() {
        return toAuthUser(firebaseAuth.getCurrentUser());
    }

    /**
     * Whether Google Sign-In can be attempted at all.
     *
     * <p>False until the SHA-1 fingerprint is registered in the Firebase console and
     * {@code google-services.json} is re-downloaded. The profile card reads this and explains the step
     * rather than offering a button that cannot work.
     */
    public boolean isConfigured() {
        return !BuildConfig.GOOGLE_WEB_CLIENT_ID.isEmpty();
    }

    // ------------------------------------------------------------------ signing in

    /**
     * Starts the Google sign-in flow, in a Chrome Custom Tab over Google's own sign-in page.
     *
     * <p>Needs an {@link Activity} rather than a bare {@link Context}: the browser tab is launched with
     * {@code startActivityForResult} under the hood, which only an Activity can do. The Settings screen
     * is the only caller, and it already has one ({@code requireActivity()}).
     *
     * <p><b>The callback reports why it failed, in the user's terms.</b> Closing the tab before finishing
     * and every other kind of failure are told apart, because only one of them is the user's own choice.
     */
    public void signIn(@NonNull Activity activity, @NonNull SignInCallback callback) {
        if (!isConfigured()) {
            callback.onFailed(SignInFailure.NOT_CONFIGURED, "no web client id in google-services.json");
            return;
        }

        OAuthProvider provider = OAuthProvider.newBuilder("google.com").build();

        firebaseAuth.startActivityForSignInWithProvider(activity, provider)
                .addOnSuccessListener(result -> {
                    // The auth state listener publishes the new user; nothing is set here, so there is
                    // exactly one place that decides who is signed in.
                    callback.onSucceeded();
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "The browser sign-in flow failed", e);
                    String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    callback.onFailed(classify(e), detail);
                });
    }

    /** Turns a sign-in failure into something the user can act on. */
    @NonNull
    private static SignInFailure classify(@NonNull Exception e) {
        if (e instanceof FirebaseAuthException
                && ERROR_CODE_WEB_CONTEXT_CANCELED.equals(((FirebaseAuthException) e).getErrorCode())) {
            // The user closed the browser tab before finishing. Not a failure to report as one.
            return SignInFailure.CANCELLED;
        }
        // Everything else - including a SHA-1 that is not registered, and any problem on Google's own
        // sign-in page. There is no "no account on this device" case any more: the browser page lets the
        // person type in or choose any account, so that failure mode does not exist for this flow.
        return SignInFailure.FAILED;
    }

    // ------------------------------------------------------------------ signing out

    /**
     * Signs out.
     *
     * <p><b>It clears the Firebase session only.</b> It does not touch the local database - the stores and
     * the completion history stay exactly where they are. This app is local-first: signing out means "stop
     * syncing", not "forget my route". Anything else would be a data-loss bug dressed up as a logout.
     */
    public void signOut() {
        firebaseAuth.signOut();
    }

    // ------------------------------------------------------------------ mapping

    @Nullable
    private static AuthUser toAuthUser(@Nullable FirebaseUser firebaseUser) {
        if (firebaseUser == null) {
            return null;
        }
        String photo = firebaseUser.getPhotoUrl() == null
                ? null
                : firebaseUser.getPhotoUrl().toString();

        return new AuthUser(
                firebaseUser.getUid(),
                firebaseUser.getDisplayName(),
                firebaseUser.getEmail(),
                photo);
    }

    /** What happened when sign-in was attempted. */
    public enum SignInFailure {
        /** Dismissed by the user. Nothing is wrong. */
        CANCELLED,
        /** The device has no Google account signed in. */
        NO_ACCOUNT,
        /** The Firebase console setup is not finished - the SHA-1 is not registered. */
        NOT_CONFIGURED,
        /** Anything else. */
        FAILED
    }

    /** The result of a sign-in attempt. */
    public interface SignInCallback {
        void onSucceeded();

        void onFailed(@NonNull SignInFailure failure, @NonNull String detail);
    }
}
