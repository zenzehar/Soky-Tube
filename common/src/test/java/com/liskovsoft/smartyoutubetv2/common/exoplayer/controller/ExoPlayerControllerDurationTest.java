package com.liskovsoft.smartyoutubetv2.common.exoplayer.controller;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/** Regression for mobile v7.2 crash: duration queried before setVideo(). */
public class ExoPlayerControllerDurationTest {
    @Test
    public void missingVideoGivesUnknownLiveDuration() {
        assertEquals(-1L, ExoPlayerController.resolveLiveDurationMs(null, -1L, false));
    }

    @Test
    public void missingVideoCanUseKnownSabrEdge() {
        assertEquals(120000L, ExoPlayerController.resolveLiveDurationMs(null, 120000L, true));
    }

    @Test
    public void normalVideoWithUnknownLiveDurationStaysUnknown() {
        assertEquals(-1L, ExoPlayerController.resolveLiveDurationMs(new Video(), -1L, false));
    }

    @Test
    public void normalVideoWithKnownSabrEdgeUsesKnownEdge() {
        assertEquals(120000L, ExoPlayerController.resolveLiveDurationMs(new Video(), 120000L, true));
    }
}
