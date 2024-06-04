package sampleagent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
// import java.util.Set;

import org.apache.log4j.Logger;
// import org.dom4j.Entity;

// import com.vividsolutions.jts.geom.impl.PackedCoordinateSequence.Double;

import org.jfree.chart.block.Block;
import rescuecore2.messages.Command;
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
// import rescuecore2.misc.geometry.Vector2D;
// import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.*;
// import rescuecore2.standard.entities.StandardPropertyURN;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.worldmodel.Entity;
import rescuecore2.worldmodel.EntityID;
import sample.AbstractSampleAgent;
import sample.DistanceSorter;

public class SampleDrone extends AbstractSampleAgent<Drone> {

    private static final Logger LOG = Logger.getLogger(SampleDrone.class);
    private static final int VISION_RANGE = 500;
    private Collection<EntityID> unexploredBuildings;


    private int distance;


    @Override
    public String toString() {
        return "Sample drone";
    }

    @Override
    protected void postConnect() {
        super.postConnect();
        model.indexClass(StandardEntityURN.CIVILIAN, StandardEntityURN.BUILDING,
                StandardEntityURN.RESCUE_ROBOT, StandardEntityURN.DRONE,
                StandardEntityURN.REFUGE, StandardEntityURN.HYDRANT,
                StandardEntityURN.FIRE_BRIGADE, StandardEntityURN.GAS_STATION);
        unexploredBuildings = new HashSet<EntityID>(buildingIDs);
    }

    @Override
    protected void think(int time, ChangeSet changed, Collection<Command> heard) {
        if(time == config.getIntValue(kernel.KernelConstants.IGNORE_AGENT_COMMANDS_KEY)) {
            //subscribe to channel 1
            sendSubscribe(time, 1);
        }
        for (Command next : heard){
            LOG.debug("Heard " + next);
        }
        updateUnexploredBuildings(changed);
        //If near a blockade
        //fly over it
//        Blockade target = getTargetBlockade();
//        if (target != null) {
//            List<EntityID> path = search.breadthFirstSearch(me().getPosition(), getBlockedRoads());
//            if (path != null) {
//                Road road = (Road) model.getEntity(path.get(path.size() - 1));
//                Blockade blockade = getTargetBlockade(road, -1);
//                sendMove(time, path, blockade.getX(), blockade.getY());
//                return;
//            }
//        }
        //go through targets and see if there are any civilians
        for (Human next : getTargets()) {
            if(next.getPosition().equals(location().getID())) {
                //Target civilians that might need rescuing 
                if((next instanceof Civilian) && next.getBuriedness() == 0
                    && !(location() instanceof Refuge)) {
                        int x = me().getX();
                        int y = me().getY();
                        LOG.info("Civilians detected at: " + x + ", " + y);
                        //Send coordinates to police office 
                        sendCoordinatesToPolice(1, x, y);
                        return;
                    }
            } else {
                //try to move to target
                List<EntityID> path = search.breadthFirstSearch(me().getPosition(), next.getPosition());
                if(path != null){
                    LOG.info("Moving to target");
                    sendMove(time, path);
                    // fly command
//                    sendFly(time, path);
                    return;
                }
            }
        }

        // Keep exploring
        List<EntityID> path = search.breadthFirstSearch(me().getPosition(), unexploredBuildings);
        if(path != null) {
            LOG.info("Searching map");
            sendMove(time, path);
//            sendFly(time, path);
            return;
        }
        LOG.info("Flying in random direction");
        sendMove(time, randomWalk());
//        sendFly(time, randomWalk());
    }



    @Override
    protected EnumSet<StandardEntityURN> getRequestedEntityURNsEnum() {
        return EnumSet.of(StandardEntityURN.DRONE);
    }

//    private void goThroughBlockade(int time, Blockade blockade) {
//        Collection<StandardEntity> roads = model.getEntitiesOfType(StandardEntityURN.ROAD);
//        List<EntityID> res = new ArrayList<EntityID>();
//        for (StandardEntity next : roads) {
//
//        }
////        sendClear(time, blockade.getID());
////        sendMove(time, randomWalk());
//
//    }

