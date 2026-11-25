package com.sya.tube.library;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UnifiedSearchTest {
    @Test public void sameNameMatches() {
        assertTrue(UnifiedSearch.matches("ohnePixel", "ohnepixel"));
        assertTrue(UnifiedSearch.matches("The Walking Dead", "the walking dead"));
        assertTrue(UnifiedSearch.matches("Dan and Phil", "dan & phil"));
    }

    @Test public void closeNamesMatch() {
        assertTrue(UnifiedSearch.matches("ohnePixel247", "ohnepixel"));
        assertTrue(UnifiedSearch.matches("The Walking Dead on AMC", "walking dead"));
    }

    @Test public void strangersDoNotMatch() {
        assertFalse(UnifiedSearch.matches("Walking Dead Fan Theories and Lore Explained", "walking dead"));
        assertFalse(UnifiedSearch.matches("Dan", "dan and phil"));
        assertFalse(UnifiedSearch.matches("", "x"));
    }

    @Test public void typosStillFindTheCreator() {
        assertTrue(UnifiedSearch.matches("ohnePixel", "ohnpixel"));
        assertTrue(UnifiedSearch.matches("ohnePixel", "ohenpixel"));
        assertTrue(UnifiedSearch.matches("The Walking Dead", "the walkng dead"));
        assertTrue(UnifiedSearch.matches("The Walking Dead on AMC", "walkin dead"));
        assertTrue(UnifiedSearch.matches("Dan and Phil", "dan n phil"));
        assertFalse(UnifiedSearch.matches("Mrbeast", "markiplier"));
        assertFalse(UnifiedSearch.matches("abc", "abd"));
        assertFalse(UnifiedSearch.matches("Pre-Omnixel", "ohnpixel"));
        assertFalse(UnifiedSearch.matches("Hasani Walker", "hasanbi"));
        assertTrue(UnifiedSearch.matches("HasanAbi", "hasanbi"));
    }

    @Test public void closestNameRanksFirst() {
        int creator = UnifiedSearch.score("ohnepixel", "ohnepixel");
        int rerun = UnifiedSearch.score("ohnepixel247", "ohnepixel");
        int lookalike = UnifiedSearch.score("onepixel_", "ohnepixel");
        assertTrue(creator < rerun && creator < lookalike);
        int typo = UnifiedSearch.score("ohnepixel", "ohnpixel"), typoRerun = UnifiedSearch.score("ohnepixel247", "ohnpixel");
        assertTrue(typo >= 0 && (typoRerun < 0 || typo < typoRerun));
    }

    @Test public void exactBeatsContains() {
        assertTrue(UnifiedSearch.exact("ohnepixel", "ohnePixel"));
        assertFalse(UnifiedSearch.exact("ohnepixel247", "ohnepixel"));
    }

    @Test public void patreonSkipsFanPagesAndPlaceholders() {
        assertTrue(UnifiedSearch.patreonScore("markiplierSINGSbadly", "markiplierSINGSbadly", "https://www.patreon.com/MarkiplierFAM", 18, "markiplier") < 0);
        assertTrue(UnifiedSearch.patreonScore("The Walking Dead Talk Through ", "", "https://www.patreon.com/WalkingDeadTalkThroug", 72, "the walking dead") < 0);
        assertTrue(UnifiedSearch.patreonScore("mrbeast", "mrbeast", "https://www.patreon.com/mrbeast47", 1, "mrbeast") < 0);
    }

    @Test public void patreonFindsTheCreatorByNameOrAddress() {
        assertEquals(0, UnifiedSearch.patreonScore("Philosophy Tube", "", "https://www.patreon.com/PhilosophyTube", 807, "philosophy tube"));
        assertEquals(0, UnifiedSearch.patreonScore("Kurzgesagt – In a Nutshell", "", "https://www.patreon.com/Kurzgesagt", 538, "kurzgesagt"));
        assertTrue(UnifiedSearch.patreonScore("LEMMiNO", "", "https://www.patreon.com/lemmino", 67, "lemino") > 0);
    }

    @Test public void readsYouTubeLengths() {
        assertEquals(7 * 3600_000L + 13 * 60_000 + 45_000, UnifiedSearch.clockMs("7:13:45"));
        assertEquals(65_000, UnifiedSearch.clockMs("1:05"));
        assertEquals(0, UnifiedSearch.clockMs("LIVE"));
    }
}
