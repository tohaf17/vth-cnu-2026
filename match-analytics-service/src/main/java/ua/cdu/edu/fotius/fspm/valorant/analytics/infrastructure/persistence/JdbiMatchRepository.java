package ua.cdu.edu.fotius.fspm.valorant.analytics.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.jdbi.v3.core.Jdbi;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import ua.cdu.edu.fotius.fspm.valorant.analytics.domain.model.Match;
import ua.cdu.edu.fotius.fspm.valorant.analytics.domain.model.MatchHistoryItem;
import ua.cdu.edu.fotius.fspm.valorant.analytics.domain.repository.MatchRepository;

@Singleton
public class JdbiMatchRepository implements MatchRepository {
    private final Jdbi jdbi;

    @Inject
    public JdbiMatchRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    @Override
    public void save(Match match) {
        jdbi.useTransaction(handle -> {
            handle.createUpdate("INSERT INTO matches (id, roster_id, map_name, result, round_margin) "
                    + "VALUES (:id, :rosterId, :mapName, :result, :roundMargin)")
                .bind("id", match.id())
                .bind("rosterId", match.rosterId())
                .bind("mapName", match.mapName())
                .bind("result", match.result().name())
                .bind("roundMargin", match.stats().roundMargin())
                .execute();

            handle.createUpdate("INSERT INTO match_players "
                    + "(id, match_id, player_id, kills, deaths, assists, combat_score, delta_rr, tier_status) "
                    + "VALUES (:id, :matchId, :playerId, :kills, :deaths, :assists, :combatScore, :deltaRR, :tierStatus)")
                .bind("id", UUID.randomUUID().toString())
                .bind("matchId", match.id())
                .bind("playerId", match.playerId())
                .bind("kills", match.stats().kills())
                .bind("deaths", match.stats().deaths())
                .bind("assists", match.stats().assists())
                .bind("combatScore", match.stats().combatScore())
                .bind("deltaRR", match.deltaRR())
                .bind("tierStatus", match.tierStatus())
                .execute();
        });
    }

    @Override
    public List<MatchHistoryItem> findHistoryByPlayer(String playerId) {
        return jdbi.withHandle(handle -> handle.createQuery(
                "SELECT m.id, m.map_name, m.result, mp.delta_rr, mp.tier_status "
                    + "FROM matches m JOIN match_players mp ON m.id = mp.match_id "
                    + "WHERE mp.player_id = :playerId ORDER BY m.played_at DESC")
            .bind("playerId", playerId)
            .map((rs, ctx) -> new MatchHistoryItem(
                rs.getString("id"),
                rs.getString("map_name"),
                rs.getString("result"),
                rs.getInt("delta_rr"),
                rs.getString("tier_status")))
            .list());
    }
}
