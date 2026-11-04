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
    public void accommodatesNarrowSplitWindowsAndTabletWidth() {
        assertEquals(1, MobileUi.columnsForWidthDp(240, false));
        assertEquals(1, MobileUi.columnsForWidthDp(320, false));
        assertEquals(2, MobileUi.columnsForWidthDp(340, false));
        assertEquals(2, MobileUi.columnsForWidthDp(360, true));
        assertEquals(4, MobileUi.columnsForWidthDp(768, false));
    }
}
