package com.eveningoutpost.dexdrip.services;

import static com.google.common.truth.Truth.assertWithMessage;

import com.eveningoutpost.dexdrip.RobolectricTestWithConfig;
import com.eveningoutpost.dexdrip.models.BgReading;
import com.eveningoutpost.dexdrip.models.JoH;
import com.eveningoutpost.dexdrip.utilitymodels.Constants;
import com.eveningoutpost.dexdrip.utilitymodels.PersistentStore;

import org.junit.Before;
import org.junit.Test;

import lombok.val;

public class UiBasedCollectorTest extends RobolectricTestWithConfig {

    @Before
    @Override
    public void setUp() {
        super.setUp();
        PersistentStore.setLong("UI_BASED_STORE_LAST_VALUE", 0);
        PersistentStore.setLong("UI_BASED_STORE_LAST_REPEAT", 0);
        PersistentStore.setLong("UI_BASED_STORE_LAST_TIME", 0);
    }

    @Test
    public void isValidMmolTest() {

        assertWithMessage("good 1").that(UiBasedCollector.isValidMmol("5.6")).isTrue();
        assertWithMessage("good 2").that(UiBasedCollector.isValidMmol("5.55")).isTrue();
        assertWithMessage("good 3").that(UiBasedCollector.isValidMmol("12.34")).isTrue();

        assertWithMessage("bad 1").that(UiBasedCollector.isValidMmol("555")).isFalse();
        assertWithMessage("bad 2").that(UiBasedCollector.isValidMmol("abc")).isFalse();
        assertWithMessage("bad 3").that(UiBasedCollector.isValidMmol("abc 12.34")).isFalse();
        assertWithMessage("bad 4").that(UiBasedCollector.isValidMmol("abc12.34")).isFalse();
        assertWithMessage("bad 5").that(UiBasedCollector.isValidMmol("12.34abc")).isFalse();
        assertWithMessage("bad 6").that(UiBasedCollector.isValidMmol("5..55")).isFalse();
        assertWithMessage("bad 7").that(UiBasedCollector.isValidMmol("5.")).isFalse();
        assertWithMessage("bad 8").that(UiBasedCollector.isValidMmol(".5")).isFalse();
        assertWithMessage("bad 9").that(UiBasedCollector.isValidMmol("5")).isFalse();


    }

    @Test
    public void filterStringTest() {
        val i = new UiBasedCollector();
        val spec1 = "non spec";
        val spec2 = "≤5.123";
        val spec2p = "5.123";
        val spec3 = "≥100.123";
        val spec3p = "100.123";
        val spec4 = "\u0038\u002c\u0039\u00a0\u006d\u006d\u006f\u006c\u2060\u002f\u2060\u006c\u0020";
        val spec4p = "8,9";
        val spec5 = "\u0038\u002c\u0039\u00a0\u006d\u006d\u006f\u006c\u2060\u002f\u2060\u2191\u2b06\u006c\u0020";
        val spec5p = "8,9";
        assertWithMessage("null test pass through 1").that(i.filterString(spec1)).isEqualTo(spec1);
        assertWithMessage("null test pass through 2").that(i.filterString(spec2)).isEqualTo(spec2);
        assertWithMessage("null test pass through 3").that(i.filterString(spec3)).isEqualTo(spec3);
        i.lastPackage = "hello world";
        assertWithMessage("non 1").that(i.filterString(spec1)).isEqualTo(spec1);
        assertWithMessage("gte 1").that(i.filterString(spec2)).isEqualTo(spec2p);
        assertWithMessage("lte 1").that(i.filterString(spec3)).isEqualTo(spec3p);
        assertWithMessage("gc1 1").that(i.filterString(spec4)).isEqualTo(spec4p);
        assertWithMessage("gc1 2").that(i.filterString(spec5)).isEqualTo(spec5p);
    }

    @Test
    public void parseIoBOmnipodTest() {
        val i = new UiBasedCollector();

        val valid = "Automated Mode (IOB: 5.1 U)";
        val invalid = "Foobar";

        assertWithMessage("valid Omnipod IoB message").that(i.parseIoB(valid)).isEqualTo(5.1);
        assertWithMessage("invalid IoB message").that(i.parseIoB(invalid)).isNull();
    }

    @Test
    public void parseIoBMiniMedTest() {
        val i = new UiBasedCollector();

        val valid = "2.400 U";
        val invalidNoUnit = "2.400";
        val invalidExtraText = "Active Insulin 2.400 U";

        assertWithMessage("valid MiniMed IoB message").that(i.parseIoB(valid)).isEqualTo(2.4);
        assertWithMessage("invalid IoB message (no unit)").that(i.parseIoB(invalidNoUnit)).isNull();
        assertWithMessage("invalid IoB message (extra text)").that(i.parseIoB(invalidExtraText)).isNull();
    }

