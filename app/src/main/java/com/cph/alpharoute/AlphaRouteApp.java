package com.cph.alpharoute;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;

import com.cph.alpharoute.data.db.AppDatabase;
import com.cph.alpharoute.data.auth.AuthRepository;
import com.cph.alpharoute.data.repository.BackupRepository;
import com.cph.alpharoute.data.repository.CompletionRepository;
import com.cph.alpharoute.data.repository.PlaceLinkRepository;
import com.cph.alpharoute.data.repository.PlanRepository;
import com.cph.alpharoute.data.repository.RoutingRepository;
import com.cph.alpharoute.data.repository.SettingsRepository;
import com.cph.alpharoute.data.repository.StoreRepository;
import com.cph.alpharoute.data.sync.CloudSyncRepository;
import com.cph.alpharoute.data.sync.NoOpSyncManager;
import com.cph.alpharoute.data.sync.SyncManager;
import com.cph.alpharoute.location.LocationProvider;
import com.cph.alpharoute.navigation.NavigationSession;
import com.cph.alpharoute.navigation.NavigationSessionManager;
import com.cph.alpharoute.ui.map.MapConfig;
import com.cph.alpharoute.util.AppExecutors;

/**
 * The application object, and the app's only dependency container.
 *
 * <p>Architecture decision AD-4: Room is the single source of truth, and these objects are the only route
 * to it. Screens never construct a repository, never open a database and never touch a DAO directly -
 * they ask this class for the repository they need.
 *
 * <p><b>Why hand-written rather than Hilt, Dagger or Koin:</b> there is one user and one device (D-5), and
 * the object graph is a list of assignments that can be read top to bottom. A dependency-injection
 * framework would add a build plugin, generated code and a concept to learn, to replace about forty lines
 * of plain Java. It is worth revisiting only if the graph grows substantially - and it has not, through
 * Phase 4.3.
 *
 * <p><b>What this class exists for right now:</b> nothing else in the app may run code before the
 * database is ready. Building the executors, the database and the repositories here - and creating the
 * settings row with {@link SettingsRepository#ensureDefaults()} - means every later caller can assume
 * all of it already exists.
 */
public class AlphaRouteApp extends Application {

    private AppExecutors executors;
    private AppDatabase database;
    private SyncManager syncManager;
    private LocationProvider locationProvider;
    private StoreRepository storeRepository;
    private CompletionRepository completionRepository;
    private SettingsRepository settingsRepository;
    private RoutingRepository routingRepository;
    private PlaceLinkRepository placeLinkRepository;
    private NavigationSession navigationSession;
    private NavigationSessionManager navigationSessionManager;
    private BackupRepository backupRepository;
    private AuthRepository authRepository;
    private PlanRepository planRepository;
    private CloudSyncRepository cloudSyncRepository;

