package bugger;

import battlecode.common.*;
import java.util.*;

public class RobotPlayer
{
    static final Direction[] directions =
    { Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST, Direction.SOUTH,
            Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST };
    static final RobotType[] BROADCAST_TYPES =
    { RobotType.BEAVER, RobotType.MINER, RobotType.MINERFACTORY, RobotType.HELIPAD, RobotType.SUPPLYDEPOT,
            RobotType.AEROSPACELAB, RobotType.LAUNCHER };
    static final boolean[] IS_MAIN_BUILDING = new boolean[RobotType.values().length];
    static final int[] BASE_SUPPLY = new int[RobotType.values().length];
    static final int BEAVER_SUPPLY = 1000;
    static final int BUILDING_SUPPLY = 15000;
    static
    {
        IS_MAIN_BUILDING[RobotType.HQ.ordinal()] = true;
        IS_MAIN_BUILDING[RobotType.MINERFACTORY.ordinal()] = true;
        IS_MAIN_BUILDING[RobotType.HELIPAD.ordinal()] = true;
        IS_MAIN_BUILDING[RobotType.AEROSPACELAB.ordinal()] = true;

        BASE_SUPPLY[RobotType.BEAVER.ordinal()] = BEAVER_SUPPLY;
        BASE_SUPPLY[RobotType.MINER.ordinal()] = 5000;
        BASE_SUPPLY[RobotType.LAUNCHER.ordinal()] = 2500;
    }
    static final int MAX_MINERS = 40;
    static final int I_BEAVER = RobotType.BEAVER.ordinal();
    static final int I_MINER = RobotType.MINER.ordinal();
    static final int I_MINERFACTORY = RobotType.MINERFACTORY.ordinal();
    static final int I_HELIPAD = RobotType.HELIPAD.ordinal();
    static final int I_LAB = RobotType.AEROSPACELAB.ordinal();
    static final int I_DEPOT = RobotType.SUPPLYDEPOT.ordinal();
    static final int RUSH_TIME = 1000;
    static final int MIN_ORE = 9;
    static final int LAUNCHER_RANGE = 36;

    // Heuristic to see if we need more miners.
    // If during the last ORE_WINDOW turns, we've gathered more than
    // MINE_SATURATION ore per turn on average, then produce more miners.
    // Unless we have more than ORE_SATURATION ore in the bank, then DON'T
    // PRODUCE more miners!
    static final int ORE_WINDOW = 10;
    static final double MINE_SATURATION = 0.7;
    static final int ORE_SATURATION = 700;

