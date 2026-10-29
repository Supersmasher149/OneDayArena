package com.alteredarena.sim;

import com.alteredarena.rules.Tuning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Free-for-all deathmatch rules: first to the kill limit, or most kills when time runs out. */
public final class Match {
    public enum Phase { RUNNING, FINISHED }
    public enum EndReason { KILL_LIMIT, TIME_LIMIT }

    public final int killLimit;
    public final float duration;
    private final List<Combatant> participants;
    private float elapsed;
    private Phase phase = Phase.RUNNING;
    private EndReason endReason;

    public Match(List<Combatant> participants) {
        this(participants, Tuning.KILL_LIMIT, Tuning.MATCH_DURATION);
    }

    public Match(List<Combatant> participants, int killLimit, float duration) {
        this.participants = participants;
        this.killLimit = killLimit;
        this.duration = duration;
    }

    public void update(float dt) {
        if (phase != Phase.RUNNING) return;
        elapsed = Math.min(duration, elapsed + dt);
        if (elapsed >= duration) finish(EndReason.TIME_LIMIT);
    }

    /** Records a kill. Self-kills cost a point instead of awarding one. */
    public void recordKill(Combatant killer, Combatant victim) {
        if (phase != Phase.RUNNING) return;
        victim.deaths++;
        if (killer == null || killer == victim) {
            victim.kills = Math.max(0, victim.kills - 1);
            return;
        }
        killer.kills++;
        if (killer.kills >= killLimit) finish(EndReason.KILL_LIMIT);
    }

    private void finish(EndReason reason) {
        phase = Phase.FINISHED;
        endReason = reason;
    }

    public boolean isRunning() { return phase == Phase.RUNNING; }
    public Phase phase() { return phase; }
    public EndReason endReason() { return endReason; }
    public float elapsed() { return elapsed; }
    public float remaining() { return Math.max(0f, duration - elapsed); }

    /** Standings: kills descending, then fewer deaths, then id. Ranks are shared on equal kills. */
    public Results results() {
        List<Combatant> sorted = new ArrayList<>(participants);
        sorted.sort((a, b) -> {
            if (a.kills != b.kills) return Integer.compare(b.kills, a.kills);
            if (a.deaths != b.deaths) return Integer.compare(a.deaths, b.deaths);
            return Integer.compare(a.id, b.id);
        });
        List<Standing> standings = new ArrayList<>();
        int rank = 0;
        for (int i = 0; i < sorted.size(); i++) {
            Combatant c = sorted.get(i);
            if (i == 0 || c.kills != sorted.get(i - 1).kills) rank = i + 1;
            standings.add(new Standing(rank, c));
        }
        List<Combatant> winners = new ArrayList<>();
        for (Standing s : standings) if (s.rank == 1) winners.add(s.combatant);
        return new Results(Collections.unmodifiableList(standings), Collections.unmodifiableList(winners), endReason);
    }

    public static final class Standing {
        public final int rank;
        public final Combatant combatant;
        Standing(int rank, Combatant combatant) {
            this.rank = rank;
            this.combatant = combatant;
        }
    }

    public static final class Results {
        public final List<Standing> standings;
        /** Everyone tied for the top score; more than one means the match is a draw. */
        public final List<Combatant> winners;
        public final EndReason reason;

        Results(List<Standing> standings, List<Combatant> winners, EndReason reason) {
            this.standings = standings;
            this.winners = winners;
            this.reason = reason;
        }

        public boolean isDraw() { return winners.size() > 1; }

        public String headline() {
            if (winners.isEmpty()) return "NO RESULT";
            if (!isDraw()) return winners.get(0).bot ? winners.get(0).name.toUpperCase() + " WINS" : "YOU WIN";
            StringBuilder sb = new StringBuilder("DRAW: ");
            for (int i = 0; i < winners.size(); i++) {
                if (i > 0) sb.append(i == winners.size() - 1 ? " & " : ", ");
                sb.append(winners.get(i).name.toUpperCase());
            }
            return sb.toString();
        }
    }
}
