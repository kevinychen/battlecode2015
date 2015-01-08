package robot1;

import battlecode.common.*;
import java.util.*;

public class RobotPlayer {
    static final Direction[] directions = {Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST, Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST};
    static final RobotType[] BROADCAST_TYPES = {RobotType.BEAVER, RobotType.MINER, RobotType.MINERFACTORY, RobotType.HELIPAD, RobotType.AEROSPACELAB, RobotType.LAUNCHER};
    static final int I_BEAVER = RobotType.BEAVER.ordinal();
    static final int I_MINER = RobotType.MINER.ordinal();
    static final int I_MINERFACTORY = RobotType.MINERFACTORY.ordinal();
    static final int I_HELIPAD = RobotType.HELIPAD.ordinal();
    static final int I_LAB = RobotType.AEROSPACELAB.ordinal();
    static final int RUSH_TIME = 1000;
    static final int HQ_TO_MINERFACTORY_ST = 9000;
    static final int HQ_TO_LAB_ST = 15000;
    static final int MINERFACTORY_TO_MINER_ST = 3000;
    static final int LAB_TO_LAUNCHER_ST = 5000;
    
    // Build orders/compositions
    static final int[] START_ORDER = new int[RobotType.values().length];
    static final int[] COMP_RATIO = new int[RobotType.values().length];
    static {
        START_ORDER[RobotType.BEAVER.ordinal()] = 3; 
        START_ORDER[RobotType.MINERFACTORY.ordinal()] = 2; 
        START_ORDER[RobotType.MINER.ordinal()] = 10; 
        START_ORDER[RobotType.HELIPAD.ordinal()] = 1; 
        START_ORDER[RobotType.AEROSPACELAB.ordinal()] = 4; 

        COMP_RATIO[RobotType.MINER.ordinal()] = 5; 
        COMP_RATIO[RobotType.AEROSPACELAB.ordinal()] = 1; 
        COMP_RATIO[RobotType.LAUNCHER.ordinal()] = 5; 
    }
    
    // Message channels
    static final int ROUND_NUM = 0;
    static final int POP_COUNT = 1;

    // Game constants
    static RobotController rc;
    static Team myTeam;
    static Team enemyTeam;
    static RobotType myType;
    static int mySensors;
    static int myRange;
    static int producedRound;
    static MapLocation myHQ;
    static MapLocation enemyHQ;
    
    // Round constants
    static int roundNum;
    static MapLocation myLoc;
    static double mySupply;
    static double teamOre;
    
    // Other
    static Direction lastMoveDir;
    
