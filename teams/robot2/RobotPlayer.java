package robot2;

import battlecode.common.*;
import java.util.*;

public class RobotPlayer {
    static final Direction[] directions = {Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST, Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST};
    static final RobotType[] BROADCAST_TYPES = {RobotType.BEAVER, RobotType.MINER, RobotType.MINERFACTORY, RobotType.HELIPAD, RobotType.SUPPLYDEPOT, RobotType.AEROSPACELAB, RobotType.LAUNCHER};
    static final int MAX_MINERS = 40;
    static final int I_BEAVER = RobotType.BEAVER.ordinal();
    static final int I_MINER = RobotType.MINER.ordinal();
    static final int I_MINERFACTORY = RobotType.MINERFACTORY.ordinal();
    static final int I_HELIPAD = RobotType.HELIPAD.ordinal();
    static final int I_LAB = RobotType.AEROSPACELAB.ordinal();
    static final int I_DEPOT = RobotType.SUPPLYDEPOT.ordinal();
    static final int RUSH_TIME = 1000;
    static final int HQ_TO_BEAVER_ST = 1000;
    static final int HQ_TO_MINERFACTORY_ST = 9000;
    static final int HQ_TO_LAB_ST = 9000;
    static final int MINERFACTORY_TO_MINER_ST = 3000;
    static final int LAB_TO_LAUNCHER_ST = 3000;
    
    // Heuristic to see if we need more miners.
    // If during the last ORE_WINDOW turns, we've gathered more than
    // MINE_SATURATION ore per turn on average, then produce more miners.
    // Unless we have more than ORE_SATURATION ore in the bank, then DON'T PRODUCE more miners!
    static final int ORE_WINDOW = 10;
    static final double MINE_SATURATION = 0.7;
    static final int ORE_SATURATION = 700;
    
