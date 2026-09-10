package data.missions.omega_ai3_trial;

import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;

/** Fixed equipment and map for manual A/B trials; battle simulation itself is not deterministic. */
public final class MissionDefinition implements MissionDefinitionPlugin {
    @Override public void defineMission(MissionDefinitionAPI api) {
        api.initFleet(FleetSide.PLAYER, "OME", FleetGoal.ATTACK, false, 5);
        api.initFleet(FleetSide.ENEMY, "SIM", FleetGoal.ATTACK, true, 5);
        api.setFleetTagline(FleetSide.PLAYER, "Heavy anchor with fast escorts");
        api.setFleetTagline(FleetSide.ENEMY, "Mixed opposition and mobile distractions");
        api.addBriefingItem("Use autopilot for the flagship to compare autonomous fleets.");
        api.addBriefingItem("Start in Observe mode, then repeat with Coordinate selected in LunaLib.");
        api.addBriefingItem("Keep other settings and orders identical. Record losses, chase time, and the combat log.");
        api.addToFleet(FleetSide.PLAYER, "onslaught_Standard", FleetMemberType.SHIP, "Anchor", true);
        api.addToFleet(FleetSide.PLAYER, "hammerhead_Balanced", FleetMemberType.SHIP, "Support", false);
        api.addToFleet(FleetSide.PLAYER, "wolf_Assault", FleetMemberType.SHIP, "Scout One", false);
        api.addToFleet(FleetSide.PLAYER, "wolf_Assault", FleetMemberType.SHIP, "Scout Two", false);
        api.addToFleet(FleetSide.ENEMY, "eagle_Assault", FleetMemberType.SHIP, false);
        api.addToFleet(FleetSide.ENEMY, "hammerhead_Balanced", FleetMemberType.SHIP, false);
        api.addToFleet(FleetSide.ENEMY, "hammerhead_Balanced", FleetMemberType.SHIP, false);
        api.addToFleet(FleetSide.ENEMY, "tempest_Attack", FleetMemberType.SHIP, false);
        api.addToFleet(FleetSide.ENEMY, "lasher_Strike", FleetMemberType.SHIP, false);
        api.initMap(-6500, 6500, -6500, 6500);
        api.addObjective(-2400, 0, "nav_buoy");
        api.addObjective(2400, 0, "sensor_array");
    }
}
