package net.t00thpick1.residence.api.events;

import net.t00thpick1.residence.api.areas.ResidenceArea;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * This event should be called any time a ResidenceArea is created.
 * 
 * @author t00thpick1
 */
public class ResidenceAreaCreatedEvent extends Event {
    private final ResidenceArea residence;

    public ResidenceAreaCreatedEvent(ResidenceArea residence) {
        this.residence = residence;
    }

    /**
     * Gets the ResidenceArea being created.
     *
     * @return the ResidenceArea involved
     */
    public ResidenceArea getResidenceArea() {
        return residence;
    }

    private static final HandlerList handlerlist = new HandlerList();

    @Override
    public HandlerList getHandlers() {
        return handlerlist;
    }

    public static HandlerList getHandlerList() {
        return handlerlist;
    }
}