    // standard 5 minute apart readings are all accepted
    @Test
    public void deDupeTest1() {
        BgReading.deleteALL();
        val ui = new UiBasedCollector();
        val start = JoH.tsl();
        for (int i = 0; i < 50; i++) {
            val ts = start + (Constants.SECOND_IN_MS * 300 * i);
            val mgdl = i + 100;
            val result = ui.handleNewValue(ts, mgdl);
            assertWithMessage("deDupeTest1 ts: " + ts + " mgdl: " + mgdl).that(result).isTrue();
        }
    }

    // differing 1 minute apart readings are all accepted
    @Test
    public void deDupeTest2() {
        BgReading.deleteALL();
        val ui = new UiBasedCollector();
        val start = JoH.tsl();
        for (int i = 0; i < 50; i++) {
            val ts = start + (Constants.SECOND_IN_MS * 60 * i);
            val mgdl = i + 100;
            val result = ui.handleNewValue(ts, mgdl);
            assertWithMessage("deDupeTest2 ts: " + ts + " mgdl: " + mgdl).that(result).isTrue();
        }
    }

    // differing readings 5 seconds apart are rejected
    @Test
    public void deDupeTest3() {
        BgReading.deleteALL();
        val ui = new UiBasedCollector();
        val start = JoH.tsl();
        assertWithMessage("deDupeTest3 A ts: ")
                .that(ui.handleNewValue(start + (Constants.SECOND_IN_MS * 5 * 0), 100)).isTrue();
        assertWithMessage("deDupeTest3 B ts: ")
                .that(ui.handleNewValue(start + (Constants.SECOND_IN_MS * 5 * 1), 101)).isFalse();
    }

    // differing readings 15 seconds apart are accepted
    @Test
    public void deDupeTest4() {
        BgReading.deleteALL();
        val ui = new UiBasedCollector();
        val start = JoH.tsl();
        assertWithMessage("deDupeTest4 A ts: ")
                .that(ui.handleNewValue(start + (Constants.SECOND_IN_MS * 15 * 0), 100)).isTrue();
        assertWithMessage("deDupeTest4 B ts: ")
                .that(ui.handleNewValue(start + (Constants.SECOND_IN_MS * 15 * 1), 101)).isTrue();
    }

    // same readings 2 minutes apart are rejected
    @Test
    public void deDupeTest5() {
        BgReading.deleteALL();
        val ui = new UiBasedCollector();
        val start = JoH.tsl();
        assertWithMessage("deDupeTest5 A ts: ")
                .that(ui.handleNewValue(start + (Constants.MINUTE_IN_MS * 2 * 0), 100)).isTrue();
        assertWithMessage("deDupeTest5 B ts: ")
                .that(ui.handleNewValue(start + (Constants.MINUTE_IN_MS * 2 * 1), 100)).isFalse();
    }

    // same readings 4 minutes apart are rejected
    @Test
    public void deDupeTest6() {
        BgReading.deleteALL();
        val ui = new UiBasedCollector();
        val start = JoH.tsl();
        assertWithMessage("deDupeTest5 A ts: ")
                .that(ui.handleNewValue(start + (Constants.MINUTE_IN_MS * 4 * 0), 100)).isTrue();
        assertWithMessage("deDupeTest5 B ts: ")
                .that(ui.handleNewValue(start + (Constants.MINUTE_IN_MS * 4 * 1), 100)).isFalse();
    }


    // same readings 5 minutes apart are allowed
    @Test
    public void deDupeTest7() {
        BgReading.deleteALL();
        val ui = new UiBasedCollector();
        val start = JoH.tsl();
        assertWithMessage("deDupeTest5 A ts: ")
                .that(ui.handleNewValue(start + (Constants.MINUTE_IN_MS * 5 * 0), 100)).isTrue();
        assertWithMessage("deDupeTest5 B ts: ")
                .that(ui.handleNewValue(start + (Constants.MINUTE_IN_MS * 5 * 1), 100)).isTrue();
    }

    /**
     * Characterization: different values 5 minutes apart should always be accepted.
     * Exercises the normal case where the sensor provides fresh, unique readings
     * every 5 minutes.
     */
    @Test
    public void deDupeTest_differentValues5minApart_accepted() {
        // :: Setup
        BgReading.deleteALL();
        val ui = new UiBasedCollector();
        val start = JoH.tsl();

        // :: Act & Verify
        assertWithMessage("first reading accepted")
                .that(ui.handleNewValue(start, 100)).isTrue();
        assertWithMessage("different value 5 min later accepted")
                .that(ui.handleNewValue(start + Constants.MINUTE_IN_MS * 5, 110)).isTrue();
        assertWithMessage("different value 10 min later accepted")
                .that(ui.handleNewValue(start + Constants.MINUTE_IN_MS * 10, 120)).isTrue();
    }

