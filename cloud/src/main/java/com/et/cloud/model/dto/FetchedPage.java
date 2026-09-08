package com.et.cloud.model.dto;

import lombok.Data;

/**
 * A webpage downloaded by {@link com.et.cloud.service.WebPageFetcher}.
 */
@Data
public class FetchedPage {

    /**
     * Raw HTML body of the page.
     */
    private String html;

    /**
     * Url actually served, after following redirects.
     */
    private String finalUrl;

    /**
     * Page title read from {@code <title>}, may be blank.
     */
    private String pageTitle;
}