    // Build orders/compositions
    static final int[] START_ORDER = new int[RobotType.values().length];
    static final int[] COMP_RATIO = new int[RobotType.values().length];
    static {
        START_ORDER[RobotType.BEAVER.ordinal()] = 3; 
        START_ORDER[RobotType.MINERFACTORY.ordinal()] = 2; 
        START_ORDER[RobotType.HELIPAD.ordinal()] = 1; 
        START_ORDER[RobotType.AEROSPACELAB.ordinal()] = 4; 

        COMP_RATIO[RobotType.MINER.ordinal()] = 2; 
        COMP_RATIO[RobotType.AEROSPACELAB.ordinal()] = 1; 
        COMP_RATIO[RobotType.SUPPLYDEPOT.ordinal()] = 2; 
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
    static MapLocation frontier;
    
    // Round constants
    static int roundNum;
    static MapLocation myLoc;
    static double mySupply;
    static double teamOre;
    
    // Other
    static Direction lastMoveDir;
    static double prevTeamOre;
    static double[] prevOreGained = new double[ORE_WINDOW];
    
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
            frontier = myHQ.add(myHQ.directionTo(enemyHQ), 8);
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
            if (r.type == RobotType.BEAVER && r.supplyLevel < HQ_TO_BEAVER_ST && mySupply >= HQ_TO_BEAVER_ST) {
                rc.transferSupplies(HQ_TO_BEAVER_ST, r.location);
                mySupply -= HQ_TO_BEAVER_ST;
            } else if (r.type == RobotType.MINERFACTORY && r.supplyLevel < HQ_TO_MINERFACTORY_ST && mySupply >= HQ_TO_MINERFACTORY_ST) {
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
        int numMiners = rc.readBroadcast(POP_COUNT + I_MINER);
        int numMinerFactories = rc.readBroadcast(POP_COUNT + I_MINERFACTORY);
        int numHelipads = rc.readBroadcast(POP_COUNT + I_HELIPAD);
        int numLabs = rc.readBroadcast(POP_COUNT + I_LAB);
        int numDepots = rc.readBroadcast(POP_COUNT + I_DEPOT);
        if (numMinerFactories < START_ORDER[I_MINERFACTORY])
            tryBuild(RobotType.MINERFACTORY, GameConstants.SUPPLY_TRANSFER_RADIUS_SQUARED);
        else if (numHelipads < START_ORDER[I_HELIPAD])
            tryBuild(RobotType.HELIPAD, GameConstants.SUPPLY_TRANSFER_RADIUS_SQUARED);
        else if (numDepots < numLabs * COMP_RATIO[I_DEPOT] / COMP_RATIO[I_LAB])
            tryBuild(RobotType.SUPPLYDEPOT);
        else if (numLabs < START_ORDER[I_LAB] || numLabs < numMiners * COMP_RATIO[I_LAB] / COMP_RATIO[I_MINER])
            tryBuild(RobotType.AEROSPACELAB, GameConstants.SUPPLY_TRANSFER_RADIUS_SQUARED);

        if (rc.senseOre(myLoc) > 0 && rc.isCoreReady() && rc.canMine())
            rc.mine();

        if (myLoc.distanceSquaredTo(myHQ) >= 80)
            tryWander(directionToInt(myLoc.directionTo(myHQ)));
        else if (roundNum < 100 || roundNum % 10 == 0)
            tryRandomMove();
    }

    static void runMiner() throws Exception {
        if (!rc.isCoreReady())
            return;
        
        RobotInfo[] enemies = rc.senseNearbyRobots(myType.sensorRadiusSquared, enemyTeam);
        for (RobotInfo r : enemies)
            if (r.type.canAttack() && r.type != RobotType.BEAVER && r.type != RobotType.MINER) {
                lastMoveDir = null;
                tryMove(directionToInt(myLoc.directionTo(enemies[0].location).opposite()));
                return;
            }
        
        if (rc.senseOre(myLoc) > 0 && rc.canMine()) {
            rc.mine();
            return;
        }
        
        for (Direction dir : directions) {
            MapLocation loc = myLoc.add(dir);
            if (rc.senseOre(loc) > 0 && !isBadSpot(loc) && rc.canMove(dir)) {
                lastMoveDir = dir;
                rc.move(dir);
                return;
            }
        }
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
        
        if (teamOre < ORE_SATURATION) {
            int oreGained = 0, turnsOreGained = 0;
            for (int i = 0; i < ORE_WINDOW; i++)
                if (prevOreGained[i] > 0) {
                    oreGained += prevOreGained[i];
                    turnsOreGained++;
                }
            int numMiners = rc.readBroadcast(POP_COUNT + I_MINER);
            if (oreGained >= numMiners * MINE_SATURATION * turnsOreGained)
                trySpawn(RobotType.MINER);
        }
        prevOreGained[roundNum % ORE_WINDOW] = (teamOre > prevTeamOre ? teamOre - prevTeamOre : 0);
        prevTeamOre = teamOre;
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
                tryMove(directionToInt(myLoc.directionTo(frontier)));
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
        } else {
            tryWander(directionToInt(myLoc.directionTo(myHQ)));
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
    
    static boolean[][] badSpot = new boolean[GameConstants.MAP_MAX_HEIGHT * 2][GameConstants.MAP_MAX_WIDTH * 2];
    static boolean[][] badSpotUsed = new boolean[GameConstants.MAP_MAX_HEIGHT * 2][GameConstants.MAP_MAX_WIDTH * 2];
    static boolean isBadSpot(MapLocation target) throws Exception {
        int diffY = target.y - myHQ.y + GameConstants.MAP_MAX_HEIGHT;
        int diffX = target.x - myHQ.x + GameConstants.MAP_MAX_WIDTH;
        if (!badSpotUsed[diffY][diffX]) {
            if (enemyHQ.distanceSquaredTo(target) <= 35)
                badSpot[diffY][diffX] = true;
            else for (MapLocation loc : rc.senseEnemyTowerLocations())
                if (loc.distanceSquaredTo(target) <= 24)
                    badSpot[diffY][diffX] = true;
            badSpotUsed[diffY][diffX] = true;
        }
        return badSpot[diffY][diffX];
    }
    
    static double mine(double current) {
        return Math.min(3, current / 4);
    }
}
