package dev.sergiomarin.sitewatch.site;

/** Confirmed state of a site; a single failed check does not make it DOWN. */
public enum SiteStatus {
    UNKNOWN, UP, DOWN
}
