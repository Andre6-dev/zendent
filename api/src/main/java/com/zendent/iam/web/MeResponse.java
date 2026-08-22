package com.zendent.iam.web;

import java.util.List;
import java.util.UUID;

public record MeResponse(UUID userId, String email, UUID clinicId, List<String> roles) {
}
