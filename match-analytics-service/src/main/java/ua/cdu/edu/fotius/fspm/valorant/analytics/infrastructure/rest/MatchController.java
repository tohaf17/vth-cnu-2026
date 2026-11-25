package ua.cdu.edu.fotius.fspm.valorant.analytics.infrastructure.rest;

import java.util.List;
import java.util.Optional;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Inject;
import ua.cdu.edu.fotius.fspm.valorant.analytics.application.MatchApplicationService;
import ua.cdu.edu.fotius.fspm.valorant.analytics.domain.model.MatchHistoryItem;

@Controller("/api/v1/matches")
public class MatchController {
    private final MatchApplicationService matchService;

    @Inject
    public MatchController(MatchApplicationService matchService) {
        this.matchService = matchService;
    }

    @Serdeable
    public record MatchSubmissionRequest(
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

    @Serdeable
    public record MatchSubmissionResponse(String matchId, int deltaRR, String tierStatus) {}

    @Serdeable
    public record MatchHistoryItemDto(
        String matchId,
        String mapName,
        String result,
        int deltaRR,
        String tierStatus
    ) {}

    @ExecuteOn(TaskExecutors.BLOCKING)
    @Post("/submit")
    public MatchSubmissionResponse submitMatch(
        @Body MatchSubmissionRequest request,
        @Header("Authorization") Optional<String> authHeader
    ) {
        String token = authHeader
            .filter(header -> !header.isBlank() && header.startsWith("Bearer "))
            .orElseThrow(() -> new HttpStatusException(
                HttpStatus.UNAUTHORIZED,
                "Missing or invalid Bearer token"));

        MatchApplicationService.SubmissionCommand command = new MatchApplicationService.SubmissionCommand(
            request.rosterId(),
            request.playerId(),
            request.mapName(),
            request.isWin(),
            request.roundMargin(),
            request.kills(),
            request.deaths(),
            request.assists(),
            request.combatScore(),
            request.isAfk());

        MatchApplicationService.SubmissionResult result = matchService.submit(command, token);
        return new MatchSubmissionResponse(result.matchId(), result.deltaRR(), result.tierStatus());
    }

    @Get("/history/{playerId}")
    public HttpResponse<List<MatchHistoryItemDto>> getMatchHistory(String playerId) {
        List<MatchHistoryItemDto> history = matchService.history(playerId).stream()
            .map(this::toDto)
            .toList();
        return HttpResponse.ok(history);
    }

    private MatchHistoryItemDto toDto(MatchHistoryItem item) {
        return new MatchHistoryItemDto(
            item.matchId(),
            item.mapName(),
            item.result(),
            item.deltaRR(),
            item.tierStatus());
    }
}
