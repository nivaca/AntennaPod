package de.danoeh.antennapod.storage.importexport;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
public class SettingsExporterTest {
    private Context context;
    private SharedPreferences defaultPrefs;
    private SharedPreferences sleepTimerPrefs;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        defaultPrefs = context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE);
        sleepTimerPrefs = context.getSharedPreferences("SleepTimerDialog", Context.MODE_PRIVATE);
    }

    @Test
    public void exportAndImportRestoresAllTypes() throws IOException {
        Set<String> set = new HashSet<>(Arrays.asList("a", "b"));
        defaultPrefs.edit()
                .putBoolean("prefBoolean", true)
                .putInt("prefInt", 42)
                .putLong("prefLong", 1234567890123L)
                .putFloat("prefFloat", 1.5f)
                .putString("prefString", "text")
                .putStringSet("prefSet", set)
                .commit();
        sleepTimerPrefs.edit().putBoolean("Vibrate", true).commit();
        byte[] exported = export();

        defaultPrefs.edit().clear().putString("prefOther", "removed").commit();
        sleepTimerPrefs.edit().clear().commit();
        SettingsExporter.importFromStream(context, new ByteArrayInputStream(exported));

        assertTrue(defaultPrefs.getBoolean("prefBoolean", false));
        assertEquals(42, defaultPrefs.getInt("prefInt", 0));
        assertEquals(1234567890123L, defaultPrefs.getLong("prefLong", 0));
        assertEquals(1.5f, defaultPrefs.getFloat("prefFloat", 0), 0);
        assertEquals("text", defaultPrefs.getString("prefString", null));
        assertEquals(set, defaultPrefs.getStringSet("prefSet", null));
        assertFalse(defaultPrefs.contains("prefOther"));
        assertTrue(sleepTimerPrefs.getBoolean("Vibrate", false));
    }

    @Test
    public void privateAndDeviceSettingsAreNotExportedOrReplaced() throws IOException {
        defaultPrefs.edit()
                .putString("prefProxyPassword", "secret")
                .putString("prefDataFolder", "/old/folder")
                .putLong("de.danoeh.antennapod.preferences.currentlyPlayingMedia", 7)
                .commit();
        byte[] exported = export();
        assertFalse(new String(exported, StandardCharsets.UTF_8).contains("secret"));

        defaultPrefs.edit()
                .putString("prefProxyPassword", "other")
                .putString("prefDataFolder", "/new/folder")
                .commit();
        SettingsExporter.importFromStream(context, new ByteArrayInputStream(exported));

        assertEquals("other", defaultPrefs.getString("prefProxyPassword", null));
        assertEquals("/new/folder", defaultPrefs.getString("prefDataFolder", null));
        assertEquals(7, defaultPrefs.getLong("de.danoeh.antennapod.preferences.currentlyPlayingMedia", 0));
    }

    @Test
    public void invalidFileChangesNothing() {
        defaultPrefs.edit().putString("prefString", "kept").commit();
        byte[] invalid = "{\"something\": 1}".getBytes(StandardCharsets.UTF_8);
        try {
            SettingsExporter.importFromStream(context, new ByteArrayInputStream(invalid));
            fail("Expected IOException");
        } catch (IOException expected) {
            assertEquals("kept", defaultPrefs.getString("prefString", null));
        }
    }

    private byte[] export() throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        SettingsExporter.exportToStream(context, outputStream);
        return outputStream.toByteArray();
    }
}
