package net.t00thpick1.residence.protection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.milkbowl.vault.economy.Economy;
import net.t00thpick1.residence.ConfigManager;
import net.t00thpick1.residence.Residence;
import net.t00thpick1.residence.api.Flag;
import net.t00thpick1.residence.api.ResidenceAPI;
import net.t00thpick1.residence.api.ResidenceArea;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class ClaimedResidence extends CuboidArea implements ResidenceArea {
    private String name;
    private ClaimedResidence parent;
    private Map<String, ClaimedResidence> subzoneObjects;
    private ConfigurationSection data;
    private Location tpLoc;
    private ConfigurationSection flags;
    private ConfigurationSection groupFlags;
    private ConfigurationSection playerFlags;
    private ConfigurationSection subzones;
    private ConfigurationSection marketData;
    private ConfigurationSection rentData;
    private List<ClaimedResidence> rentLinks;

    public ClaimedResidence(ConfigurationSection section, ClaimedResidence parent) throws Exception {
        super();
        name = section.getName();
        this.parent = parent;
        data = section.getConfigurationSection("Data");
        if (data.isConfigurationSection("RentData")) {
            rentData = data.getConfigurationSection("RentData");
        }
        marketData = data.getConfigurationSection("MarketData");
        initMarketState();
        loadArea(data.getConfigurationSection("Area"));
        loadTpLoc();
        flags = section.getConfigurationSection("Flags");
        groupFlags = section.getConfigurationSection("Groups");
        playerFlags = section.getConfigurationSection("Players");
        subzones = section.getConfigurationSection("Subzones");
        subzoneObjects = new HashMap<String, ClaimedResidence>();
        loadSubzones();
    }

    private void initMarketState() {
        if (isRented()) {
            EconomyManager.setRented(this);
        }
        if (isForRent()) {
            EconomyManager.setForRent(this);
        }
        if (isForSale()) {
            EconomyManager.setForSale(this);
        }
    }

    private void loadRentLinks() {
        // TODO
    }

    private void loadSubzones() throws Exception {
        for (String subzone : subzones.getKeys(false)) {
            subzoneObjects.put(subzone, new ClaimedResidence(subzones.getConfigurationSection(subzone), this));
        }
    }

    private void loadTpLoc() {
        if (!data.isConfigurationSection("TPLocation")) {
            data.createSection("TPLocation");
            setTeleportLocation(getCenter());
        }
        ConfigurationSection tpLocation = data.getConfigurationSection("TPLocation");
        tpLoc = new Location(getWorld(), tpLocation.getDouble("X"), tpLocation.getDouble("Y"), tpLocation.getDouble("Z"));
    }

    public boolean allowAction(Flag flag) {
        if (flags.contains(flag.getName())) {
            return flags.getBoolean(flag.getName());
        }
        if (flag.getParent() != null) {
            return allowAction(flag.getParent());
        }
        return ResidenceAPI.getResidenceWorld(world).allowAction(flag);
    }

    public boolean allowAction(Player player, Flag flag) {
        Flag origFlag = flag;
        String name = player.getName();
        while (true) {
            if (playerFlags.isConfigurationSection(name)) {
                ConfigurationSection playerPerms = playerFlags.getConfigurationSection(name);
                if (playerPerms.contains(flag.getName())) {
                    return playerPerms.getBoolean(flag.getName());
                }
            }

            String group = GroupManager.getPlayerGroup(player.getName());
            if (groupFlags.isConfigurationSection(group)) {
                ConfigurationSection groupPerms = groupFlags.getConfigurationSection(group);
                if (groupPerms.contains(flag.getName())) {
                    return groupPerms.getBoolean(flag.getName());
                }
            }
            if (rentData != null && rentData.getStringList("RentFlags").contains(flag.getName())) {
                for (ClaimedResidence rentLocation : rentLinks) {
                    if (rentLocation.getRenter() == player.getName()) {
                        return true;
                    }
                }
            }
            if (flags.contains(flag.getName())) {
                return flags.getBoolean(flag.getName());
            }
            if (flag.getParent() == null) {
                return ResidenceAPI.getResidenceWorld(world).allowAction(origFlag);
            } else {
                flag = flag.getParent();
            }
        }
    }

    public boolean addSubzone(String name, String owner, Location loc1, Location loc2) {
        if (subzones.isConfigurationSection(name)) {
            return false;
        }
        CuboidArea newArea = new CuboidArea(loc1, loc2);
        if (!isAreaWithin(newArea)) {
            return false;
        }
        for (ClaimedResidence subzone : getSubzoneList()) {
            if (subzone.checkCollision(newArea)) {
                return false;
            }
        }

        try {
            ConfigurationSection res = subzones.createSection(name);
            ConfigurationSection data = res.createSection("Data");
            data.set("Owner", owner);
            data.set("CreationDate", System.currentTimeMillis());
            data.set("EnterMessage", GroupManager.getDefaultEnterMessage(owner));
            data.set("LeaveMessage", GroupManager.getDefaultLeaveMessage(owner));
            newArea.saveArea(data.createSection("Area"));
            ConfigurationSection marketData = data.createSection("MarketData");
            marketData.set("ForSale", false);
            marketData.set("ForRent", false);
            marketData.set("Cost", 0);
            marketData.set("IsAutoRenew", ConfigManager.getInstance().isAutoRenewDefault());
            res.createSection("Flags");
            res.createSection("Groups");
            res.createSection("Players");
            res.createSection("Subzones");
            ClaimedResidence newres = new ClaimedResidence(res, this);
            subzoneObjects.put(name, newres);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public ClaimedResidence getSubzoneByLoc(Location loc) {
        ClaimedResidence residence = null;
        for (ClaimedResidence res : subzoneObjects.values()) {
            if (res.containsLocation(loc)) {
                residence = res;
                break;
            }
        }
        if (residence == null) {
            return null;
        }
        ClaimedResidence subrez = residence.getSubzoneByLoc(loc);
        if (subrez == null) {
            return residence;
        }
        return subrez;
    }

    public ClaimedResidence getSubzone(String subzonename) {
        if (!subzonename.contains(".")) {
            return subzoneObjects.get(subzonename);
        }
        String split[] = subzonename.split("\\.");
        ClaimedResidence get = subzoneObjects.get(split[0]);
        for (int i = 1; i < split.length; i++) {
            if (get == null) {
                return null;
            }
            get = get.getSubzone(split[i]);
        }
        return get;
    }

    public Collection<ClaimedResidence> getSubzoneList() {
        return subzoneObjects.values();
    }

    public Collection<String> getSubzoneNameList() {
        return subzoneObjects.keySet();
    }

    public ClaimedResidence getParent() {
        return parent;
    }

    public ClaimedResidence getTopParent() {
        if (parent != null) {
            return this;
        }
        return parent.getTopParent();
    }

    public int getSubzoneDepth() {
        int count = 0;
        ClaimedResidence res = parent;
        while (res != null) {
            count++;
            res = res.parent;
        }
        return count;
    }

    public boolean removeSubzone(String subzone) {
        if (subzoneObjects.remove(subzone) == null) {
            return false;
        }
        subzones.set(subzone, null);
        return true;
    }

    public String getEnterMessage() {
        return data.getString("EnterMessage");
    }

    public String getLeaveMessage() {
        return data.getString("LeaveMessage");
    }

    public void setEnterMessage(String message) {
        data.set("EnterMessage", message);
    }

    public void setLeaveMessage(String message) {
        data.set("LeaveMessage", message);
    }

    public Location getOutsideFreeLoc(Location insideLoc) {
        int maxIt = 100;
        if (!containsLocation(insideLoc)) {
            return insideLoc;
        }
        Location highLoc = getHighLocation();
        Location newLoc = new Location(highLoc.getWorld(), highLoc.getBlockX(), highLoc.getBlockY(), highLoc.getBlockZ());
        boolean found = false;
        int it = 0;
        while (!found && it < maxIt) {
            it++;
            Location lowLoc;
            newLoc.setX(newLoc.getBlockX() + 1);
            newLoc.setZ(newLoc.getBlockZ() + 1);
            lowLoc = new Location(newLoc.getWorld(), newLoc.getBlockX(), 254, newLoc.getBlockZ());
            newLoc.setY(255);
            while ((newLoc.getBlock().getTypeId() != 0 || lowLoc.getBlock().getTypeId() == 0) && lowLoc.getBlockY() > -126) {
                newLoc.setY(newLoc.getY() - 1);
                lowLoc.setY(lowLoc.getY() - 1);
            }
            if (newLoc.getBlock().getTypeId() == 0 && lowLoc.getBlock().getTypeId() != 0) {
                found = true;
            }
        }
        if (found) {
            return newLoc;
        } else {
            return getWorld().getSpawnLocation();
        }
    }

    public Location getTeleportLocation() {
        return tpLoc;
    }

    public boolean setTeleportLocation(Location location) {
        if (!this.containsLocation(location)) {
            return false;
        }
        tpLoc = location;
        ConfigurationSection tpLocation = data.getConfigurationSection("TPLocation");
        tpLocation.set("X", location.getX());
        tpLocation.set("Y", location.getY());
        tpLocation.set("Z", location.getZ());
        return true;
    }

    public boolean rename(String newName) {
        if (parent == null) {
            if (Residence.getInstance().getResidenceManager().rename(this, newName)) {
                this.name = newName;
                return true;
            }
            return false;
        } else {
            ClaimedResidence parent = getParent();
            if (parent.renameSubzone(name, newName)) {
                name = newName;
                return true;
            }
            return false;
        }
    }

    public boolean renameSubzone(String oldName, String newName) {
        if (!subzoneObjects.containsKey(oldName)) {
            return false;
        }
        if (subzoneObjects.containsKey(newName)) {
            return false;
        }
        subzones.createSection(newName, subzones.getConfigurationSection(oldName).getValues(true));
        subzones.set(oldName, null);
        subzoneObjects.put(newName, subzoneObjects.remove(oldName));
        return true;
    }

    public String getName() {
        return name;
    }

    public String getFullName() {
        if (parent != null) {
            return parent.getFullName() + "." + name;
        } else {
            return name;
        }
    }

    public String getOwner() {
        return data.getString("Owner");
    }

    public void setOwner(String string) {
        data.set("Owner", string);
        clearFlags();
        applyDefaultFlags();
    }

    public boolean isRented() {
        return rentData != null;
    }

    public String getRenter() {
        if (!isRented()) {
            throw new IllegalStateException("Unrented Residences have no expiration");
        }
        return rentData.getString("Renter");
    }

    public boolean rent(String renter, boolean autoRenew) {
        if (renter == null) {
            throw new IllegalArgumentException("Renter cannot be null");
        }
        if (rentData == null) {
            rentData = data.createSection("RentData");
        }
        rentData.set("Renter", renter);
        rentData.set("IsAutoRenew", autoRenew);
        EconomyManager.setRented(this);
        return checkRent();
    }

    public boolean checkRent() {
        if (System.currentTimeMillis() >= rentData.getLong("NextPayment", 0)) {
            return true;
        }
        Economy econ = Residence.getInstance().getEconomy();
        if (econ.getBalance(getRenter()) < getCost()) {
            evict();
            return false;
        }
        econ.withdrawPlayer(getRenter(), getCost());
        econ.depositPlayer(getOwner(), getCost());
        rentData.set("NextPayment", System.currentTimeMillis() + getRentPeriod());
        return true;
    }

    public long getRentPeriod() {
        return marketData.getLong("RentPeriod");
    }

    public int getCost() {
        return marketData.getInt("Cost");
    }

    public void evict() {
        rentData = null;
        data.set("RentData", null);
        if (!isForRent()) {
            marketData.set("Cost", 0);
            marketData.set("RentPeriod", 0);
        }
        EconomyManager.evict(this);
        return;
    }

    public boolean isAutoRenew() {
        if (!isRented()) {
            throw new IllegalStateException("Unrented Residence");
        }
        return rentData.getBoolean("IsAutoRenew");
    }

    public boolean isAutoRenewEnabled() {
        return marketData.getBoolean("IsAutoRenew");
    }

    public void setAutoRenew(boolean autoRenew) {
        if (!isRented()) {
            throw new IllegalStateException("Unrented Residence");
        }
        rentData.set("IsAutoRenew", autoRenew);
    }

    public void setAutoRenewEnabled(boolean autoRenew) {
        marketData.set("IsAutoRenew", autoRenew);
    }

    public List<Player> getPlayersInResidence() {
        List<Player> within = new ArrayList<Player>();
        Player[] players = Residence.getInstance().getServer().getOnlinePlayers();
        for (Player player : players) {
            if (containsLocation(player.getLocation())) {
                within.add(player);
            }
        }
        return within;
    }

    @Override
    public void setFlag(Flag flag, Boolean value) {
        flags.set(flag.getName(), value);
    }

    @Override
    public void setGroupFlag(String group, Flag flag, Boolean value) {
        ConfigurationSection groupPerms;
        if (!groupFlags.isConfigurationSection(group)) {
            groupPerms = groupFlags.createSection(group);
        } else {
            groupPerms = groupFlags.getConfigurationSection(group);
        }
        groupPerms.set(flag.getName(), value);
    }

    @Override
    public void setPlayerFlag(String player, Flag flag, Boolean value) {
        ConfigurationSection playerPerms;
        if (!playerFlags.isConfigurationSection(player)) {
            playerPerms = playerFlags.createSection(player);
        } else {
            playerPerms = playerFlags.getConfigurationSection(player);
        }
        playerPerms.set(flag.getName(), value);
    }

    @Override
    public boolean isForRent() {
        return marketData.getBoolean("ForRent");
    }

    @Override
    public void setForRent(int cost, long rentPeriod, boolean isAutoRenewEnabled) {
        marketData.set("ForRent", true);
        marketData.set("Cost", cost);
        marketData.set("RentPeriod", rentPeriod);
        marketData.set("IsAutoRenew", isAutoRenewEnabled);
        EconomyManager.setForRent(this);
    }

    @Override
    public boolean isForSale() {
        return marketData.getBoolean("ForSale");
    }

    @Override
    public void setForSale(int cost) {
        marketData.set("ForSale", true);
        marketData.set("Cost", cost);
        EconomyManager.setForSale(this);
    }

    
    public void removeFromMarket() {
        marketData.set("ForSale", false);
        marketData.set("ForRent", false);
        EconomyManager.removeFromSale(this);
        EconomyManager.removeFromRent(this);
        if (!isRented()) {
            marketData.set("Cost", 0);
            marketData.set("RentPeriod", 0);
        }
    }
    @Override
    public boolean buy(String buyer) {
        Economy econ = Residence.getInstance().getEconomy();
        if (econ.getBalance(buyer) < getCost()) {
            return false;
        }
        econ.withdrawPlayer(buyer, getCost());
        econ.depositPlayer(getOwner(), getCost());
        removeFromMarket();
        EconomyManager.removeFromSale(this);
        setOwner(buyer);
        return true;
    }

    @Override
    public long getLastPaymentDate() {
        if (!isRented()) {
            throw new IllegalStateException("Unrented Residence");
        }
        return rentData.getLong("NextPayment");
    }

    public void applyDefaultFlags() {
        for (Entry<Flag, Boolean> defaultFlag : GroupManager.getDefaultAreaFlags(getOwner()).entrySet()) {
            setFlag(defaultFlag.getKey(), defaultFlag.getValue());
        }
        for (Entry<Flag, Boolean> defaultFlag : GroupManager.getDefaultOwnerFlags(getOwner()).entrySet()) {
            setPlayerFlag(getOwner(), defaultFlag.getKey(), defaultFlag.getValue());
        }
        for (Entry<String, Map<Flag, Boolean>> group : GroupManager.getDefaultGroupFlags(getOwner()).entrySet()) {
            for (Entry<Flag, Boolean> defaultFlag : group.getValue().entrySet()) {
                setGroupFlag(group.getKey(), defaultFlag.getKey(), defaultFlag.getValue());
            }
        }
    }

    public void clearFlags() {
        removeAllPlayerFlags();
        removeAllGroupFlags();
        removeAllAreaFlags();
    }

    public void copyPermissions(ClaimedResidence mirror) {
        ConfigurationSection parent = playerFlags.getParent();
        parent.set("Players", null);
        playerFlags = parent.createSection("Players", mirror.playerFlags.getValues(true));
        parent = groupFlags.getParent();
        parent.set("Groups", null);
        groupFlags = parent.createSection("Groups", mirror.groupFlags.getValues(true));
        parent = flags.getParent();
        parent.set("Flags", null);
        flags = parent.createSection("Flags", mirror.flags.getValues(true));
    }

    public void removeAllPlayerFlags() {
        ConfigurationSection parent = playerFlags.getParent();
        parent.set("Players", null);
        playerFlags = parent.createSection("Players");
    }

    public void removeAllGroupFlags() {
        ConfigurationSection parent = groupFlags.getParent();
        parent.set("Groups", null);
        groupFlags = parent.createSection("Groups");
    }

    public void removeAllAreaFlags() {
        ConfigurationSection parent = flags.getParent();
        parent.set("Flags", null);
        flags = parent.createSection("Flags");
    }

    public void printInformation(Player player) {
        // TODO Auto-generated method stub

    }

    public void printMarketInfo(Player player) {
        // TODO Auto-generated method stub
        
    }
}
