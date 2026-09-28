package dev.zynema.bff.dto;

import java.util.List;

/**
 * The landing screen: what to feature and which rails to render.
 *
 * <p>Anonymous by design — browsing must work before signing up. Watching is
 * what needs an account and an active subscription; that gate lives in
 * playback-service, and the detail view surfaces it so the UI can show a
 * paywall instead of a broken play button.
 */
public record HomeView(Hero hero, List<Row> rows) {

    /**
     * The featured title: the full card plus the two fields the hero needs and
     * a poster does not (a headline needs a synopsis to read as a story).
     */
    public record Hero(TitleCard card, String synopsis, String tagline) {
    }

    /**
     * @param id stable key the frontend uses for ordering and analytics
     */
    public record Row(String id, String title, List<TitleCard> items) {
    }
}
