package ua.cdu.edu.fotius.fspm.valorant.analytics.domain.repository;

import java.util.List;

import ua.cdu.edu.fotius.fspm.valorant.analytics.domain.model.Match;
import ua.cdu.edu.fotius.fspm.valorant.analytics.domain.model.MatchHistoryItem;

public interface MatchRepository {
    void save(Match match);

    List<MatchHistoryItem> findHistoryByPlayer(String playerId);
}
