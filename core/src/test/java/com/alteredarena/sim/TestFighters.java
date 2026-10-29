package com.alteredarena.sim;

import java.util.ArrayList;
import java.util.List;

final class TestFighters {
    static List<Combatant> make(int n) {
        List<Combatant> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Combatant c = new Combatant(i, "C" + i, i > 0, 0xFFFFFFFF);
            c.alive = true;
            list.add(c);
        }
        return list;
    }
}
