package com.liskovsoft.smartyoutubetv2.tv.ui.common;

import org.junit.Test;
import static org.junit.Assert.*;

public class MobileSystemBarsTest {
    @Test public void fittedWindowIsNotOffsetTwice() {
        assertArrayEquals(new int[]{0, 0}, MobileSystemBars.correction(100, 2200, 100, 2300));
    }
    @Test public void edgeToEdgeWindowReservesTopAndBottom() {
        assertArrayEquals(new int[]{100, 100}, MobileSystemBars.correction(0, 2400, 100, 2300));
    }
    @Test public void tallerCameraCutoutIsRespected() {
        assertArrayEquals(new int[]{142, 100}, MobileSystemBars.correction(0, 2400, 142, 2300));
    }
    @Test public void splitScreenDoesNotCreateFalseBottomPadding() {
        assertArrayEquals(new int[]{0, 0}, MobileSystemBars.correction(100, 1100, 100, 2300));
    }
}
