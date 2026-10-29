package com.alteredarena.sim;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MatchTest {
    @Test
    void killLimitEndsMatchWithSingleWinner() {
        List<Combatant> cs = TestFighters.make(3);
        Match m = new Match(cs, 15, 300f);
        for (int i = 0; i < 14; i++) m.recordKill(cs.get(1), cs.get(2));
        assertTrue(m.isRunning());
        m.recordKill(cs.get(1), cs.get(0));
        assertFalse(m.isRunning());
        assertEquals(Match.EndReason.KILL_LIMIT, m.endReason());
        Match.Results r = m.results();
        assertFalse(r.isDraw());
        assertSame(cs.get(1), r.winners.get(0));
        assertEquals("C1 WINS", r.headline());
        assertEquals(15, cs.get(1).kills);
        assertEquals(14, cs.get(2).deaths);
    }

    @Test
    void timerExpiryEndsMatchAndTiesAreDraws() {
        List<Combatant> cs = TestFighters.make(3);
        Match m = new Match(cs, 15, 300f);
        m.recordKill(cs.get(0), cs.get(2));
        m.recordKill(cs.get(1), cs.get(2));
        for (int i = 0; i < 299 * 120; i++) m.update(1f / 120f);
        assertTrue(m.isRunning());
        for (int i = 0; i < 2 * 120; i++) m.update(1f / 120f);
        assertFalse(m.isRunning());
        assertEquals(Match.EndReason.TIME_LIMIT, m.endReason());
        assertEquals(0f, m.remaining());
        Match.Results r = m.results();
        assertTrue(r.isDraw());
        assertEquals(2, r.winners.size());
        assertEquals(1, r.standings.get(0).rank);
        assertEquals(1, r.standings.get(1).rank, "tied players share a rank");
        assertEquals(3, r.standings.get(2).rank);
        assertTrue(r.headline().startsWith("DRAW"));
    }

    @Test
    void playerWinHeadline() {
        List<Combatant> cs = TestFighters.make(2);
        Match m = new Match(cs, 1, 300f);
        m.recordKill(cs.get(0), cs.get(1));
        assertEquals("YOU WIN", m.results().headline());
    }

    @Test
    void killsAfterEndAreIgnored() {
        List<Combatant> cs = TestFighters.make(2);
        Match m = new Match(cs, 1, 300f);
        m.recordKill(cs.get(0), cs.get(1));
        m.recordKill(cs.get(1), cs.get(0));
        assertEquals(0, cs.get(1).kills);
    }

    @Test
    void selfKillCostsAPoint() {
        List<Combatant> cs = TestFighters.make(2);
        Match m = new Match(cs);
        m.recordKill(cs.get(0), cs.get(1));
        m.recordKill(cs.get(0), cs.get(0));
        assertEquals(0, cs.get(0).kills);
        assertEquals(1, cs.get(0).deaths);
    }
}
