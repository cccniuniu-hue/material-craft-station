package com.xiaoying.workshop;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.time.Instant;
import java.util.UUID;

@Service
public class AssetService {
    private final WorkshopMapper mapper;
    private final FileStorage storage;
    public AssetService(WorkshopMapper mapper, FileStorage storage) { this.mapper = mapper; this.storage = storage; }
    public ApiDtos.AssetResponse upload(String userId, MultipartFile file) {
        FileStorage.StoredFile saved = storage.save(file);
        Models.Asset row = new Models.Asset();
        row.id = UUID.randomUUID().toString(); row.userId = userId; row.relativePath = saved.relativePath();
        row.originalName = saved.originalName(); row.mimeType = saved.mimeType(); row.sizeBytes = saved.sizeBytes();
        row.createdAt = Instant.now();
        try { mapper.insertAsset(row); }
        catch (RuntimeException e) {
            try { storage.delete(saved.relativePath()); } catch (RuntimeException cleanup) { e.addSuppressed(cleanup); }
            throw e;
        }
        return new ApiDtos.AssetResponse(row.id, "/media/" + row.relativePath, row.originalName, row.mimeType, row.sizeBytes);
    }
}
