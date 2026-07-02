/**
 * The {@code iam} module: clinic onboarding, authentication (JWT issuance and
 * refresh), and staff invitation. Closed by default — internals ({@code domain},
 * {@code internal}, {@code web}, {@code mapper}) are module-private; anything
 * other modules must see is exposed via a {@code @NamedInterface} or, preferably,
 * published domain events owned by {@code shared}.
 */
package com.zendent.iam;