    /**
     * Characterization: same value 10 minutes apart should be accepted.
     * When the sensor genuinely reads the same value after a full update cycle,
     * it should be recorded.
     */
    @Test
    public void deDupeTest_sameValue10minApart_accepted() {
        // :: Setup
        BgReading.deleteALL();
        val ui = new UiBasedCollector();
        val start = JoH.tsl();

        // :: Act & Verify
        assertWithMessage("first reading accepted")
                .that(ui.handleNewValue(start, 100)).isTrue();
        assertWithMessage("same value 10 min later accepted")
                .that(ui.handleNewValue(start + Constants.MINUTE_IN_MS * 10, 100)).isTrue();
    }

    /**
     * Characterization: alternating values at 5-min intervals all accepted.
     * e.g. 100, 110, 100 — each differs from its predecessor.
     */
    @Test
    public void deDupeTest_alternatingValues_allAccepted() {
        // :: Setup
        BgReading.deleteALL();
        val ui = new UiBasedCollector();
        val start = JoH.tsl();

        // :: Act & Verify
        assertWithMessage("reading 1 accepted")
                .that(ui.handleNewValue(start, 100)).isTrue();
        assertWithMessage("different reading 2 accepted")
                .that(ui.handleNewValue(start + Constants.MINUTE_IN_MS * 5, 110)).isTrue();
        assertWithMessage("back to original reading 3 accepted")
                .that(ui.handleNewValue(start + Constants.MINUTE_IN_MS * 10, 100)).isTrue();
    }

    /**
     * Characterization: jam detection rejects after Medtronic threshold (9 repeats).
     * Readings spaced at 10-min intervals to pass dedup but increment jam counter.
     */
    @Test
    public void deDupeTest_jamDetection_rejectsExcessiveRepeats() {
        // :: Setup
        BgReading.deleteALL();
        val ui = new UiBasedCollector();
        ui.lastPackage = "com.medtronic.diabetes.guardian";
        val start = JoH.tsl();

        // :: Act - insert same value 10 times at 10-min intervals
        // For Medtronic a same value is held back first, and saved when no new value arrives.
        assertWithMessage("reading 0 accepted")
                .that(ui.handleNewValue(start, 100)).isTrue();
        for (int i = 1; i <= 9; i++) {
            assertWithMessage("reading " + i + " held")
                    .that(ui.handleNewValue(start + Constants.MINUTE_IN_MS * 10 * i, 100)).isFalse();
            assertWithMessage("reading " + i + " accepted")
                    .that(ui.saveHeldValue()).isTrue();
        }

        // :: Verify - 11th same value rejected by jam detection
        assertWithMessage("11th identical value held")
                .that(ui.handleNewValue(start + Constants.MINUTE_IN_MS * 10 * 10, 100)).isFalse();
        assertWithMessage("11th identical value rejected by jam detection")
                .that(ui.saveHeldValue()).isFalse();
    }

    private static final String GUARDIAN = "com.medtronic.diabetes.guardian";

    private static boolean readingAt(final long timestamp) {
        return BgReading.getForPreciseTimestamp(timestamp, Constants.SECOND_IN_MS, false) != null;
    }

