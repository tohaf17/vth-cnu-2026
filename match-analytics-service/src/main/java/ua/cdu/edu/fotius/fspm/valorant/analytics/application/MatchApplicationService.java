package ua.cdu.edu.fotius.fspm.valorant.analytics.application;

import java.util.List;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import ua.cdu.edu.fotius.fspm.valorant.analytics.domain.model.Match;
import ua.cdu.edu.fotius.fspm.valorant.analytics.domain.model.MatchHistoryItem;
import ua.cdu.edu.fotius.fspm.valorant.analytics.domain.model.MatchStats;
import ua.cdu.edu.fotius.fspm.valorant.analytics.domain.repository.MatchRepository;
import ua.cdu.edu.fotius.fspm.valorant.analytics.infrastructure.client.RankCalculatorClient;
import ua.cdu.edu.fotius.fspm.valorant.analytics.infrastructure.client.RosterServiceClient;

@Singleton
public class MatchApplicationService {
    private final MatchRepository matchRepository;
    private final RankCalculatorClient rankClient;
    private final RosterServiceClient rosterClient;

    @Inject
    public MatchApplicationService(
        MatchRepository matchRepository,
        RankCalculatorClient rankClient,
        RosterServiceClient rosterClient
    ) {
        this.matchRepository = matchRepository;
        this.rankClient = rankClient;
        this.rosterClient = rosterClient;
    }

    public SubmissionResult submit(SubmissionCommand command, String token) {
        verifyLockedRoster(command.rosterId(), token);

        MatchStats stats = new MatchStats(
            command.isWin(),
            command.roundMargin(),
            command.kills(),
            command.deaths(),
            command.assists(),
            command.combatScore(),
            command.isAfk());

        RankCalculatorClient.RankCalculationResponse rating = rankClient.calculateRating(
            new RankCalculatorClient.RankCalculationRequest(
                stats.isWin(),
                stats.roundMargin(),
                stats.kills(),
                stats.deaths(),
                stats.assists(),
                stats.combatScore(),
                stats.isAfk()));

        archiveRoster(command.rosterId(), token);

        Match match = Match.create(
            command.rosterId(),
            command.playerId(),
            command.mapName(),
            stats,
            rating.deltaRR(),
            rating.tierStatus());

        try {
            matchRepository.save(match);
        } catch (Exception persistenceFailure) {
            try {
                rosterClient.unarchiveRoster(command.rosterId(), token);
            } catch (Exception ignored) { }
            throw new HttpStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to persist match: " + persistenceFailure.getMessage());
        }

        return new SubmissionResult(match.id(), rating.deltaRR(), rating.tierStatus());
    }

    public List<MatchHistoryItem> history(String playerId) {
        return matchRepository.findHistoryByPlayer(playerId);
    }

    private void verifyLockedRoster(String rosterId, String token) {
        try {
            RosterServiceClient.RosterResponse roster = rosterClient.getRoster(rosterId, token);
            if ("ARCHIVED".equals(roster.status())) {
                throw new HttpStatusException(
                    HttpStatus.CONFLICT,
                    "Match already submitted for roster " + rosterId);
            }
            if (!"LOCKED".equals(roster.status())) {
                throw new HttpStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Roster must be LOCKED before match submission (current status: "
                        + roster.status() + ")");
            }
        } catch (HttpStatusException exception) {
            throw exception;
        } catch (HttpClientResponseException exception) {
            throw new HttpStatusException(
                HttpStatus.BAD_REQUEST,
                "Could not verify roster status for roster " + rosterId + ": " + exception.getMessage());
        } catch (Exception exception) {
            throw new HttpStatusException(
                HttpStatus.BAD_REQUEST,
                "Could not verify roster status for roster " + rosterId + ": " + exception.getMessage());
        }
    }

    private void archiveRoster(String rosterId, String token) {
        try {
            rosterClient.archiveRoster(rosterId, token);
        } catch (HttpClientResponseException exception) {
            if (exception.getStatus().getCode() == HttpStatus.CONFLICT.getCode()) {
                throw new HttpStatusException(
                    HttpStatus.CONFLICT,
                    "Match already submitted for roster " + rosterId);
            }
            throw new HttpStatusException(
                HttpStatus.BAD_REQUEST,
                "Failed to archive roster " + rosterId + ": " + exception.getMessage());
        } catch (Exception exception) {
            throw new HttpStatusException(
                HttpStatus.BAD_REQUEST,
                "Failed to archive roster " + rosterId + ": " + exception.getMessage());
        }
    }

    public record SubmissionCommand(
        String rosterId,
        String playerId,
        String mapName,
        boolean isWin,
        int roundMargin,
        int kills,
        int deaths,
        int assists,
        int combatScore,
        boolean isAfk
    ) {}

    public record SubmissionResult(String matchId, int deltaRR, String tierStatus) {}
}
