package org.bottiger.podcast.webservices.datastore.gpodder.datatypes;

/**
 * Created by Arvid on 8/25/2015.
 */
public class GActionsList {
    private long timestamp;
    private GEpisodeAction[] actions;

    public GEpisodeAction[] getActions() {
        return actions;
    }
}