    /**
     * Replays a real Guardian 4 log (Guardian app 1.6.0). Guardian posts the glucose notification
     * again every 2 minutes with the same value. Only the 5 real readings may be saved.
     * Before the fix, the re-posts at +288 s and +888 s were saved as extra readings.
     */
    @Test
    public void medtronic_realGuardianLog_onlyRealReadingsSaved() {
        // :: Setup
        BgReading.deleteALL();
        val ui = new UiBasedCollector();
        ui.lastPackage = GUARDIAN;
        val start = JoH.tsl(); // 02:26:56.278 in the log

        // time in ms after start, value, expected result
        final long[][] events = {
                {0, 141, 1},       // 02:26:56 real reading
                {48162, 141, 0},   // 02:27:44 re-post
                {168193, 141, 0},  // 02:29:44 re-post
                {288289, 141, 0},  // 02:31:44 re-post, held
                {300058, 144, 1},  // 02:31:56 real reading, drops the held re-post
                {323398, 144, 0},  // 02:32:19 re-post
                {326714, 144, 0},  // 02:32:22 re-post
                {600824, 151, 1},  // 02:36:57 real reading (after "Updating...")
                {648399, 151, 0},  // 02:37:44 re-post
                {768454, 151, 0},  // 02:39:44 re-post
                {888493, 151, 0},  // 02:41:44 re-post, held
                {900841, 155, 1},  // 02:41:57 real reading, drops the held re-post
                {1008505, 155, 0}, // 02:43:44 re-post
                {1128542, 155, 0}, // 02:45:44 re-post
                {1200338, 163, 1}, // 02:46:56 real reading
        };

        // :: Act & Verify
        for (val event : events) {
            assertWithMessage("value " + event[1] + " at +" + event[0] + " ms")
                    .that(ui.handleNewValue(start + event[0], (int) event[1])).isEqualTo(event[2] == 1);
        }
        assertWithMessage("nothing left to save").that(ui.saveHeldValue()).isFalse();
        assertWithMessage("no reading from re-post at +288 s").that(readingAt(start + 288289)).isFalse();
        assertWithMessage("no reading from re-post at +888 s").that(readingAt(start + 888493)).isFalse();
        assertWithMessage("real reading at +300 s").that(readingAt(start + 300058)).isTrue();
        assertWithMessage("real reading at +900 s").that(readingAt(start + 900841)).isTrue();
    }

    /**
     * A real reading with the same value as the one before (flat glucose) is still saved,
     * after the hold time, even when a re-post came a few seconds before it.
     */
    @Test
    public void medtronic_flatValue_savedOncePerReading() {
        // :: Setup
        BgReading.deleteALL();
        val ui = new UiBasedCollector();
        ui.lastPackage = GUARDIAN;
        val start = JoH.tsl();
        val s = Constants.SECOND_IN_MS;

        // :: Act & Verify
        assertWithMessage("first reading").that(ui.handleNewValue(start, 100)).isTrue();
        assertWithMessage("re-post +47 s").that(ui.handleNewValue(start + 47 * s, 100)).isFalse();
        assertWithMessage("re-post +167 s").that(ui.handleNewValue(start + 167 * s, 100)).isFalse();
        assertWithMessage("re-post +287 s held").that(ui.handleNewValue(start + 287 * s, 100)).isFalse();
        assertWithMessage("real +300 s, already held").that(ui.handleNewValue(start + 300 * s, 100)).isFalse();
        assertWithMessage("held value saved").that(ui.saveHeldValue()).isTrue();
        assertWithMessage("only one reading for this 5 minutes").that(ui.saveHeldValue()).isFalse();

        assertWithMessage("re-post +407 s").that(ui.handleNewValue(start + 407 * s, 100)).isFalse();
        assertWithMessage("re-post +527 s").that(ui.handleNewValue(start + 527 * s, 100)).isFalse();
        assertWithMessage("real +600 s held").that(ui.handleNewValue(start + 600 * s, 100)).isFalse();
        assertWithMessage("real +600 s saved").that(ui.saveHeldValue()).isTrue();
        assertWithMessage("reading at +600 s").that(readingAt(start + 600 * s)).isTrue();
    }

    /**
     * If the delayed save did not run (for example in doze), the held value is saved
     * when the next value arrives, and not lost.
     */
    @Test
    public void medtronic_heldValueNotLostWhenDelayedSaveDidNotRun() {
        // :: Setup
        BgReading.deleteALL();
        val ui = new UiBasedCollector();
        ui.lastPackage = GUARDIAN;
        val start = JoH.tsl();
        val s = Constants.SECOND_IN_MS;

        // :: Act
        assertWithMessage("first reading").that(ui.handleNewValue(start, 100)).isTrue();
        assertWithMessage("same value +300 s held").that(ui.handleNewValue(start + 300 * s, 100)).isFalse();
        assertWithMessage("new value +600 s").that(ui.handleNewValue(start + 600 * s, 110)).isTrue();

        // :: Verify
        assertWithMessage("held reading at +300 s saved").that(readingAt(start + 300 * s)).isTrue();
        assertWithMessage("reading at +600 s").that(readingAt(start + 600 * s)).isTrue();
    }

    /**
     * Other companion apps keep the old behaviour: a same value 5 minutes later is saved at once.
     */
    @Test
    public void nonMedtronic_sameValue5minApart_savedAtOnce() {
        // :: Setup
        BgReading.deleteALL();
        val ui = new UiBasedCollector();
        ui.lastPackage = "com.dexcom.g7";
        val start = JoH.tsl();

        // :: Act & Verify
        assertWithMessage("first reading").that(ui.handleNewValue(start, 100)).isTrue();
        assertWithMessage("same value 5 min later")
                .that(ui.handleNewValue(start + Constants.MINUTE_IN_MS * 5, 100)).isTrue();
    }

}
