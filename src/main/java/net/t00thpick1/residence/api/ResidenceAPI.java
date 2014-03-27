package net.t00thpick1.residence.api;

import org.bukkit.Location;
import org.bukkit.World;

import net.t00thpick1.residence.Residence;
import net.t00thpick1.residence.api.areas.CuboidArea;
import net.t00thpick1.residence.api.areas.PermissionsArea;
import net.t00thpick1.residence.protection.CuboidAreaFactory;

public class ResidenceAPI {
    public static PermissionsArea getPermissionsAreaByLocation(Location location) {
        PermissionsArea area = Residence.getInstance().getResidenceManager().getByLocation(location);
        if (area == null) {
            area = getResidenceWorld(location.getWorld());
        }
        return area;
    }

    public static ResidenceManager getResidenceManager() {
        return Residence.getInstance().getResidenceManager();
    }

    public static CuboidArea createCuboidArea(Location lowPoint, Location highPoint) {
        return CuboidAreaFactory.createNewCuboidArea(lowPoint, highPoint);
    }

    public static PermissionsArea getResidenceWorld(World world) {
        return Residence.getInstance().getWorldManager().getResidenceWorld(world);
    }
}