    // Build orders/compositions
    static final int[] START_ORDER = new int[RobotType.values().length];
    static final int[] COMP_RATIO = new int[RobotType.values().length];
    static
    {
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
    static final int POP_COUNT = 1;  // to 31

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
    static MapLocation[] enemyTowers;

    // Other
    static Direction lastMoveDir;
    static double prevTeamOre;
    static double[] prevOreGained = new double[ORE_WINDOW];

    public static void run(RobotController tomatojuice)
    {
        rc = tomatojuice;
        myTeam = rc.getTeam();
        enemyTeam = myTeam.opponent();
        myType = rc.getType();
        mySensors = myType.sensorRadiusSquared;
        if (myType == RobotType.MISSILE)
        {
            while (true)
            {
                try
                {
                    roundNum = Clock.getRoundNum();
                    myLoc = rc.getLocation();

                    runMissile();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }

                if (Clock.getRoundNum() != roundNum)
                    System.out.println("MISSILE USED TOO MUCH BYTECODE");
                rc.yield();
            }
        }

        try
        {
            myRange = myType.attackRadiusSquared;
            producedRound = rc.readBroadcast(ROUND_NUM);
            myHQ = rc.senseHQLocation();
            enemyHQ = rc.senseEnemyHQLocation();
            frontier = myHQ.add(myHQ.directionTo(enemyHQ), 8);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        while (true)
        {
            try
            {
                roundNum = Clock.getRoundNum();
                myLoc = rc.getLocation();
                mySupply = rc.getSupplyLevel();
                teamOre = rc.getTeamOre();
                enemyTowers = rc.senseEnemyTowerLocations();

                if (myType == RobotType.HQ)
                {
                    int[] popCounts = new int[RobotType.values().length];
                    for (RobotInfo r : rc.senseNearbyRobots(999999, myTeam))
                    {
                        RobotType type = r.type;
                        popCounts[type.ordinal()]++;
                    }
                    for (RobotType type : BROADCAST_TYPES)
                        rc.broadcast(POP_COUNT + type.ordinal(), popCounts[type.ordinal()]);

                    for (RobotInfo r : rc.senseNearbyRobots(GameConstants.SUPPLY_TRANSFER_RADIUS_SQUARED, myTeam))
                        if (IS_MAIN_BUILDING[r.type.ordinal()] && r.supplyLevel < BUILDING_SUPPLY
                                && mySupply >= BUILDING_SUPPLY) {
                            rc.transferSupplies(BUILDING_SUPPLY, r.location);
                            mySupply -= BUILDING_SUPPLY;
                        } else if (r.type == RobotType.BEAVER && r.supplyLevel < 0.5 * BEAVER_SUPPLY && mySupply >= BEAVER_SUPPLY) {
                            rc.transferSupplies(BEAVER_SUPPLY, r.location);
                            mySupply -= BEAVER_SUPPLY;
                        }
                }
                else
                {
                    if (myType.isBuilding) {
                        for (RobotInfo r : rc.senseNearbyRobots(GameConstants.SUPPLY_TRANSFER_RADIUS_SQUARED, myTeam)) {
                            if (!r.type.isBuilding) {
                                int goal = BASE_SUPPLY[r.type.ordinal()];
                                if (r.supplyLevel < 0.5 * goal && mySupply >= goal) {
                                    rc.transferSupplies(goal, r.location);
                                    break;
                                }
                            } else if (IS_MAIN_BUILDING[r.type.ordinal()] && r.supplyLevel < BUILDING_SUPPLY
                                    && mySupply >= BUILDING_SUPPLY) {
                                Direction dir1 = myHQ.directionTo(myLoc), dir2 = myLoc.directionTo(r.location);
                                if (dir1 == dir2.rotateLeft() || dir1 == dir2 || dir1 == dir2.rotateRight()) {
                                    rc.transferSupplies(BUILDING_SUPPLY, r.location);
                                    break;
                                }
                            }
                        }
                    }
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
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }

            if (Clock.getRoundNum() != roundNum)
                System.out.println("USED TOO MUCH BYTECODE");
            rc.yield();
        }
    }

    static void runHQ() throws Exception
    {
        int numBeavers = rc.readBroadcast(POP_COUNT + I_BEAVER);
        if (numBeavers < START_ORDER[I_BEAVER])
            trySpawn(RobotType.BEAVER);
        attackSomething();
    }

    static void runTower() throws Exception
    {
        attackSomething();
    }

    static void runBeaver() throws Exception
    {
        int numMiners = rc.readBroadcast(POP_COUNT + I_MINER);
        int numMinerFactories = rc.readBroadcast(POP_COUNT + I_MINERFACTORY);
        int numHelipads = rc.readBroadcast(POP_COUNT + I_HELIPAD);
        int numLabs = rc.readBroadcast(POP_COUNT + I_LAB);
        int numDepots = rc.readBroadcast(POP_COUNT + I_DEPOT);
        if (numMinerFactories < START_ORDER[I_MINERFACTORY])
            tryBuild(RobotType.MINERFACTORY);
        else if (numHelipads < START_ORDER[I_HELIPAD])
            tryBuild(RobotType.HELIPAD);
        else if (numDepots < numLabs * COMP_RATIO[I_DEPOT] / COMP_RATIO[I_LAB])
            tryBuild(RobotType.SUPPLYDEPOT);
        else if (numLabs < START_ORDER[I_LAB] || numLabs < numMiners * COMP_RATIO[I_LAB] / COMP_RATIO[I_MINER] || teamOre > ORE_SATURATION)
            tryBuild(RobotType.AEROSPACELAB);

        if (rc.senseOre(myLoc) > 0 && rc.isCoreReady() && rc.canMine())
            rc.mine();

        if (myLoc.distanceSquaredTo(myHQ) >= 80)
            tryWander(directionToInt(myLoc.directionTo(myHQ)));
        else if (roundNum < 100 || roundNum % 10 == 0)
            tryRandomMove();
    }

    static void runMiner() throws Exception
    {
        if (!rc.isCoreReady())
            return;

        RobotInfo[] enemies = rc.senseNearbyRobots(35, enemyTeam);
        for (RobotInfo r : enemies)
            if (r.type.canAttack() && r.type != RobotType.BEAVER && r.type != RobotType.MINER)
            {
                lastMoveDir = enemies[0].location.directionTo(myLoc);
                tryMove(directionToInt(lastMoveDir));
                return;
            }

        double currOre = rc.senseOre(myLoc);
        if (currOre > MIN_ORE)
        {
            rc.mine();
            return;
        }

        for (Direction dir : directions)
            if (rc.senseOre(myLoc.add(dir)) > 2 * currOre && !isBadDir(dir) && rc.canMove(dir))
            {
                lastMoveDir = dir;
                rc.move(dir);
                return;
            }

        if (currOre > 0)
        {
            rc.mine();
            return;
        }

        if (lastMoveDir == null)
            lastMoveDir = tryRandomMove();
        else
        {
            Direction dir = tryWander(directionToInt(lastMoveDir));
            if (dir != null)
                lastMoveDir = dir;
        }
    }

    static void runMinerFactory() throws Exception
    {
        if (teamOre < ORE_SATURATION)
        {
            int oreGained = 0, turnsOreGained = 0;
            for (int i = 0; i < ORE_WINDOW; i++)
                if (prevOreGained[i] > 0)
                {
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

    static void runLab() throws Exception
    {
        trySpawn(RobotType.LAUNCHER);
    }

    static void runLauncher() throws Exception
    {
        RobotInfo[] enemies = rc.senseNearbyRobots(LAUNCHER_RANGE, enemyTeam);
        if (enemies.length > 0)
        {
            tryLaunch(directionToInt(myLoc.directionTo(enemies[0].location)));
            return;
        }
        
        for (MapLocation enemyTower : enemyTowers)
            if (myLoc.distanceSquaredTo(enemyTower) < LAUNCHER_RANGE)
            {
                tryLaunch(directionToInt(myLoc.directionTo(enemyTower)));
                return;
            }
                
        if (rc.isCoreReady())
        {
            if (roundNum < RUSH_TIME)
                Bugger.set(frontier);
            else
                Bugger.set(enemyHQ);
            Direction dir = Bugger.getDir();
            if (dir != null)
                rc.move(dir);
        }
    }

    static void runMissile() throws Exception
    {
        RobotInfo[] enemies = rc.senseNearbyRobots(mySensors, enemyTeam);
        if (enemies.length != 0)
        {
            tryMissileMove(myLoc.directionTo(enemies[0].location));
            return;
        }
        RobotInfo[] allies = rc.senseNearbyRobots(mySensors, myTeam);
        if (allies.length != 0)
        {
            tryMissileMove(allies[0].location.directionTo(myLoc));
            return;
        }
    }

    // This method will attack an enemy in sight, if there is one
    static void attackSomething() throws GameActionException
    {
        if (!rc.isWeaponReady())
            return;
        RobotInfo[] enemies = rc.senseNearbyRobots(myRange, enemyTeam);
        if (enemies.length > 0)
        {
            rc.attackLocation(enemies[0].location);
        }
    }

    static Direction tryRandomMove() throws Exception
    {
        return tryWander((int) (Math.random() * 8));
    }

    static Direction tryWander(int dirint) throws Exception
    {
        if (!rc.isCoreReady())
            return null;
        int offsetIndex = 0;
        int[] offsets =
        { 0, 1, -1, 2, -2, 3, -3, 4 };
        while (offsetIndex < 8) {
            Direction dir = directions[(dirint + offsets[offsetIndex] + 8) % 8];
            if (rc.canMove(dir) && !isBadDir(dir))
                break;
            offsetIndex++;
        }
        if (offsetIndex < 8)
        {
            Direction res = directions[(dirint + offsets[offsetIndex] + 8) % 8];
            rc.move(res);
            return res;
        }
        return null;
    }

    // This method will attempt to move in Direction d (or as close to it as
    // possible)
    static Direction tryMove(int dirint) throws Exception
    {
        if (!rc.isCoreReady())
            return null;
        int offsetIndex = 0;
        int[] offsets =
        { 0, 1, -1, 2, -2 };
        while (offsetIndex < 5) {
            Direction dir = directions[(dirint + offsets[offsetIndex] + 8) % 8];
            if (rc.canMove(dir) && (myType == RobotType.MISSILE || !isBadDir(dir)))
                break;
            offsetIndex++;
        }
        if (offsetIndex < 5)
        {
            Direction res = directions[(dirint + offsets[offsetIndex] + 8) % 8];
            rc.move(res);
            return res;
        }
        return null;
    }
    
    // Optimized for missiles
    static void tryMissileMove(Direction dir) throws Exception
    {
        if (!rc.isCoreReady())
            return;
        Direction left, right, left2, right2;
        if (rc.canMove(dir))
            rc.move(dir);
        else if (rc.canMove(left = dir.rotateLeft()))
            rc.move(left);
        else if (rc.canMove(right = dir.rotateRight()))
            rc.move(right);
        else if (rc.canMove(left2 = left.rotateLeft()))
            rc.move(left2);
        else if (rc.canMove(right2 = right.rotateRight()))
            rc.move(right2);
    }

    // This method will attempt to spawn in the given direction (or as close to
    // it as possible)
    static void trySpawn(RobotType type) throws GameActionException
    {
        if (!rc.isCoreReady())
            return;
        int i = 0;
        while (i < 8 && !rc.canSpawn(directions[i], type))
            i++;
        if (i < 8)
        {
            rc.spawn(directions[i], type);
        }
    }

    static void tryBuild(RobotType type) throws Exception
    {
        if (!rc.isCoreReady() || teamOre < type.oreCost)
            return;
        int i = 0;
        while (i < 8 && (!rc.canMove(directions[i]) || !tryBuildHere(type, myLoc.add(directions[i]))))
            i++;
        if (i < 8)
        {
            rc.build(directions[i], type);
        }
    }

    static boolean tryBuildHere(RobotType type, MapLocation loc)
    {
        for (RobotInfo r : rc.senseNearbyRobots(loc, 1, myTeam))
            if (r.type.isBuilding)
                return false;
        for (RobotInfo r : rc.senseNearbyRobots(loc, 13, myTeam))
            if (IS_MAIN_BUILDING[r.type.ordinal()])
                return true;
        return false;
    }

    static void tryLaunch(int dirint) throws GameActionException
    {
        if (!rc.isCoreReady())
            return;
        int offsetIndex = 0;
        int[] offsets =
        { 0, 1, -1, 2, -2 };
        while (offsetIndex < 5 && !rc.canLaunch(directions[(dirint + offsets[offsetIndex] + 8) % 8]))
        {
            offsetIndex++;
        }
        if (offsetIndex < 5)
        {
            rc.launchMissile(directions[(dirint + offsets[offsetIndex] + 8) % 8]);
        }
    }

    static int directionToInt(Direction d)
    {
        switch (d)
        {
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

    static boolean isBadDir(Direction dir) throws Exception
    {
        MapLocation target = myLoc.add(dir);
        if (enemyHQ.distanceSquaredTo(target) <= 35)
            return true;

        for (MapLocation loc : enemyTowers)
            if (loc.distanceSquaredTo(target) <= 24)
                return true;

        return false;
    }

    static double mine(double current)
    {
        return Math.min(3, current / 4);
    }

    static class Bugger {
        static final int FORWARD = 0;
        static final int BUG_LEFT = 1;  // follow left wall
        static final int BUG_RIGHT = 2;
    
        static MapLocation dest;
        static int state;
        static int lastBugState;
        
        // Only valid when bugging
        static Direction bugDir;
        static int bugDirIndex;
        static int hitWallDirIndex;
        static MapLocation hitWallLoc;
        
        static void set(MapLocation dest) {
            if (!dest.equals(Bugger.dest))
            {
                Bugger.dest = dest;
                state = FORWARD;
                lastBugState = BUG_LEFT;
            }
        }
        
        static Direction getDir() throws Exception {
            // Update state
            Direction dir = myLoc.directionTo(dest);
            if (state == FORWARD) {
                if (!rc.canMove(dir)) {
                    state = lastBugState == BUG_LEFT ? BUG_RIGHT : BUG_LEFT;
                    bugDir = dir;
                    bugDirIndex = hitWallDirIndex = directionToInt(dir);
                    hitWallLoc = myLoc;
                }
            } else {
                if (rc.canMove(dir) && dir != bugDir.opposite()) {
                    int newBugDirIndex = align(bugDirIndex, directionToInt(dir));
                    if (state == BUG_LEFT && newBugDirIndex <= hitWallDirIndex + 4 ||
                            state == BUG_RIGHT && newBugDirIndex >= hitWallDirIndex - 4) {
                        lastBugState = state;
                        state = FORWARD;
                    }
                }
            }

            // Move
            if (state == FORWARD) {
                return dir;
            } else {
                int candDirIndex = bugDirIndex;
                Direction candDir = bugDir;
                if (state == BUG_LEFT) {
                    candDirIndex -= 2;
                    candDir = candDir.rotateLeft().rotateLeft();
                } else if (state == BUG_RIGHT) {
                    candDirIndex += 2;
                    candDir = candDir.rotateRight().rotateRight();
                }
                Direction startDir = candDir;
                do {
                    if (rc.canMove(candDir)) {
                        bugDir = candDir;
                        bugDirIndex = candDirIndex;
                        return candDir;
                    }
                    if (state == BUG_LEFT) {
                        candDirIndex++;
                        candDir = candDir.rotateRight();
                    } else if (state == BUG_RIGHT) {
                        candDirIndex--;
                        candDir = candDir.rotateLeft();
                    }
                } while (candDir != startDir);
                return null;
            }
        }
        
        // Returns whether target is on the left or right side of the ray loc1->loc2.
        static int getSide(MapLocation loc1, MapLocation loc2, MapLocation target) {
            int cp = (loc2.x - loc1.x) * (target.y - loc1.y) - (loc2.y - loc1.y) * (target.x - loc1.x); 
            if (cp > 0)
                return BUG_LEFT;
            else if (cp < 0)
                return BUG_RIGHT;
            else
                return FORWARD;
        }
        
        // Updates startDir to the nearest direction equal to newDir (mod 8).
        static int align(int startDir, int newDir) {
            int diff = (newDir - startDir) % 8;
            if (diff >= 5)
                return startDir + diff - 8;
            else if (diff <= -5)
                return startDir + diff - 8;
            else
                return startDir + diff;
        }
    }
}
