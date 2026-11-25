package ua.cdu.edu.fotius.fspm.valorant.analytics.infrastructure.rest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Inject;
import ua.cdu.edu.fotius.fspm.valorant.analytics.infrastructure.client.RankCalculatorClient;
import ua.cdu.edu.fotius.fspm.valorant.analytics.infrastructure.client.RosterServiceClient;

@Controller("/api/v1/matches")
public class MatchController {

    private static final Logger log = LoggerFactory.getLogger(MatchController.class);

    private final Jdbi jdbi;
    private final RankCalculatorClient rankClient;
    private final RosterServiceClient rosterClient;

    @Inject
    public MatchController(Jdbi jdbi, RankCalculatorClient rankClient, RosterServiceClient rosterClient) {
        this.jdbi = jdbi;
        this.rankClient = rankClient;
        this.rosterClient = rosterClient;
    }

    @Serdeable public record MatchSubmissionRequest(String rosterId, String playerId, String mapName, boolean isWin, int roundMargin, int kills, int deaths, int assists, int combatScore, boolean isAfk) {}
    @Serdeable public record MatchSubmissionResponse(String matchId, int deltaRR, String tierStatus) {}
    @Serdeable public record MatchHistoryItemDto(String matchId, String mapName, String result, int deltaRR, String tierStatus) {}

