package launcher1;

import battlecode.common.*;
import java.util.*;

public class RobotPlayer {
    static final Direction[] directions = {Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST, Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST};
    static final int MAX_BEAVERS = 10;
    static final int MAX_LABS = 2;
    static final int RUSH_TIME = 1000;
    
    // Message channels
    static final int ROUND_NUM = 0;
    static final int NUM_BEAVERS = 1;
    static final int NUM_HELIPADS = 2;
    static final int NUM_LABS = 3;

    // Game constants
    static RobotController rc;
    static Team myTeam;
    static Team enemyTeam;
    static RobotType myType;
    static int mySensors;
    static int myRange;
    static int producedRound;
    static MapLocation enemyHQ;
    
    // Round constants
    static int roundNum;
    static MapLocation myLoc;
    static double teamOre;
    static int numBeavers;
    static int numHelipads;
    static int numLabs;
    
    public static void run(RobotController tomatojuice) {
        try {
            rc = tomatojuice;
            myTeam = rc.getTeam();
            enemyTeam = myTeam.opponent();
            myType = rc.getType();
            mySensors = myType.sensorRadiusSquared;
            myRange = myType.attackRadiusSquared;
            producedRound = rc.readBroadcast(ROUND_NUM);
            enemyHQ = rc.senseEnemyHQLocation();
        } catch (Exception e) {
            e.printStackTrace();
        }

        while(true) {
            try {
                rc.setIndicatorString(0, "I am a " + myType);

                myLoc = rc.getLocation();
                teamOre = rc.getTeamOre();

                if (myType == RobotType.HQ) {
                    roundNum++;
                    rc.broadcast(ROUND_NUM, roundNum);

                    numBeavers = 0;
                    numHelipads = 0;
                    numLabs = 0;
                    for (RobotInfo r : rc.senseNearbyRobots(999999, myTeam))
                    {
                        RobotType type = r.type;
                        if (type == RobotType.BEAVER) {
                            numBeavers++;
                        } else if (type == RobotType.HELIPAD) {
                            numHelipads++;
                        } else if (type == RobotType.AEROSPACELAB) {
                            numLabs++;
                        }
                    }
                    rc.broadcast(NUM_BEAVERS, numBeavers);
                    rc.broadcast(NUM_HELIPADS, numHelipads);
                    rc.broadcast(NUM_LABS, numLabs);
                }
                else {
                    roundNum = rc.readBroadcast(ROUND_NUM);
                    numBeavers = rc.readBroadcast(NUM_BEAVERS);
                    numHelipads = rc.readBroadcast(NUM_HELIPADS);
                    numLabs = rc.readBroadcast(NUM_LABS);
                }
                
                if (myType == RobotType.HQ)
                    runHQ();
                else if (myType == RobotType.TOWER)
                    runTower();
                else if (myType == RobotType.BEAVER)
                    runBeaver();
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
        if (numBeavers < MAX_BEAVERS)
            trySpawn(RobotType.BEAVER);
        attackSomething();
    }

    static void runTower() throws Exception {
        attackSomething();
    }

    static void runBeaver() throws Exception {
        if (numHelipads == 0)
            tryBuild(RobotType.HELIPAD);
        if (numLabs < MAX_LABS)
            tryBuild(RobotType.AEROSPACELAB);
        if (rc.senseOre(myLoc) > 0 && rc.isCoreReady() && rc.canMine())
            rc.mine();
        tryRandomMove();
    }

    static void runLab() throws Exception {
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
    
    static void tryRandomMove() throws GameActionException {
        tryMove((int) (Math.random() * 8));
    }
    
    // This method will attempt to move in Direction d (or as close to it as possible)
    static void tryMove(int dirint) throws GameActionException {
        if (!rc.isCoreReady())
            return;
        int offsetIndex = 0;
        int[] offsets = {0,1,-1,2,-2};
        while (offsetIndex < 5 && !rc.canMove(directions[(dirint+offsets[offsetIndex]+8)%8])) {
            offsetIndex++;
        }
        if (offsetIndex < 5) {
            rc.move(directions[(dirint+offsets[offsetIndex]+8)%8]);
        }
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