    private void sendCoordinatesToPolice(int time, int x, int y) {
        Collection<StandardEntity> entities = model.getEntitiesOfType(StandardEntityURN.RESCUE_ROBOT);
        for (StandardEntity entity : entities) {
            int policeOfficeId = entity.getID().getValue();
            sendSpeak(time, policeOfficeId, ("Civilians detected at " + x + ", " + y).getBytes());
            LOG.info("Send help!");
        }        
    }

    private List<Human> getTargets() {
        List<Human> targets = new ArrayList<Human>();
        for (StandardEntity next: model.getEntitiesOfType(
            StandardEntityURN.CIVILIAN)) {
                Human human = (Human) next;
                if(human == me()) {
                    continue;
                }
                if (human.isHPDefined() && human.isBuriednessDefined() && human.isDamageDefined()
                    && human.isPositionDefined() && human.getHP() > 0 && (human.getBuriedness() > 0 || human.getDamage() > 0)) {
                        targets.add(human);
                }
        }
        Collections.sort(targets, new DistanceSorter(location(), model));
        return targets;
    }

    private void updateUnexploredBuildings(ChangeSet changed) {
        for(EntityID next : changed.getChangedEntities()) {
            unexploredBuildings.remove(next);
        }
    }

    private List<EntityID> getBlockedRoads() {
        Collection<
                StandardEntity> e = model.getEntitiesOfType(StandardEntityURN.ROAD);
        List<EntityID> result = new ArrayList<EntityID>();
        for (StandardEntity next : e) {
            Road r = (Road) next;
            if (r.isBlockadesDefined() && !r.getBlockades().isEmpty()) {
                result.add(r.getID());
            }
        }
        return result;
    }

    private int findDistanceTo(Blockade b, int x, int y) {
        // Logger.debug("Finding distance to " + b + " from " + x + ", " + y);
        List<Line2D> lines = GeometryTools2D.pointsToLines(
            GeometryTools2D.vertexArrayToPoints(b.getApexes()), true);
        double best = Double.MAX_VALUE;
        Point2D origin = new Point2D(x, y);
        for (Line2D next : lines) {
          Point2D closest = GeometryTools2D.getClosestPointOnSegment(next, origin);
          double d = GeometryTools2D.getDistance(origin, closest);
          // Logger.debug("Next line: " + next + ", closest point: " + closest + ",
          // distance: " + d);
          if (d < best) {
            best = d;
            // Logger.debug("New best distance");
          }
    
        }
        return (int) best;
      }

    private Blockade getTargetBlockade() {
        LOG.debug("Looking for target blockade");
        Area location = (Area) location();
        LOG.debug("Looking in current location");
        Blockade result = getTargetBlockade(location, distance);
        if (result != null) {
            return result;
        }
        LOG.debug("Looking in neighboring locations");
        for (EntityID next : location.getNeighbours()) {
            location = (Area) model.getEntity(next);
            result = getTargetBlockade(location, distance);
            if (result != null) {
                return result;
            }
        }
        return null;
    }


    private Blockade getTargetBlockade(Area area, int maxDistance) {
        // Logger.debug("Looking for nearest blockade in " + area);
        if (area == null || !area.isBlockadesDefined()) {
            // Logger.debug("Blockades undefined");
            return null;
        }
        List<EntityID> ids = area.getBlockades();
        // Find the first blockade that is in range.
        int x = me().getX();
        int y = me().getY();
        for (EntityID next : ids) {
            Blockade b = (Blockade) model.getEntity(next);
            double d = findDistanceTo(b, x, y);
            // Logger.debug("Distance to " + b + " = " + d);
            if (maxDistance < 0 || d < maxDistance) {
                // Logger.debug("In range");
                return b;
            }
        }
        // Logger.debug("No blockades in range");
        return null;
    }
  
 
}
