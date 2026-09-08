package com.et.cloud.service;

import com.et.cloud.model.dto.FetchedPage;

import java.io.IOException;

/**
 * Downloads an external webpage for batch import.
 *
 * Implementations validate every request hop so a user supplied url cannot be used to reach a
 * loopback, private or link-local address, and enforce the configured timeout, redirect and size
 * limits.
 */
public interface WebPageFetcher {

    /**
     * Downloads the given url.
     *
     * @throws java.io.IOException                  network or limit failure
     * @throws com.et.cloud.exception.BusinessException unsafe, malformed or unreachable target
     */
    FetchedPage fetch(String url) throws IOException;
}