    @ExecuteOn(TaskExecutors.BLOCKING)
    @Post("/submit")
    public MatchSubmissionResponse submitMatch(@Body MatchSubmissionRequest request,
                                               @Header("Authorization") Optional<String> authHeader) {
        log.info("POST /matches/submit - rosterId={}, playerId={}, map={}, isWin={}, kills={}, deaths={}, assists={}, combatScore={}",
            request.rosterId(), request.playerId(), request.mapName(), request.isWin(),
            request.kills(), request.deaths(), request.assists(), request.combatScore());

        // 1. Require authentication — header must be present and carry a Bearer token
        String token = authHeader
            .filter(h -> !h.isBlank() && h.startsWith("Bearer "))
            .orElseThrow(() -> {
                log.warn("POST /matches/submit - missing or invalid Authorization header, rejecting");
                return new HttpStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Bearer token");
            });

        // 2. Check roster status (fail-fast)
        try {
            var roster = rosterClient.getRoster(request.rosterId(), token);
            if ("ARCHIVED".equals(roster.status())) {
                log.warn("POST /matches/submit - roster {} is ARCHIVED (already submitted), rejecting", request.rosterId());
                throw new HttpStatusException(
                    HttpStatus.CONFLICT,
                    "Match already submitted for roster " + request.rosterId()
                );
            }
            if (!"LOCKED".equals(roster.status())) {
                log.warn("POST /matches/submit - roster {} is {} (not LOCKED), rejecting", request.rosterId(), roster.status());
                throw new HttpStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Roster must be LOCKED before match submission (current status: " + roster.status() + ")"
                );
            }
        } catch (HttpStatusException e) {
            throw e;
        } catch (HttpClientResponseException e) {
            if (e.getStatus().getCode() == 404) {
                throw new HttpStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Roster not found for roster " + request.rosterId()
                );
            }
            throw new HttpStatusException(
                HttpStatus.BAD_REQUEST,
                "Could not verify roster status for roster " + request.rosterId() + ": " + e.getMessage()
            );
        } catch (Exception e) {
            throw new HttpStatusException(
                HttpStatus.BAD_REQUEST,
                "Could not verify roster status for roster " + request.rosterId() + ": " + e.getMessage()
            );
        }

        // 3. Calculate rating
        String matchId = UUID.randomUUID().toString();
        var rankRes = rankClient.calculateRating(new RankCalculatorClient.RankCalculationRequest(
            request.isWin(), request.roundMargin(), request.kills(),
            request.deaths(), request.assists(), request.combatScore(), request.isAfk()
        ));
        log.info("POST /matches/submit - rank calc: deltaRR={}, tierStatus={}", rankRes.deltaRR(), rankRes.tierStatus());

        // 4. Archive roster FIRST (outside DB transaction)
        //    If archive fails, no match is persisted — atomicity preserved
        try {
            rosterClient.archiveRoster(request.rosterId(), token);
            log.info("POST /matches/submit - roster {} archived", request.rosterId());
        } catch (HttpClientResponseException e) {
            if (e.getStatus().getCode() == 409) {
                log.warn("POST /matches/submit - roster {} already archived (409), client inconsistent", request.rosterId());
                throw new HttpStatusException(
                    HttpStatus.CONFLICT,
                    "Match already submitted for roster " + request.rosterId()
                );
            }
            log.warn("POST /matches/submit - failed to archive roster {}: {}", request.rosterId(), e.getMessage());
            throw new HttpStatusException(
                HttpStatus.BAD_REQUEST,
                "Failed to archive roster " + request.rosterId() + ": " + e.getMessage()
            );
        } catch (Exception e) {
            log.warn("POST /matches/submit - failed to archive roster {}: {}", request.rosterId(), e.getMessage());
            throw new HttpStatusException(
                HttpStatus.BAD_REQUEST,
                "Failed to archive roster " + request.rosterId() + ": " + e.getMessage()
            );
        }

        // 5. Persist match in DB transaction
        try {
            jdbi.useTransaction(handle -> {
                handle.createUpdate("INSERT INTO matches (id, roster_id, map_name, result, round_margin) VALUES (:id, :rid, :map, :res, :margin)")
                    .bind("id", matchId)
                    .bind("rid", request.rosterId())
                    .bind("map", request.mapName())
                    .bind("res", request.isWin() ? "WIN" : "LOSS")
                    .bind("margin", request.roundMargin())
                    .execute();

                handle.createUpdate("INSERT INTO match_players (id, match_id, player_id, kills, deaths, assists, combat_score, delta_rr, tier_status) " +
                                    "VALUES (:id, :mid, :pid, :k, :d, :a, :score, :rr, :tier)")
                    .bind("id", UUID.randomUUID().toString())
                    .bind("mid", matchId)
                    .bind("pid", request.playerId())
                    .bind("k", request.kills())
                    .bind("d", request.deaths())
                    .bind("a", request.assists())
                    .bind("score", request.combatScore())
                    .bind("rr", rankRes.deltaRR())
                    .bind("tier", rankRes.tierStatus())
                    .execute();
            });
        } catch (Exception e) {
            // Compensate: unarchive roster so it can be retried
            try {
                rosterClient.unarchiveRoster(request.rosterId(), token);
                log.info("POST /matches/submit - compensated: roster {} unarchived", request.rosterId());
            } catch (Exception ignored) {
                log.warn("POST /matches/submit - compensation failed for roster {}: {}", request.rosterId(), ignored.getMessage());
            }
            throw new HttpStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to persist match: " + e.getMessage()
            );
        }

        log.info("POST /matches/submit - match recorded: matchId={}", matchId);
        return new MatchSubmissionResponse(matchId, rankRes.deltaRR(), rankRes.tierStatus());
    }

    @Get("/history/{playerId}")
    public HttpResponse<List<MatchHistoryItemDto>> getMatchHistory(String playerId) {
        log.info("GET /matches/history/{}", playerId);
        List<MatchHistoryItemDto> history = jdbi.withHandle(handle ->
            handle.createQuery("SELECT m.id, m.map_name, m.result, mp.delta_rr, mp.tier_status " +
                               "FROM matches m JOIN match_players mp ON m.id = mp.match_id " +
                               "WHERE mp.player_id = :pid ORDER BY m.played_at DESC")
                .bind("pid", playerId)
                .map((rs, ctx) -> new MatchHistoryItemDto(
                    rs.getString("id"),
                    rs.getString("map_name"),
                    rs.getString("result"),
                    rs.getInt("delta_rr"),
                    rs.getString("tier_status")
                ))
                .list()
        );
        log.info("GET /matches/history/{} - {} matches found", playerId, history.size());
        return HttpResponse.ok(history);
    }
}
