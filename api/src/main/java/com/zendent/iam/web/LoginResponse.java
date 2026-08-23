package com.zendent.iam.web;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, String refreshToken) {
}
