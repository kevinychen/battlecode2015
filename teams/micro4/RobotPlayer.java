package micro4;

import battlecode.common.*;
import java.util.*;

public class RobotPlayer
{
    static final Direction[] directions =
    { Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST, Direction.SOUTH,
            Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST };
    static final RobotType[] BROADCAST_TYPES =
    { RobotType.BEAVER, RobotType.MINER, RobotType.MINERFACTORY, RobotType.HELIPAD, RobotType.DRONE,
            RobotType.SUPPLYDEPOT, RobotType.AEROSPACELAB, RobotType.LAUNCHER };
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
        BASE_SUPPLY[RobotType.LAUNCHER.ordinal()] = 5000;
        BASE_SUPPLY[RobotType.DRONE.ordinal()] = 2500;
    }
    static final int I_BEAVER = RobotType.BEAVER.ordinal();
    static final int I_MINER = RobotType.MINER.ordinal();
    static final int I_MINERFACTORY = RobotType.MINERFACTORY.ordinal();
    static final int I_DRONE = RobotType.DRONE.ordinal();
    static final int I_HELIPAD = RobotType.HELIPAD.ordinal();
    static final int I_LAB = RobotType.AEROSPACELAB.ordinal();
    static final int I_DEPOT = RobotType.SUPPLYDEPOT.ordinal();
    static final int RUSH_TIME = 1000;
    static final int MIN_ORE = 9;
    static final int LAUNCHER_RANGE = 36;
    static final Clause SAFE_CLAUSE = new Clause()
    {
        public boolean isValid(Direction dir)
        {
            MapLocation target = myLoc.add(dir);
            if (enemyHQ.distanceSquaredTo(target) <= GameConstants.HQ_BUFFED_ATTACK_RADIUS_SQUARED)
                return false;

            for (MapLocation loc : enemyTowers)
                if (loc.distanceSquaredTo(target) <= RobotType.TOWER.attackRadiusSquared)
                    return false;

            return true;
        }
    };
    static final Clause DEFENSE_CLAUSE = new Clause()
    {
        public boolean isValid(Direction dir)
        {
            if (!SAFE_CLAUSE.isValid(dir))
                return false;
            int myDist = myLoc.distanceSquaredTo(myHQ);
            if (myDist > 36)
                return true;
            MapLocation goal = myLoc.add(dir);
            return goal.distanceSquaredTo(myHQ) <= myDist;
        }
    };
    
    // Message channels
    static final int ROUND_NUM = 0;
    static final int POP_COUNT = 1;  // to 31
    static final int PATROL_DRONES = 32;  // to 39
    static final int ENEMY_ARMY_LOC = 40;
    static final int ENEMY_ECON_LOC = 41;
    static final int DEFEND_CALL = 42;
    static final int MISSILE_TARGETS = 32768;  // to 65536
    static final int NUM_MISSILE_TARGET_CHANNELS = 16384;

    // Game constants
    static RobotController rc;
    static int myID;
    static Team myTeam;
    static Team enemyTeam;
    static RobotType myType;
    static int mySensors;
    static int myRange;
    static int producedRound;
    static MapLocation myHQ;
    static MapLocation enemyHQ;
    static MapLocation frontier;
    static Random random;

    // Round constants
    static int roundNum;
    static MapLocation myLoc;
    static double mySupply;
    static double teamOre;
    static MapLocation[] enemyTowers;

    // Other
    static Direction lastMoveDir;
    static MapLocation prevEnemyArmyLoc;
    static MapLocation prevEnemyEconLoc;

    public static void run(RobotController tomatojuice)
    {
        rc = tomatojuice;
        myID = rc.getID();
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
            random = new Random(rc.getID());
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
                    
                    find_enemy_army:
                    {
                        if (prevEnemyArmyLoc != null)
                        {
                            for (RobotInfo r : rc.senseNearbyRobots(prevEnemyArmyLoc, 36, enemyTeam))
                                if (r.type != RobotType.BEAVER && r.type != RobotType.MINER)
                                {
                                    rc.broadcast(ENEMY_ARMY_LOC, mapLocationToInt(r.location));
                                    prevEnemyArmyLoc = r.location;
                                    break find_enemy_army;
                                }
                        }
                        for (RobotInfo r : rc.senseNearbyRobots(999999, enemyTeam))
                            if (r.type != RobotType.BEAVER && r.type != RobotType.MINER)
                            {
                                rc.broadcast(ENEMY_ARMY_LOC, mapLocationToInt(r.location));
                                prevEnemyArmyLoc = r.location;
                                break find_enemy_army;
                            }
                        rc.broadcast(ENEMY_ARMY_LOC, 0);
                    }
                    find_enemy_econ:
                    {
                        if (prevEnemyEconLoc != null)
                        {
                            for (RobotInfo r : rc.senseNearbyRobots(prevEnemyEconLoc, 36, enemyTeam))
                                if (r.type == RobotType.BEAVER || r.type == RobotType.MINER)
                                {
                                    rc.broadcast(ENEMY_ECON_LOC, mapLocationToInt(r.location));
                                    prevEnemyEconLoc = r.location;
                                    break find_enemy_econ;
                                }
                        }
                        for (RobotInfo r : rc.senseNearbyRobots(999999, enemyTeam))
                            if (r.type == RobotType.BEAVER || r.type == RobotType.MINER)
                            {
                                rc.broadcast(ENEMY_ECON_LOC, mapLocationToInt(r.location));
                                prevEnemyEconLoc = r.location;
                                break find_enemy_econ;
                            }
                        rc.broadcast(ENEMY_ECON_LOC, 0);
                    }

                    RobotInfo[] homeEnemies = rc.senseNearbyRobots(myLoc, 100, enemyTeam);
                    if (homeEnemies.length > 0)
                        rc.broadcast(DEFEND_CALL, mapLocationToInt(homeEnemies[0].location));
                    else
                        rc.broadcast(DEFEND_CALL, 0);

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
                else if (myType == RobotType.HELIPAD)
                    runHelipad();
                else if (myType == RobotType.DRONE)
                    runDrone();
                else if (myType == RobotType.AEROSPACELAB)
                    runLab();
                else if (myType == RobotType.LAUNCHER)
                    runLauncher();
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
        final int MAX_BEAVERS = roundNum < 300 ? 1 : 2;
        if (numBeavers < MAX_BEAVERS)
            trySpawn(RobotType.BEAVER);
        attack();
    }

    static void runTower() throws Exception
    {
        attack();
    }

    static void runBeaver() throws Exception
    {
        build_stuff:
        {
            int numMinerFactories = rc.readBroadcast(POP_COUNT + I_MINERFACTORY);
            final int MAX_MINER_FACTORIES = 1;
            if (numMinerFactories < MAX_MINER_FACTORIES)
            {
                tryBuild(RobotType.MINERFACTORY);
                break build_stuff;
            }

            int numHelipads = rc.readBroadcast(POP_COUNT + I_HELIPAD);
            final int MAX_HELIPADS = 2;
            if (numHelipads < MAX_HELIPADS)
            {
                tryBuild(RobotType.HELIPAD);
                break build_stuff;
            }

            int numLabs = rc.readBroadcast(POP_COUNT + I_LAB);
            int numDepots = rc.readBroadcast(POP_COUNT + I_DEPOT);
            if (numDepots < numLabs - 2)
            {
                tryBuild(RobotType.SUPPLYDEPOT);
                break build_stuff;
            }

            tryBuild(RobotType.AEROSPACELAB);
        }

        final int HQ_DIST = roundNum < 500 ? 15 : (roundNum < 1000 ? 35 : 80);
        if (myLoc.distanceSquaredTo(myHQ) >= HQ_DIST)
            tryWander(directionToInt(myLoc.directionTo(myHQ)));
        else if (roundNum > 50)
            tryRandomMove();
    }

    static void runMiner() throws Exception
    {
        if (!rc.isCoreReady())
            return;

        RobotInfo[] enemies = rc.senseNearbyRobots(35, enemyTeam);
        for (RobotInfo r : enemies)
        {
            if ((r.type == RobotType.BEAVER || r.type == RobotType.MINER || r.type == RobotType.DRONE) &&
                    myLoc.distanceSquaredTo(r.location) <= myRange && rc.isWeaponReady())
            {
                rc.attackLocation(r.location);
                return;
            }
            if (r.type.canAttack() && r.type != RobotType.BEAVER && r.type != RobotType.MINER)
            {
                lastMoveDir = enemies[0].location.directionTo(myLoc);
                tryMove(directionToInt(lastMoveDir));
                return;
            }
        }

        double currOre = rc.senseOre(myLoc);
        if (currOre > MIN_ORE)
        {
            rc.mine();
            return;
        }

        int offset = rc.getID() % 8;
        for (int i = 0; i < 8; i++)
        {
            Direction dir = directions[(offset + i) % 8];
            if (dir.opposite() != lastMoveDir && rc.senseOre(myLoc.add(dir)) > 2 * currOre && !isBadDir(dir) && rc.canMove(dir))
            {
                lastMoveDir = dir;
                rc.move(dir);
                return;
            }
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
        int numMiners = rc.readBroadcast(POP_COUNT + I_MINER);
        final int MAX_MINERS = 30;
        if (numMiners < MAX_MINERS)
            trySpawn(RobotType.MINER);
    }

    static void runHelipad() throws Exception
    {
        int numDrones = rc.readBroadcast(POP_COUNT + I_DRONE);
        final int MAX_DRONES = 8;
        if (roundNum < RUSH_TIME && numDrones < MAX_DRONES)
            trySpawn(RobotType.DRONE);
    }
    
    static double supplyRefuel = -1;
    static void runDrone() throws Exception
    {
        attack();
        
        if (!rc.isCoreReady())
            return;
        int[] scores = new int[16];
        RobotInfo[] enemies = rc.senseNearbyRobots(myType.sensorRadiusSquared, enemyTeam);
        boolean seeMissile = false;
        for (RobotInfo r : enemies)
        {
            int dist = myLoc.distanceSquaredTo(r.location);
            Direction dir = myLoc.directionTo(r.location), opp = dir.opposite();
            if (r.type == RobotType.DRONE || r.type == RobotType.COMMANDER)
            {
                if (dist <= 4)
                    ;
                else if (dist == 5)
                    scores[opp.ordinal()] += r.type.attackPower;
                else if (dist <= 10)
                {
                    scores[opp.ordinal()] += r.type.attackPower;
                    scores[opp.rotateLeft().ordinal()] += r.type.attackPower;
                    scores[opp.rotateRight().ordinal()] += r.type.attackPower;
                }
                else if (dist <= 17)
                {
                    scores[dir.ordinal()] -= r.type.attackPower;
                    scores[dir.rotateLeft().ordinal()] -= r.type.attackPower;
                    scores[dir.rotateRight().ordinal()] -= r.type.attackPower;
                }
                else
                    scores[dir.ordinal()] -= RobotType.DRONE.attackPower;
            }
            else if (r.type == RobotType.MISSILE)
            {
                seeMissile = true;
                int dx = r.location.x - myLoc.x, dy = r.location.y - myLoc.y;
                int adx = Math.abs(dx), ady = Math.abs(dy);
                if (adx == ady)
                {
                    scores[opp.ordinal()] += RobotType.MISSILE.attackPower;
                    scores[opp.rotateLeft().ordinal()] += RobotType.MISSILE.attackPower;
                    scores[opp.rotateRight().ordinal()] += RobotType.MISSILE.attackPower;
                }
                else if (dist <= 13)
                {
                    Direction d;
                    if (adx > ady)
                        d = (dx > 0 ? Direction.WEST : Direction.EAST);
                    else
                        d = (dy > 0 ? Direction.NORTH : Direction.SOUTH);

                    scores[d.ordinal()] += RobotType.MISSILE.attackPower;
                    scores[d.rotateLeft().ordinal()] += RobotType.MISSILE.attackPower - 5;
                    scores[d.rotateRight().ordinal()] += RobotType.MISSILE.attackPower - 5;
                    scores[Direction.NONE.ordinal()]--;
                }
                else
                {
                    Direction d;
                    if (adx > ady)
                        d = (dx > 0 ? Direction.EAST : Direction.WEST);
                    else
                        d = (dy > 0 ? Direction.SOUTH : Direction.NORTH);

                    scores[d.ordinal()] -= RobotType.MISSILE.attackPower;
                    scores[d.rotateLeft().ordinal()] -= RobotType.MISSILE.attackPower;
                    scores[d.rotateRight().ordinal()] -= RobotType.MISSILE.attackPower;
                    scores[Direction.NONE.ordinal()]--;
                }
            }
            else if (r.type == RobotType.LAUNCHER)
            {
                seeMissile = true;
                if (dist <= 16 || dist == 18)
                {
                    scores[opp.ordinal()] += RobotType.MISSILE.attackPower / 2;
                    scores[opp.rotateLeft().ordinal()] += RobotType.MISSILE.attackPower / 2;
                    scores[opp.rotateRight().ordinal()] += RobotType.MISSILE.attackPower / 2;
                }
                else
                {
                    scores[dir.ordinal()] -= RobotType.MISSILE.attackPower / 2;
                    scores[dir.rotateLeft().ordinal()] -= RobotType.MISSILE.attackPower / 2;
                    scores[dir.rotateRight().ordinal()] -= RobotType.MISSILE.attackPower / 2;
                }
            }
            else if (r.type == RobotType.TANK)
            {
                scores[opp.ordinal()] += r.type.attackPower;
                scores[opp.rotateLeft().ordinal()] += r.type.attackPower;
                scores[opp.rotateRight().ordinal()] += r.type.attackPower;
            }
            else
            {
                if (dist <= 8)
                {
                    scores[opp.ordinal()] += r.type.attackPower;
                    scores[opp.rotateLeft().ordinal()] += r.type.attackPower;
                    scores[opp.rotateRight().ordinal()] += r.type.attackPower;
                }
                else if (dist <= 10)
                {
                    scores[dir.ordinal()] -= r.type.attackPower;
                    scores[dir.rotateLeft().ordinal()] -= r.type.attackPower;
                    scores[dir.rotateRight().ordinal()] -= r.type.attackPower;
                }
                else if (dist <= 13)
                {
                    scores[dir.ordinal()] += 5;
                }
                else
                {
                    scores[dir.ordinal()] += 5;
                    scores[dir.rotateLeft().ordinal()] += 5;
                    scores[dir.rotateRight().ordinal()] += 5;
                }
            }
        }
//        rc.setIndicatorString(1, Arrays.toString(Direction.values()));
//        rc.setIndicatorString(2, Arrays.toString(scores));
        
        if (seeMissile && rc.getCoreDelay() > 0.6)
        {
            scores[Direction.NORTH_WEST.ordinal()] -= RobotType.MISSILE.attackPower;
            scores[Direction.NORTH_EAST.ordinal()] -= RobotType.MISSILE.attackPower;
            scores[Direction.SOUTH_WEST.ordinal()] -= RobotType.MISSILE.attackPower;
            scores[Direction.SOUTH_EAST.ordinal()] -= RobotType.MISSILE.attackPower;
        }

        for (Direction dir : directions)
        {
            MapLocation loc = myLoc.add(dir);
            if (enemyHQ.distanceSquaredTo(loc) <= 50)
                scores[dir.ordinal()] -= 500;
            for (MapLocation towerLoc : enemyTowers)
                if (towerLoc.distanceSquaredTo(loc) <= RobotType.TOWER.attackRadiusSquared)
                    scores[dir.ordinal()] -= 500;
        }
            
        int bestScore = scores[Direction.NONE.ordinal()];
        for (Direction dir : directions)
            if (rc.canMove(dir) && scores[dir.ordinal()] > bestScore)
                bestScore = scores[dir.ordinal()];
        
        boolean[] good = new boolean[9];
        for (Direction dir : directions)
            if (rc.canMove(dir) && scores[dir.ordinal()] == bestScore)
                good[dir.ordinal()] = true;
        if (scores[Direction.NONE.ordinal()] == bestScore)
            good[Direction.NONE.ordinal()] = true;
        
        // Low supply, go back to HQ
        if (myLoc.distanceSquaredTo(myHQ) + 500 >= mySupply / myType.supplyUpkeep * mySupply / myType.supplyUpkeep)
        {
            if (supplyRefuel == -1)
            {
                supplyRefuel = mySupply;
                Bugger.set(myHQ);
            }
        }

        if (myHQ.equals(Bugger.dest) && mySupply > supplyRefuel)
        {
            supplyRefuel = -1;
            Bugger.set(null);
        }
        if (Bugger.dest == null)
            Bugger.set(enemyHQ);
        
        Bugger.fun(good);
    }

    static void runLab() throws Exception
    {
        trySpawn(RobotType.LAUNCHER);
    }

    static void runLauncher() throws Exception
    {
        RobotInfo[] enemies = rc.senseNearbyRobots(LAUNCHER_RANGE, enemyTeam);
        for (RobotInfo r : enemies)
            if (r.type != RobotType.MISSILE)
            {
                tryLaunch(directionToInt(myLoc.directionTo(r.location)), r.location, r.ID);
                return;
            }
        
        for (MapLocation enemyTower : enemyTowers)
            if (myLoc.distanceSquaredTo(enemyTower) < LAUNCHER_RANGE)
            {
                tryLaunch(directionToInt(myLoc.directionTo(enemyTower)), enemyTower, -1);
                return;
            }
                
        if (rc.isCoreReady())
        {
            if (roundNum < RUSH_TIME)
                Bugger.set(frontier);
            else
                Bugger.set(enemyHQ);
            
            boolean[] good = new boolean[16];
            for (Direction dir : directions)
                if (rc.canMove(dir))
                    good[dir.ordinal()] = true;
            Bugger.bug(good);
        }
    }

    static int missileTargetID;
    static MapLocation missileTargetLoc;
    static void runMissile() throws Exception
    {
        if (missileTargetID == 0)
        {
            int missileChannel = hashMissile(roundNum, myLoc);
            missileTargetID = rc.readBroadcast(missileChannel);
            missileTargetLoc = intToMapLocation(rc.readBroadcast(missileChannel + 1));
        }
        
        MapLocation targetLoc;
        if (rc.canSenseRobot(missileTargetID))
            targetLoc = rc.senseRobot(missileTargetID).location;
        else
            targetLoc = missileTargetLoc;
        
        if (myLoc.distanceSquaredTo(targetLoc) <= 2)
            rc.explode();
        else
            tryMissileMove(myLoc.directionTo(targetLoc));
    }

    // This method will attack an enemy in sight, if there is one
    static void attack() throws GameActionException
    {
        if (!rc.isWeaponReady())
            return;
        double lowestHits = 999999;
        MapLocation lowestHitsLoc = null;
        for (RobotInfo r : rc.senseNearbyRobots(myRange, enemyTeam))
        {
            double numHits = r.type == RobotType.MISSILE ? r.health : r.health / myType.attackPower;
            if (numHits <= 1)
            {
                rc.attackLocation(r.location);
                return;
            }
            else if (numHits < lowestHits)
            {
                lowestHits = numHits;
                lowestHitsLoc = r.location;
            }
        }
        if (lowestHitsLoc != null)
            rc.attackLocation(lowestHitsLoc);
    }

    static Direction tryRandomMove() throws Exception
    {
        return tryWander(random.nextInt(8));
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
    
    static void tryValidMove(Direction dir, Clause c) throws Exception
    {
        if (!rc.isCoreReady())
            return;
        Direction left, right, left2, right2;
        if (rc.canMove(dir) && c.isValid(dir))
            rc.move(dir);
        else if (rc.canMove(left = dir.rotateLeft()) && c.isValid(left))
            rc.move(left);
        else if (rc.canMove(right = dir.rotateRight()) && c.isValid(right))
            rc.move(right);
        else if (rc.canMove(left2 = left.rotateLeft()) && c.isValid(left2))
            rc.move(left2);
        else if (rc.canMove(right2 = right.rotateRight()) && c.isValid(right2))
            rc.move(right2);
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

    static Direction tryBuild(RobotType type) throws Exception
    {
        if (!rc.isCoreReady() || teamOre < type.oreCost)
            return null;
        int i = 0;
        while (i < 8 && (!rc.canMove(directions[i]) || !tryBuildHere(type, myLoc.add(directions[i]))))
            i++;
        if (i < 8)
        {
            rc.build(directions[i], type);
            return directions[i];
        }
        return null;
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

    static void tryLaunch(int dirint, MapLocation targetLoc, int targetID) throws GameActionException
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
            Direction dir = directions[(dirint + offsets[offsetIndex] + 8) % 8];
            rc.launchMissile(dir);
            
            int missileChannel = hashMissile(roundNum, myLoc.add(dir));
            rc.broadcast(missileChannel, targetID);
            rc.broadcast(missileChannel + 1, mapLocationToInt(targetLoc));
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
    
    static int mapLocationToInt(MapLocation loc)
    {
        return ((loc.x + (1 << 15)) << 16) | (loc.y + (1 << 15));
    }
    
    static MapLocation intToMapLocation(int code)
    {
        return new MapLocation((code >>> 16) - (1 << 15), (code & 0xffff) - (1 << 15));
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
    
    static int hashMissile(int roundNum, MapLocation mapLoc)
    {
        return MISSILE_TARGETS + (roundNum * 9701 + mapLocationToInt(mapLoc)) % NUM_MISSILE_TARGET_CHANNELS * 2;
    }
    
    interface Clause
    {
        boolean isValid(Direction dir);
    }

    static class Bugger {
        static final int FORWARD = 0;
        static final int BUG_LEFT = 1;  // follow left wall
        static final int BUG_RIGHT = 2;
    
        static MapLocation dest;
        static MapLocation prevLoc;
        static int state;
        static int lastBugState;
        static int funState;
        
        // Only valid when bugging
        static Direction bugDir;
        static int bugDirIndex;
        static int hitWallDirIndex;
        static MapLocation hitWallLoc;
        
        static void set(MapLocation dest) {
            if (dest == null)
                Bugger.dest = null;
            else if (!dest.equals(Bugger.dest))
            {
                Bugger.dest = dest;
                state = FORWARD;

                funState = random.nextInt(3);
                if (funState == 1)
                    lastBugState = BUG_LEFT;
                else if (funState == 2)
                    lastBugState = BUG_RIGHT;
            }
        }
        
        static void move(Direction dir) throws Exception {
            prevLoc = myLoc.add(dir);
            rc.move(dir);
        }
        
        static void fun(boolean[] good) throws Exception {
            if (funState == 0)
                push(myLoc.directionTo(dest), good);
            else
                bug(good);
        }
        
        static void push(Direction dir, boolean[] good) throws Exception {
            Direction left, right, left2, right2, left3, right3, opp;
            if (good[dir.ordinal()])
                move(dir);
            else if (good[(left = dir.rotateLeft()).ordinal()])
                move(left);
            else if (good[(right = dir.rotateRight()).ordinal()])
                move(right);
            else if (good[(left2 = left.rotateLeft()).ordinal()])
                move(left2);
            else if (good[(right2 = right.rotateRight()).ordinal()])
                move(right2);
            else if (good[Direction.NONE.ordinal()])
                ;
            else if (good[(left3 = left2.rotateLeft()).ordinal()])
                move(left3);
            else if (good[(right3 = right2.rotateRight()).ordinal()])
                move(right3);
            else if (good[(opp = dir.opposite()).ordinal()])
                move(opp);
        }
        
        static void bug(boolean[] good) throws Exception {
            if (prevLoc != null && !prevLoc.equals(myLoc))
            {
                // Unexpected position; move back
                push(myLoc.directionTo(prevLoc), good);
                return;
            }

            // Update state
            Direction dir = myLoc.directionTo(dest);
            if (state == FORWARD) {
                if (!good[dir.ordinal()]) {
                    state = lastBugState == BUG_LEFT ? BUG_RIGHT : BUG_LEFT;
                    bugDir = dir;
                    bugDirIndex = hitWallDirIndex = directionToInt(dir);
                    hitWallLoc = myLoc;
                }
            } else {
                if (good[dir.ordinal()] && dir != bugDir.opposite()) {
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
                move(dir);
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
                    if (good[candDir.ordinal()]) {
                        bugDir = candDir;
                        bugDirIndex = candDirIndex;
                        move(candDir);
                        return;
                    }
                    if (state == BUG_LEFT) {
                        candDirIndex++;
                        candDir = candDir.rotateRight();
                    } else if (state == BUG_RIGHT) {
                        candDirIndex--;
                        candDir = candDir.rotateLeft();
                    }
                } while (candDir != startDir);
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
