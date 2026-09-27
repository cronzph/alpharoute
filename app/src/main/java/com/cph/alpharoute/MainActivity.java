package com.cph.alpharoute;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.cph.alpharoute.domain.GeoCoordinate;
import com.cph.alpharoute.ui.completion.CompletionConfirmDialogFragment;
import com.cph.alpharoute.ui.dashboard.DashboardFragment;
import com.cph.alpharoute.ui.history.CompletionDetailFragment;
import com.cph.alpharoute.ui.history.DailyHistoryFragment;
import com.cph.alpharoute.domain.AuthUser;
import com.cph.alpharoute.ui.history.HistoryFragment;
import com.cph.alpharoute.ui.history.RoundReportFragment;
import com.cph.alpharoute.ui.route.OptimizedRouteFragment;
import com.cph.alpharoute.ui.map.MapFragment;
import com.cph.alpharoute.ui.settings.HomeSetupFragment;
import com.cph.alpharoute.ui.settings.SettingsFragment;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * The single Activity for the whole application (architecture decision AD-1).
 *
 * <p>Every screen is a Fragment hosted here. Tab switching uses plain {@link FragmentManager}
 * add / show / hide transactions - the Navigation Component is deliberately not used, so there is no
 * navigation graph XML in this project.
 *
 * <p>Tabs are never removed, only hidden, so each tab keeps its scroll position and state while the
 * user moves between them.
 *
 * <p>The toolbar is a plain {@code MaterialToolbar}, not a support action bar. There is no menu yet,
 * so calling {@code setSupportActionBar} would only add AppCompat title handling that would fight
 * the per-tab titles set below.
 *
 * <p><b>One intent is handled here rather than by a tab.</b> The arrival notification carries a store id,
 * and tapping it has to open the completion dialog - see {@link #EXTRA_CONFIRM_STORE_ID}. That is a
 * window-level concern, not a screen's, which is why it lives in the Activity.
 */
public class MainActivity extends AppCompatActivity {

    /**
     * The store to open the completion dialog for.
     *
     * <p>Put on the arrival notification's intent by {@code NavigationNotifications}. The dialog cannot
     * guess the store, and the notification is the app's only way to reach the driver while they are
     * standing in it with Google Maps in front.
     *
     * <p><b>Public, because the contract is the Activity's own.</b> {@code NavigationNotifications} is
     * package-private to {@code navigation/}, so it could not declare a constant this class reads.
     * Declaring it here keeps one name for one extra rather than a literal in two packages.
     */
    public static final String EXTRA_CONFIRM_STORE_ID = "extra_confirm_store_id";

    /** Key used to remember the active tab across rotation and process death. */
    private static final String STATE_ACTIVE_TAG = "state_active_tag";

    private static final String TAG_DASHBOARD = "tag_dashboard";
    private static final String TAG_MAP = "tag_map";
    private static final String TAG_HISTORY = "tag_history";
    private static final String TAG_SETTINGS = "tag_settings";

    /**
     * Home Setup, a pushed screen rather than a tab.
     *
     * <p>Deliberately NOT in {@link #ALL_TAGS}: it is a step reached from Settings, not a destination, so
     * tab switching must neither hide it nor show it. See {@link #push}.
     */
    private static final String TAG_HOME_SETUP = "tag_home_setup";

    /** Every tab, in menu order. Used to hide the tabs that are not being shown. */
    private static final String[] ALL_TAGS = {TAG_DASHBOARD, TAG_MAP, TAG_HISTORY, TAG_SETTINGS};

    private MaterialToolbar toolbar;
    private BottomNavigationView bottomNav;
    private String activeTag;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Must run BEFORE super.onCreate so the library can take over the splash theme and swap the
        // activity to postSplashScreenTheme once the first frame is ready.
        SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);

        // Draw behind the system bars, then inset the content so nothing hides under them.
        // EdgeToEdge also sets both bars transparent and picks the correct light/dark icon
        // appearance for the current UI mode, which is why the theme does not set them.
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root_main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        toolbar = findViewById(R.id.toolbar);
        bottomNav = findViewById(R.id.bottom_nav);

        if (savedInstanceState == null) {
            // Cold start: Dashboard is the default tab, and it is also the first item in the menu,
            // so BottomNavigationView's own default selection already agrees with it.
            activeTag = TAG_DASHBOARD;
            showTab(TAG_DASHBOARD);
        } else {
            // Recreated after rotation or process death. FragmentManager has already restored the
            // fragments and BottomNavigationView has restored its selected item, so the only thing
            // left to restore is which tab is active, so the toolbar title matches.
            activeTag = savedInstanceState.getString(STATE_ACTIVE_TAG, TAG_DASHBOARD);
        }
        updateToolbarTitle(activeTag);

        // Attached AFTER the restore above. If it were attached first, restoring the selected item
        // could fire this listener and add a second copy of a fragment that FragmentManager had
        // already recreated.
        bottomNav.setOnItemSelectedListener(item -> {
            String tag = tagForMenuItem(item.getItemId());
            if (!tag.equals(activeTag)) {
                activeTag = tag;
                showTab(tag);
                updateToolbarTitle(tag);
            }
            return true;
        });

        // Watches the back stack, so the toolbar and the bottom navigation come back when a pushed screen
        // is closed. One listener covers both ways out - the screen's own Back action and the system back
        // button - because both end in the same popBackStack.
        getSupportFragmentManager().addOnBackStackChangedListener(this::syncChromeToBackStack);

        // Applied once immediately, and that is what restores the chrome after a rotation that happened
        // with Home Setup open: by this point the fragment has already been restored by super.onCreate, so
        // no event is ever going to fire for it.
        syncChromeToBackStack();

        // A cold start from the arrival notification. Skipped when savedInstanceState is non-null: on a
        // rotation the FragmentManager has already restored the dialog, and opening it again here would
        // stack a second copy on top of the restored one.
        if (savedInstanceState == null) {
            handleCompletionIntent(getIntent());
        }

    }

    /**
     * A warm start from the arrival notification.
     *
     * <p>The notification's {@code PendingIntent} uses {@code FLAG_ACTIVITY_SINGLE_TOP}, so tapping it
     * while the app is already running behind Google Maps delivers the intent here instead of creating a
     * second Activity. Without this override the extra would be read by nothing and the tap would appear
     * to do nothing at all.
     */
    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        // Recorded as the current intent, so a later rotation re-delivers this one rather than the
        // original launch intent - which would lose the store id.
        setIntent(intent);
        handleCompletionIntent(intent);
    }

    // ------------------------------------------------------------------ the completion dialog

    /**
     * Opens the completion dialog when the app was opened by the arrival notification.
     *
     * <p><b>The store is re-read before the dialog is shown, and a completed store is not offered.</b> The
     * notification is posted at the moment of arrival and may sit in the shade for a while; by the time it
     * is tapped the user may have confirmed the completion from inside the app, or deleted the store.
     * Opening a dialog that could only answer "Already counted" would be asking a question that is
     * already settled, so the tap quietly does nothing instead - the notification is auto-cancelled
     * either way.
     *
     * <p>Consuming the extra is what stops the same tap being handled twice: the intent is the Activity's
     * own and survives until the next one arrives.
     */
    private void handleCompletionIntent(@Nullable Intent intent) {
        if (intent == null) {
            return;
        }

        long storeId = intent.getLongExtra(EXTRA_CONFIRM_STORE_ID, 0L);
        if (storeId <= 0L) {
            return;
        }
        intent.removeExtra(EXTRA_CONFIRM_STORE_ID);

        AlphaRouteApp.from(this).storeRepository().findById(storeId, store -> {
            // The disk read is asynchronous, so the Activity may have gone in the meantime.
            if (isFinishing() || isDestroyed() || store == null || !store.isPending()) {
                return;
            }
            // Two copies of a confirmation dialog would be two chances to tap YES.
            if (getSupportFragmentManager()
                    .findFragmentByTag(CompletionConfirmDialogFragment.TAG) != null) {
                return;
            }

            CompletionConfirmDialogFragment.newInstance(storeId)
                    .show(getSupportFragmentManager(), CompletionConfirmDialogFragment.TAG);
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_ACTIVE_TAG, activeTag);
    }

    /**
     * Shows the tab fragment for {@code tag}, hiding whichever tab fragment was visible before.
     *
     * <p>The fragment is only added the first time; afterwards it is shown and hidden, so its state
     * survives tab changes.
     *
     * <p><b>Only reachable while no pushed screen is open</b>, because opening one hides the bottom
     * navigation ({@link #openHomeSetup()}). That is what keeps a pushed screen from being left on top of
     * a tab the user has just chosen.
     */
    private void showTab(String tag) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction tx = fm.beginTransaction();

        for (String other : ALL_TAGS) {
            if (other.equals(tag)) {
                continue;
            }
            Fragment visible = fm.findFragmentByTag(other);
            if (visible != null && !visible.isHidden()) {
                tx.hide(visible);
            }
        }

        Fragment target = fm.findFragmentByTag(tag);
        if (target == null) {
            tx.add(R.id.fragment_container, newFragmentFor(tag), tag);
        } else if (target.isHidden()) {
            tx.show(target);
        }

        tx.commit();
    }

    /** Creates the tab fragment for a tag. The tag constants and this mapping must stay in step. */
    private Fragment newFragmentFor(String tag) {
        if (TAG_MAP.equals(tag)) {
            return new MapFragment();
        }
        if (TAG_HISTORY.equals(tag)) {
            return new HistoryFragment();
        }
        if (TAG_SETTINGS.equals(tag)) {
            return new SettingsFragment();
        }
        return new DashboardFragment();
    }

    /** Maps a bottom-navigation menu item id to its fragment tag. */
    private String tagForMenuItem(int itemId) {
        if (itemId == R.id.nav_map) {
            return TAG_MAP;
        }
        if (itemId == R.id.nav_history) {
            return TAG_HISTORY;
        }
        if (itemId == R.id.nav_settings) {
            return TAG_SETTINGS;
        }
        return TAG_DASHBOARD;
    }

    /** Maps a fragment tag back to its menu item id. */
    private int menuItemIdFor(String tag) {
        if (TAG_MAP.equals(tag)) {
            return R.id.nav_map;
        }
        if (TAG_HISTORY.equals(tag)) {
            return R.id.nav_history;
        }
        if (TAG_SETTINGS.equals(tag)) {
            return R.id.nav_settings;
        }
        return R.id.nav_dashboard;
    }

    /** Sets the toolbar title from the menu item's own title, so the two can never disagree. */
    private void updateToolbarTitle(String tag) {
        MenuItem item = bottomNav.getMenu().findItem(menuItemIdFor(tag));
        if (item != null) {
            toolbar.setTitle(item.getTitle());
        }
    }

    // ------------------------------------------------------------------ pushed screens

    /**
     * Pushes a screen on top of the tabs.
     *
     * <p>Called by {@code SettingsFragment} (Home Setup), {@code HistoryFragment} (Daily History) and
     * {@code DailyHistoryFragment} (Completion Detail). A pushed screen is a <i>step</i> rather than a
     * destination: it goes on the back stack and the bottom navigation is hidden while it is open - the way
     * out is back to where the user came from, and a tab bar offering four other places would contradict
     * that.
     *
     * <p><b>The tab underneath is hidden rather than left running.</b> That is how tab switching already
     * treats it, and it is what stops a covered screen from carrying on as though it were still visible.
     * A pushed screen is never hidden or shown by {@link #showTab}, because it is not in {@link #ALL_TAGS}
     * - and because the hidden bottom bar means a tab can never be tapped while it is open.
     *
     * <p>Ignored when a screen with the same tag is already open, so a double tap cannot put two identical
     * screens on the back stack.
     *
     * @param fragment the screen to push. It must have a public no-arg constructor
     * @param tag      its back-stack tag, and the tag the chrome lookup uses
     * @param titleRes the toolbar title for as long as it is open
     */
    public void push(@NonNull Fragment fragment, @NonNull String tag, @StringRes int titleRes) {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentByTag(tag) != null) {
            // Already open. Pushing a second copy would put two identical screens on the back stack.
            return;
        }

        FragmentTransaction tx = fm.beginTransaction();

        // Hide EVERYTHING that is currently visible, not just the active TAB.
        //
        // This was the bug. The old code hid `findFragmentByTag(activeTag)` - the tab - which is correct
        // only when the pushed screen goes directly on top of a tab. It is wrong the moment two screens
        // are pushed in a chain: History tab -> Day history -> Completion hides the TAB (already hidden)
        // and leaves Day history visible underneath.
        //
        // It was invisible for six versions because every screen root had an opaque
        // `?attr/colorSurface` background, so the fragment underneath could not be seen. Glassmorphism
        // made the roots transparent for the gradient, and the bug appeared - a real one, exposed rather
        // than caused.
        //
        // Asking the FragmentManager what is actually visible is the robust form: it needs no tag
        // bookkeeping to be kept in step, and it cannot go stale.
        for (Fragment existing : fm.getFragments()) {
            if (existing != null && existing != fragment && !existing.isHidden()) {
                tx.hide(existing);
            }
        }

        tx.add(R.id.fragment_container, fragment, tag);
        tx.addToBackStack(tag);
        tx.commit();

        // Set the chrome here rather than waiting for the back-stack listener, so the screen never appears
        // for a frame with the wrong title and a bottom bar it should not have.
        applyPushedChrome(titleRes);
    }

    /** Opens Home Setup. Kept as a named method because Settings calls it directly. */
    public void openHomeSetup() {
        push(new HomeSetupFragment(), TAG_HOME_SETUP, R.string.title_home_setup);
    }

    /** Puts the chrome back when the top pushed screen is popped. */
    private void syncChromeToBackStack() {
        FragmentManager fm = getSupportFragmentManager();
        int count = fm.getBackStackEntryCount();
        if (count == 0) {
            applyPushedChrome(0);
            return;
        }

        // The title comes from the fragment INSTANCE rather than from a map of tags kept in a field.
        // A field would be empty after a process death, while the back stack is restored by the framework
        // - so the screen would come back with no title at all. Asking the fragment cannot go stale.
        FragmentManager.BackStackEntry top = fm.getBackStackEntryAt(count - 1);
        Fragment topFragment = top.getName() == null ? null : fm.findFragmentByTag(top.getName());
        applyPushedChrome(titleFor(topFragment));
    }

    /** The toolbar title for a pushed screen, or {@code 0} when it is not one. */
    @StringRes
    private int titleFor(@Nullable Fragment fragment) {
        if (fragment instanceof HomeSetupFragment) {
            return R.string.title_home_setup;
        }
        if (fragment instanceof DailyHistoryFragment) {
            return R.string.title_daily_history;
        }
        if (fragment instanceof CompletionDetailFragment) {
            return R.string.title_completion_detail;
        }
        if (fragment instanceof OptimizedRouteFragment) {
            return R.string.title_optimized_route;
        }
        if (fragment instanceof RoundReportFragment) {
            return R.string.title_round_report;
        }
        return 0;
    }

    /**
     * Hides the bottom navigation and retitles the toolbar while a pushed screen is open, and reverses
     * both when it closes.
     *
     * <p>The toolbar still names the screen - it is retitled rather than left saying "History" - because
     * a screen with no title at all reads as a glitch.
     *
     * @param titleRes the pushed screen's title, or {@code 0} when no pushed screen is open
     */
    private void applyPushedChrome(@StringRes int titleRes) {
        boolean pushed = titleRes != 0;
        bottomNav.setVisibility(pushed ? View.GONE : View.VISIBLE);
        if (pushed) {
            toolbar.setTitle(titleRes);
        } else {
            updateToolbarTitle(activeTag);
        }
    }

    // ------------------------------------------------------------------ cross-tab jumps

    /**
     * Shows the Settings tab.
     *
     * <p>Used by the Dashboard's round-complete card, which points at Settings because that is where the
     * next round begins. A tab switch rather than a second Start-round button: the action exists in one
     * place, and a copy would be a second path to a change that resets every store.
     */
    public void showSettingsTab() {
        // Any pushed screen first, or the tab would be shown underneath one that hides it.
        getSupportFragmentManager()
                .popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

        if (!TAG_SETTINGS.equals(activeTag)) {
            activeTag = TAG_SETTINGS;
            showTab(TAG_SETTINGS);
            updateToolbarTitle(TAG_SETTINGS);
        }
        bottomNav.setSelectedItemId(R.id.nav_settings);
    }

    /**
     * Shows the Map tab, centred on a coordinate.
     *
     * <p><b>Pops every pushed screen first.</b> "View on Map" can be reached from Completion Detail, which
     * is two screens deep - and a tab cannot be shown underneath a pushed screen, because the pushed screen
     * hides it. Popping to the tabs is what makes the map actually visible.
     *
     * <p><b>The coordinate goes through the pasted-link channel</b> rather than a new one. The Map tab
     * already observes it and moves its camera, so this needs no new wiring on that side - and a second
     * channel would mean a second observer deciding when to move the camera, which is exactly the kind of
     * duplicate rule this project avoids.
     */
    public void showMapAt(@NonNull GeoCoordinate coordinate) {
        getSupportFragmentManager()
                .popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

        // setSelectedItemId fires the listener only when the selection actually changes, so the case where
        // the Map tab is already active is handled explicitly rather than left to the listener.
        if (!TAG_MAP.equals(activeTag)) {
            activeTag = TAG_MAP;
            showTab(TAG_MAP);
            updateToolbarTitle(TAG_MAP);
        }
        bottomNav.setSelectedItemId(R.id.nav_map);

        AlphaRouteApp.from(this).placeLinkRepository().showCoordinate(coordinate);
    }
}