    public static void run(RobotController tomatojuice) {
        try {
            rc = tomatojuice;
            myTeam = rc.getTeam();
            enemyTeam = myTeam.opponent();
            myType = rc.getType();
            mySensors = myType.sensorRadiusSquared;
            myRange = myType.attackRadiusSquared;
            producedRound = rc.readBroadcast(ROUND_NUM);
            myHQ = rc.senseHQLocation();
            enemyHQ = rc.senseEnemyHQLocation();
        } catch (Exception e) {
            e.printStackTrace();
        }

        while(true) {
            try {
                myLoc = rc.getLocation();
                mySupply = rc.getSupplyLevel();
                teamOre = rc.getTeamOre();

                if (myType == RobotType.HQ) {
                    roundNum++;
                    rc.broadcast(ROUND_NUM, roundNum);

                    int[] popCounts = new int[RobotType.values().length];
                    for (RobotInfo r : rc.senseNearbyRobots(999999, myTeam)) {
                        RobotType type = r.type;
                        popCounts[type.ordinal()]++;
                    }
                    for (RobotType type : BROADCAST_TYPES)
                        rc.broadcast(POP_COUNT + type.ordinal(), popCounts[type.ordinal()]);
                } else {
                    roundNum = rc.readBroadcast(ROUND_NUM);
                }
                
                if (myType == RobotType.HQ)
                    runHQ();
                else if (myType == RobotType.TOWER)
                    runTower();
                else if (myType == RobotType.BEAVER)
                    runBeaver();
                else if (myType == RobotType.MINER)
                    runMiner();
                else if (myType == RobotType.MINERFACTORY)
                    runMinerFactory();
                else if (myType == RobotType.AEROSPACELAB)
                    runLab();
                else if (myType == RobotType.LAUNCHER)
                    runLauncher();
                else if (myType == RobotType.MISSILE)
                    runMissile();
                
                rc.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static void runHQ() throws Exception {
        for (RobotInfo r : rc.senseNearbyRobots(GameConstants.SUPPLY_TRANSFER_RADIUS_SQUARED, myTeam))
            if (r.type == RobotType.MINERFACTORY && r.supplyLevel < HQ_TO_MINERFACTORY_ST && mySupply >= HQ_TO_MINERFACTORY_ST) {
                rc.transferSupplies(HQ_TO_MINERFACTORY_ST, r.location);
                mySupply -= HQ_TO_MINERFACTORY_ST;
            } else if (r.type == RobotType.AEROSPACELAB && r.supplyLevel < HQ_TO_LAB_ST && mySupply >= HQ_TO_LAB_ST) {
                rc.transferSupplies(HQ_TO_LAB_ST, r.location);
                mySupply -= HQ_TO_LAB_ST;
            }

        int numBeavers = rc.readBroadcast(POP_COUNT + I_BEAVER);
        if (numBeavers < START_ORDER[I_BEAVER])
            trySpawn(RobotType.BEAVER);
        attackSomething();
    }

    static void runTower() throws Exception {
        attackSomething();
    }

    static void runBeaver() throws Exception {
        int numMinerFactories = rc.readBroadcast(POP_COUNT + I_MINERFACTORY);
        int numHelipads = rc.readBroadcast(POP_COUNT + I_HELIPAD);
        int numLabs = rc.readBroadcast(POP_COUNT + I_LAB);
        if (numMinerFactories < START_ORDER[I_MINERFACTORY])
            tryBuild(RobotType.MINERFACTORY, GameConstants.SUPPLY_TRANSFER_RADIUS_SQUARED);
        else if (numHelipads < START_ORDER[I_HELIPAD])
            tryBuild(RobotType.HELIPAD, GameConstants.SUPPLY_TRANSFER_RADIUS_SQUARED);
        else if (numLabs < START_ORDER[I_LAB])
            tryBuild(RobotType.AEROSPACELAB, GameConstants.SUPPLY_TRANSFER_RADIUS_SQUARED);

        if (rc.senseOre(myLoc) > 0 && rc.isCoreReady() && rc.canMine())
            rc.mine();

        if (myLoc.distanceSquaredTo(myHQ) <= 35)
            tryWander(directionToInt(myLoc.directionTo(myHQ)));
        else
            tryRandomMove();
    }

    static void runMiner() throws Exception {
        if (rc.senseOre(myLoc) > 0 && rc.isCoreReady() && rc.canMine())
            rc.mine();
        
        if (lastMoveDir == null)
            lastMoveDir = tryRandomMove();
        else {
            Direction dir = tryWander(directionToInt(lastMoveDir));
            if (dir != null)
                lastMoveDir = dir;
        }
    }

    static void runMinerFactory() throws Exception {
        for (RobotInfo r : rc.senseNearbyRobots(GameConstants.SUPPLY_TRANSFER_RADIUS_SQUARED, myTeam))
            if (r.type == RobotType.MINER && r.supplyLevel < MINERFACTORY_TO_MINER_ST && mySupply >= MINERFACTORY_TO_MINER_ST) {
                rc.transferSupplies(MINERFACTORY_TO_MINER_ST, r.location);
                mySupply -= MINERFACTORY_TO_MINER_ST;
            }
        
        int numMiners = rc.readBroadcast(POP_COUNT + I_MINER);
        if (numMiners < START_ORDER[I_MINER])
            trySpawn(RobotType.MINER);
    }

    static void runLab() throws Exception {
        for (RobotInfo r : rc.senseNearbyRobots(GameConstants.SUPPLY_TRANSFER_RADIUS_SQUARED, myTeam))
            if (r.type == RobotType.LAUNCHER && r.supplyLevel < LAB_TO_LAUNCHER_ST && mySupply >= LAB_TO_LAUNCHER_ST) {
                rc.transferSupplies(LAB_TO_LAUNCHER_ST, r.location);
                mySupply -= LAB_TO_LAUNCHER_ST;
            }

        trySpawn(RobotType.LAUNCHER);
    }

    static void runLauncher() throws Exception {
        RobotInfo[] enemies = rc.senseNearbyRobots(36, enemyTeam);
        if (enemies.length > 0)
            tryLaunch(directionToInt(myLoc.directionTo(enemies[0].location)));
        else
        {
            if (roundNum < RUSH_TIME)
                tryRandomMove();
            else
                tryMove(directionToInt(myLoc.directionTo(enemyHQ)));
        }
    }

    static void runMissile() throws Exception {
        if (rc.senseNearbyRobots(myRange, enemyTeam).length > 0 &&
                rc.senseNearbyRobots(myRange, myTeam).length == 0)
        {
            rc.explode();
        }
        else
        {
            RobotInfo[] enemies = rc.senseNearbyRobots(mySensors, enemyTeam);
            if (enemies.length != 0)
            {
                tryMove(directionToInt(myLoc.directionTo(enemies[0].location)));
                return;
            }
            RobotInfo[] allies = rc.senseNearbyRobots(mySensors, myTeam);
            if (allies.length != 0)
            {
                tryMove(directionToInt(myLoc.directionTo(allies[0].location).opposite()));
                return;
            }
            tryRandomMove();
        }
    }

    // This method will attack an enemy in sight, if there is one
    static void attackSomething() throws GameActionException {
        if (!rc.isWeaponReady())
            return;
        RobotInfo[] enemies = rc.senseNearbyRobots(myRange, enemyTeam);
        if (enemies.length > 0) {
            rc.attackLocation(enemies[0].location);
        }
    }
    
    static Direction tryRandomMove() throws GameActionException {
        return tryWander((int) (Math.random() * 8));
    }
    
    static Direction tryWander(int dirint) throws GameActionException {
        if (!rc.isCoreReady())
            return null;
        int offsetIndex = 0;
        int[] offsets = {0,1,-1,2,-2,3,-3,4};
        while (offsetIndex < 8 && !rc.canMove(directions[(dirint+offsets[offsetIndex]+8)%8])) {
            offsetIndex++;
        }
        if (offsetIndex < 8) {
            Direction res = directions[(dirint+offsets[offsetIndex]+8)%8];
            rc.move(res);
            return res;
        }
        return null;
    }

    // This method will attempt to move in Direction d (or as close to it as possible)
    static Direction tryMove(int dirint) throws GameActionException {
        if (!rc.isCoreReady())
            return null;
        int offsetIndex = 0;
        int[] offsets = {0,1,-1,2,-2};
        while (offsetIndex < 5 && !rc.canMove(directions[(dirint+offsets[offsetIndex]+8)%8])) {
            offsetIndex++;
        }
        if (offsetIndex < 5) {
            Direction res = directions[(dirint+offsets[offsetIndex]+8)%8];
            rc.move(res);
            return res;
        }
        return null;
    }
    
    // This method will attempt to spawn in the given direction (or as close to it as possible)
    static void trySpawn(RobotType type) throws GameActionException {
        if (!rc.isCoreReady())
            return;
        int i = 0;
        while (i < 8 && !rc.canSpawn(directions[i], type))
            i++;
        if (i < 8) {
            rc.spawn(directions[i], type);
        }
    }
    
    static void tryBuild(RobotType type, int withinHQradius2) throws Exception {
        if (!rc.isCoreReady() || teamOre < type.oreCost)
            return;
        int i = 0;
        while (i < 8 && (!rc.canMove(directions[i]) || myLoc.add(directions[i]).distanceSquaredTo(myHQ) > withinHQradius2))
            i++;
        if (i < 8) {
            rc.build(directions[i], type);
        }
    }
    
    // This method will attempt to build in the given direction (or as close to it as possible)
    static void tryBuild(RobotType type) throws GameActionException {
        if (!rc.isCoreReady() || teamOre < type.oreCost)
            return;
        int i = 0;
        while (i < 8 && !rc.canMove(directions[i]))
            i++;
        if (i < 8) {
            rc.build(directions[i], type);
        }
    }
    
    static void tryLaunch(int dirint) throws GameActionException {
        if (!rc.isCoreReady())
            return;
        int offsetIndex = 0;
        int[] offsets = {0,1,-1,2,-2};
        while (offsetIndex < 5 && !rc.canLaunch(directions[(dirint+offsets[offsetIndex]+8)%8])) {
            offsetIndex++;
        }
        if (offsetIndex < 5) {
            rc.launchMissile(directions[(dirint+offsets[offsetIndex]+8)%8]);
        }
    }

    static int directionToInt(Direction d) {
        switch(d) {
            case NORTH:
                return 0;
            case NORTH_EAST:
                return 1;
            case EAST:
                return 2;
            case SOUTH_EAST:
                return 3;
            case SOUTH:
                return 4;
            case SOUTH_WEST:
                return 5;
            case WEST:
                return 6;
            case NORTH_WEST:
                return 7;
            default:
                return -1;
        }
    }
}
