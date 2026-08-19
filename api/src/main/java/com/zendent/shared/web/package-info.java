/**
 * Cross-cutting web infrastructure: {@code GlobalExceptionHandler} maps
 * exceptions to RFC 7807 {@code ProblemDetail} responses (task 2.1.10), and
 * {@code ProblemDetailWriter} produces the same response shape for security
 * components that run inside the filter chain, before MVC dispatch
 * (task 2.1.7, design D7 "filter-chain gap").
 */
package com.zendent.shared.web;
