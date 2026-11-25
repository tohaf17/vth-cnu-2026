package ua.cdu.edu.fotius.fspm.valorant.analytics.domain;

import org.junit.jupiter.api.Test;
import ua.cdu.edu.fotius.fspm.valorant.analytics.domain.model.Match;
import ua.cdu.edu.fotius.fspm.valorant.analytics.domain.model.MatchStats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MatchDomainTest {
    @Test
    void rejectsRoundMarginOutsideRegulationBounds() {
        assertThrows(IllegalArgumentException.class,
            () -> new MatchStats(true, 14, 10, 5, 2, 200, false));
    }

    @Test
    void createsWinMatchWithDomainResult() {
        Match match = Match.create("r-101", "p-001", "Ascent",
            new MatchStats(true, 5, 22, 12, 5, 310, false), 25, "PROMOTED");

        assertEquals(Match.Result.WIN, match.result());
        assertEquals("r-101", match.rosterId());
    }
}