    @Override
    public void onCreate() {
        super.onCreate();

        // ---------------------------------------------------------------------------------------
        // osmdroid must be configured before ANY MapView is constructed: it resolves its tile cache
        // path while loading its settings, and it must carry the app-specific User-Agent or the
        // OpenStreetMap tile servers will refuse every request. Doing it here makes that ordering
        // guaranteed rather than hopeful. See MapConfig for the full tile policy.
        // ---------------------------------------------------------------------------------------
        MapConfig.initialise(this);

        executors = new AppExecutors();
        database = AppDatabase.getInstance(this);

        // ---------------------------------------------------------------------------------------
        // One location source for the whole app.
        //
        // The blue dot on the map and the arrival check in Phase 5.1 must never disagree about where
        // the user is, so both read this. Owned here rather than by the Map fragment, because Phase 4.3
        // needs it to keep running while the user is driving in the Google Maps app and our screen is
        // nowhere to be seen.
        // ---------------------------------------------------------------------------------------
        locationProvider = new LocationProvider(this);

        // ---------------------------------------------------------------------------------------
        // The sync transport.
        //
        // Phase 2.1 uses a Null Object, so the write path can be built, wired and tested now: every
        // repository already calls onLocalDataChanged() after a successful local write, and the
        // debug log proves the ordering - local commit first, sync notification second.
        //
        // Firestore replaces this single line once the Firebase project exists. It needs that
        // project's own configuration values (project id, application id, API key), which are
        // project specific and must not be invented. Nothing else in the app has to change.
        // ---------------------------------------------------------------------------------------
        syncManager = new NoOpSyncManager();

        // Settings is built FIRST, because StoreRepository needs it: deleting a store has to be able to
        // clear a last-selected reference that would otherwise dangle.
        settingsRepository = new SettingsRepository(database.appSettingDao(), executors, syncManager);

        storeRepository = new StoreRepository(
                database.storeDao(),
                database.storeTransactionDao(),
                database.completionHistoryDao(),
                settingsRepository,
                executors,
                syncManager);

        // The completion transaction (Phase 5.3) and the round transaction (Version 7). Two DAOs because
        // each operation spans more than one table - see CompletionTransactionDao and RoundTransactionDao.
        // This repository is the ONLY writer of completion history, and the only starter of a new round.
        completionRepository = new CompletionRepository(
                database.completionHistoryDao(),
                database.completionTransactionDao(),
                database.roundTransactionDao(),
                settingsRepository,
                executors);

        // ---------------------------------------------------------------------------------------
        // Routing - the only repository that talks to the network.
        //
        // It stores nothing; it holds the current route as LiveData, which is what lets the Route
        // Preview sheet ask for a route and the Map tab draw it without either knowing the other
        // exists. Built here, in the application container, so a route survives a rotation instead of
        // being requested again - which matters, because the free plan allows 2,000 requests a day.
        // ---------------------------------------------------------------------------------------
        routingRepository = new RoutingRepository(executors);

        // Reading a location out of a pasted Google Maps link. A full link needs no network; only a short
        // one does, which is why this is a repository rather than a helper in the parser.
        placeLinkRepository = new PlaceLinkRepository(executors);

        // ---------------------------------------------------------------------------------------
        // The active navigation session (Phase 4.3).
        //
        // Owned here, not by the Map tab, because THREE things have to share it and none of them can hold
        // a reference to the others: the foreground service, which watches for arrival while Google Maps
        // is in front; the Navigation Active sheet, which shows the distance; and the compact bar on the
        // map. Held for the life of the process, so a session survives a rotation and a tab switch.
        //
        // Placed after settingsRepository because the manager writes the persisted reference (AD-10)
        // through it.
        // ---------------------------------------------------------------------------------------
        navigationSession = new NavigationSession();
        navigationSessionManager = new NavigationSessionManager(navigationSession, settingsRepository);

        // Backup and restore (Version 9). The only thing in the app that can delete every store, which is
        // why it gets its own transaction and its own repository rather than a method on an existing one.
        backupRepository = new BackupRepository(
                database.storeDao(),
                database.completionHistoryDao(),
                database.appSettingDao(),
                database.backupTransactionDao(),
                executors);

        // Who is signed in (Version 14). Built from the application context - the repository outlives
        // every screen, and an Activity here would be leaked for the life of the process.
        authRepository = new AuthRepository(this);

        // ---------------------------------------------------------------------------------------
        // Saved route plans (Version 18).
        //
        // The order the user decided, kept in the database. The route_plans and route_plan_stops tables
        // have existed since schema version 3, added ahead of the feature; this is what finally uses them.
        // Built here rather than by the screen so a plan survives every screen that ever touches it.
        // ---------------------------------------------------------------------------------------
        planRepository = new PlanRepository(database.routePlanDao(), executors);

        // ---------------------------------------------------------------------------------------
        // Cloud sync (Version 19) - MANUAL, both directions, and off by default in the sense that
        // nothing happens until a button is pressed.
        //
        // It is NOT a SyncManager. The SyncManager seam fires on every local write, which is the right
        // shape for a background transport and the wrong shape for this: a sync that runs while the user
        // is driving can push a half-finished round with nothing on screen to say so. These are two
        // explicit buttons instead, and the destructive direction shows its numbers before it writes.
        //
        // Built here so it survives every screen; it holds no data of its own, only the Firebase handles.
        // ---------------------------------------------------------------------------------------
        cloudSyncRepository = new CloudSyncRepository(
                backupRepository, storeRepository, completionRepository, executors);

        settingsRepository.ensureDefaults();
    }

    /** Convenience accessor, so a Fragment can write {@code AlphaRouteApp.from(this).storeRepository()}. */
    public static AlphaRouteApp from(@NonNull Context context) {
        return (AlphaRouteApp) context.getApplicationContext();
    }

    public AppExecutors executors() {
        return executors;
    }

    public AppDatabase database() {
        return database;
    }

    public SyncManager syncManager() {
        return syncManager;
    }

    /** The app's single source of location. See the note in {@code onCreate}. */
    public LocationProvider locationProvider() {
        return locationProvider;
    }

    public StoreRepository storeRepository() {
        return storeRepository;
    }

    public CompletionRepository completionRepository() {
        return completionRepository;
    }

    public SettingsRepository settingsRepository() {
        return settingsRepository;
    }

    /** The routing service client. Holds the current route; stores nothing. See the note in onCreate. */
    public RoutingRepository routingRepository() {
        return routingRepository;
    }

    /** Reads a location out of a pasted Google Maps link. Stores nothing. */
    public PlaceLinkRepository placeLinkRepository() {
        return placeLinkRepository;
    }

    /**
     * The active navigation session. See the note in {@code onCreate} for why three things share it.
     */
    public NavigationSession navigationSession() {
        return navigationSession;
    }

    /** Starts and ends navigation sessions. The only writer of the session and the service. */
    public NavigationSessionManager navigationSessionManager() {
        return navigationSessionManager;
    }

    /** Backup and restore (Version 9). */
    public BackupRepository backupRepository() {
        return backupRepository;
    }

    /**
     * Who is signed in (Version 14).
     *
     * <p>Nothing else in the app depends on this. An account adds sync; it does not unlock anything.
     */
    public AuthRepository authRepository() {
        return authRepository;
    }

    /**
     * Saved route plans (Version 18).
     *
     * <p>The order the user decided. Nothing else in the app depends on it, and it depends on nothing
     * else - a plan is a list of store ids in an order, and it is only ever read by the route screen.
     */
    public PlanRepository planRepository() {
        return planRepository;
    }

    /**
     * Manual cloud sync (Version 19).
     *
     * <p>Two explicit operations - save the whole database to the cloud, or bring the cloud's copy back -
     * and nothing automatic in between. It is not the {@link SyncManager} seam: see the note in
     * {@code onCreate} for why a transport that fires on every write is the wrong shape here.
     */
    public CloudSyncRepository cloudSyncRepository() {
        return cloudSyncRepository;
    }
}
