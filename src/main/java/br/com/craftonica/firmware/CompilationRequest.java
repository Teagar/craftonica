package br.com.craftonica.firmware;

import java.util.UUID;

public final class CompilationRequest {
    private final String ownerId;
    private final UUID requestId;
    private final long revision;
    private final String profileId;
    private final SourceBundle sources;

    public CompilationRequest(String ownerId, UUID requestId, long revision, SourceBundle sources) {
        this(ownerId, requestId, revision, ToolchainProfile.ID, sources);
    }

    public CompilationRequest(String ownerId, UUID requestId, long revision, String profileId, SourceBundle sources) {
        if (ownerId == null || ownerId.trim().isEmpty() || ownerId.length() > 128)
            throw new IllegalArgumentException("Invalid compilation owner");
        if (requestId == null || revision < 0 || sources == null) throw new IllegalArgumentException("Invalid compilation request");
        if (!ToolchainProfile.ID.equals(profileId)) throw new IllegalArgumentException("Unsupported toolchain profile");
        this.ownerId = ownerId;
        this.requestId = requestId;
        this.revision = revision;
        this.profileId = profileId;
        this.sources = sources;
    }

    public String getOwnerId() { return ownerId; }
    public UUID getRequestId() { return requestId; }
    public long getRevision() { return revision; }
    public String getProfileId() { return profileId; }
    public SourceBundle getSources() { return sources; }
    public CompilationKey getCompilationKey() { return CompilationKey.from(sources); }
}
