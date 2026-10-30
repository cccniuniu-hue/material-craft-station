package com.xiaoying.workshop;

import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class IdentityService {
    private final WorkshopMapper mapper;
    private final SecureRandom random = new SecureRandom();
    public IdentityService(WorkshopMapper mapper) { this.mapper = mapper; }
    public ApiDtos.IdentityResponse create(String name) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Models.Identity row = new Models.Identity();
        row.id = UUID.randomUUID().toString(); row.displayName = name.strip();
        if (row.displayName.isEmpty()) throw new ApiException(400, "请输入用户名称");
        row.tokenHash = hash(token); row.createdAt = Instant.now();
        mapper.insertIdentity(row);
        return new ApiDtos.IdentityResponse(row.id, row.displayName, token);
    }
    public Models.Identity authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() > 200)
            throw new ApiException(401, "请先创建或切换用户身份");
        Models.Identity row = mapper.identityByToken(hash(authorization.substring(7)));
        if (row == null) throw new ApiException(401, "用户身份无效，请重新创建");
        return row;
    }
    private static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
