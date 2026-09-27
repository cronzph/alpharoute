package com.cph.alpharoute.data.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for {@link AppSettingEntity} - specifically the Home-configured rule.
 *
 * <p>These run on the JVM. The class carries Room annotations, but those are compile-time metadata and
 * the entity is an ordinary object, so no database and no device are needed to check its logic.
 *
 * <p><b>What is being protected here.</b> The specification gives Home its own {@code homeSet} flag
 * rather than deriving "is Home configured?" from the coordinates being non-null. That means the two
 * can disagree - and a disagreement would put the app in a genuinely bad state: a route that tries to
 * finish at Home, with no Home to finish at. {@code isHomeSet()} is the single place that resolves the
 * question, and it requires <b>all three</b> to agree.
 */
public class AppSettingEntityTest {

    private static final double MANILA_LATITUDE = 14.5995;
    private static final double MANILA_LONGITUDE = 120.9842;

    @Test
    public void aFreshRowHasNoHome() {
        AppSettingEntity settings = new AppSettingEntity();

        assertFalse("A new settings row must not claim Home is set", settings.isHomeSet());
    }

    @Test
    public void aFreshRowUsesTheSingletonId() {
        // The whole design of this table rests on there being exactly one row with this id.
        assertEquals(AppSettingEntity.SINGLETON_ID, new AppSettingEntity().id);
    }

    @Test
    public void homeIsSetWhenTheFlagAndBothCoordinatesAgree() {
        AppSettingEntity settings = new AppSettingEntity();
        settings.homeSet = true;
        settings.homeLatitude = MANILA_LATITUDE;
        settings.homeLongitude = MANILA_LONGITUDE;

        assertTrue(settings.isHomeSet());
    }

    @Test
    public void homeIsNotSetWhenTheFlagIsFalseEvenIfCoordinatesExist() {
        // The case the flag exists for: coordinates present but the flag says Home was never
        // confirmed. The flag wins, because the flag is what the user actually confirmed.
        AppSettingEntity settings = new AppSettingEntity();
        settings.homeSet = false;
        settings.homeLatitude = MANILA_LATITUDE;
        settings.homeLongitude = MANILA_LONGITUDE;

        assertFalse(settings.isHomeSet());
    }

    @Test
    public void homeIsNotSetWhenLatitudeIsMissing() {
        // A half-written Home - the flag says yes, the data says no. Treat it as not set rather than
        // sending a route to latitude 0.
        AppSettingEntity settings = new AppSettingEntity();
        settings.homeSet = true;
        settings.homeLatitude = null;
        settings.homeLongitude = MANILA_LONGITUDE;

        assertFalse(settings.isHomeSet());
    }

    @Test
    public void homeIsNotSetWhenLongitudeIsMissing() {
        AppSettingEntity settings = new AppSettingEntity();
        settings.homeSet = true;
        settings.homeLatitude = MANILA_LATITUDE;
        settings.homeLongitude = null;

        assertFalse(settings.isHomeSet());
    }

    @Test
    public void homeIsNotSetWhenBothCoordinatesAreMissing() {
        AppSettingEntity settings = new AppSettingEntity();
        settings.homeSet = true;
        settings.homeLatitude = null;
        settings.homeLongitude = null;

        assertFalse(settings.isHomeSet());
    }

    @Test
    public void latitudeZeroIsStillAValidHome() {
        // Null means "not set". Zero is a real coordinate in the Gulf of Guinea, and conflating the
        // two is the classic bug this check exists to prevent.
        AppSettingEntity settings = new AppSettingEntity();
        settings.homeSet = true;
        settings.homeLatitude = 0.0;
        settings.homeLongitude = 0.0;

        assertTrue("0.0 is a real coordinate, not a missing one", settings.isHomeSet());
    }

    @Test
    public void aFreshRowHasNoArrivalRadiusSetByTheEntityItself() {
        // The real default (50 m) is applied by SettingsRepository.ensureDefaults() from
        // GeoConstants.DEFAULT_ARRIVAL_RADIUS_M, and read back through a safe fallback. The entity's
        // own field default is deliberately 0 so that "never configured" is distinguishable.
        assertEquals(0, new AppSettingEntity().arrivalRadius);
    }

    @Test
    public void aFreshRowHasNoLastSelectedStore() {
        AppSettingEntity settings = new AppSettingEntity();

        assertNull(settings.lastSelectedStoreId);
    }
}
