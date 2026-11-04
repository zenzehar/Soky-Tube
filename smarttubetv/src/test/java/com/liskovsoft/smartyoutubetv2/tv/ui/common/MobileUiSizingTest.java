package com.liskovsoft.smartyoutubetv2.tv.ui.common;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Device-width regression tests, including narrow panes and landscape phones. */
public class MobileUiSizingTest {
    @Test
    public void neverOverflowsCurrentWindowWidth() {
        float[] widthsDp = {200f, 240f, 280f, 320f, 340f, 360f, 393f,
                430f, 480f, 550f, 600f, 768f, 840f, 1024f, 1440f};
        for (float width : widthsDp) {
            for (boolean shorts : new boolean[]{false, true}) {
                int cols = MobileUi.columnsForWidthDp(width, shorts);
                float cardWidth = MobileUi.cardWidthForWidthDp(width, shorts);
                assertTrue("nonzero columns @" + width, cols >= 1 && cols <= 6);
                assertTrue("nonzero card @" + width, cardWidth > 0f);
                float actualRowWidth = cols * cardWidth + (cols - 1) * 10f;
                assertTrue("cards overflow " + width + ": " + actualRowWidth,
                        actualRowWidth <= width - 32f + 0.01f);
            }
        }
    }

    @Test
    public void usesActualWidthWhenRotatedAndBackAgain() {
        assertEquals(2, MobileUi.columnsForWidthDp(393, false));
        assertEquals(4, MobileUi.columnsForWidthDp(874, false));
        assertEquals(2, MobileUi.columnsForWidthDp(393, false));
    }

    @Test
    public void videoFeedUsesNativeMobileColumnsForActualWindowWidth() {
        assertEquals(1, MobileUi.feedColumnsForWidthDp(240));
        assertEquals(1, MobileUi.feedColumnsForWidthDp(360));
        assertEquals(1, MobileUi.feedColumnsForWidthDp(599));
        assertEquals(2, MobileUi.feedColumnsForWidthDp(600));
        assertEquals(2, MobileUi.feedColumnsForWidthDp(875));
        assertEquals(2, MobileUi.feedColumnsForWidthDp(1079));
        assertEquals(3, MobileUi.feedColumnsForWidthDp(1080));
        assertEquals(3, MobileUi.feedColumnsForWidthDp(1440));
    }

    @Test
    public void settingsCategoryColumnsAdaptToPhonesAndTablets() {
        assertEquals(1, MobileUi.settingsColumnsForWidthDp(200));
        assertEquals(1, MobileUi.settingsColumnsForWidthDp(393));
        assertEquals(1, MobileUi.settingsColumnsForWidthDp(599));
        assertEquals(2, MobileUi.settingsColumnsForWidthDp(600));
        assertEquals(2, MobileUi.settingsColumnsForWidthDp(999));
        assertEquals(3, MobileUi.settingsColumnsForWidthDp(1000));
    }

    @Test
    public void nestedSettingsSheetNeverOverflowsWindow() {
        float[] windows = {180f, 200f, 240f, 320f, 360f, 393f, 480f,
                600f, 768f, 1024f, 1440f};
        for (float dp : windows) {
            float panel = MobileUi.settingsPanelWidthDp(dp);
            assertTrue(panel > 0);
            assertTrue("sheet exceeds window " + dp, panel <= dp);
            if (dp <= 600f) assertEquals(dp, panel, 0.001f);
            else assertTrue(panel <= 640f);
        }
    }

    @Test
    public void playerControlsUseActualVideoHeightThresholds() {
        assertTrue(!MobileUi.showSecondaryPlayerActions(112f));
        assertTrue(!MobileUi.showSecondaryPlayerActions(189f));
        assertTrue(MobileUi.showSecondaryPlayerActions(203f));
        assertTrue(!MobileUi.showPlayerMetadata(203f));
        assertTrue(MobileUi.showPlayerMetadata(280f));
    }

    @Test
    public void accommodatesNarrowSplitWindowsAndTabletWidth() {
        assertEquals(1, MobileUi.columnsForWidthDp(240, false));
        assertEquals(1, MobileUi.columnsForWidthDp(320, false));
        assertEquals(2, MobileUi.columnsForWidthDp(340, false));
        assertEquals(2, MobileUi.columnsForWidthDp(360, true));
        assertEquals(4, MobileUi.columnsForWidthDp(768, false));
    }
}
