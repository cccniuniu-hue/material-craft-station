package com.xiaoying.workshop;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class ApiDtos {
    private ApiDtos() {}
    public record IdentityRequest(@NotBlank @Size(max = 40) String displayName) {}
    public record IdentityResponse(String id, String displayName, String token) {}
    public record Me(String id, String displayName) {}
    public record TemplateResponse(String id, String title, String category, String coverUrl,
            String description, int requiredImageCount, List<String> acceptedMimeTypes, long maxFileSizeBytes) {
        static TemplateResponse from(Models.Template row) {
            return new TemplateResponse(row.id, row.title, row.category, row.coverUrl, row.description,
                    row.requiredImageCount, List.of("image/png", "image/jpeg"), row.maxFileSizeBytes);
        }
    }
    public record AssetResponse(String id, String url, String originalName, String mimeType, long sizeBytes) {}
    public record Simulation(@Min(500) @Max(5000) Integer delayMs,
            @Pattern(regexp = "CHECKING|GENERATING|FINALIZING") String failStage) {}
    public record TaskRequest(@NotBlank String templateId,
            @NotEmpty @Size(max = 6) List<@NotBlank String> assetIds, @Valid Simulation simulation) {}
    public record StepResponse(String stage, String status, Instant startedAt, Instant finishedAt, String errorMessage) {
        static StepResponse from(Models.Step row) {
            return new StepResponse(row.stage, row.status, row.startedAt, row.finishedAt, row.errorMessage);
        }
    }
    public record TaskResponse(String id, String templateId, String templateTitle, String coverUrl,
            String status, String stage, String resultUrl, String errorMessage,
            Instant createdAt, Instant updatedAt, List<StepResponse> steps) {}
}
