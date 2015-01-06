package dronerush;

import battlecode.common.*;
import java.util.*;

public class RobotPlayer {
	static final Direction[] directions = {Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST, Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST};
	
	// Message channels
	static final int ROUND_NUM = 0;
	static final int NUM_BEAVERS = 1;
	static final int NUM_HELIPAD = 2;

	// Game constants
	static RobotController rc;
	static Team myTeam;
	static Team enemyTeam;
	static RobotType myType;
	static int myRange;
	static int producedRound;
	static MapLocation enemyHQ;
	
	// Round constants
	static int roundNum;
	static MapLocation myLoc;
	static double teamOre;
	static int numBeavers;
	static int numHelipads;
	
	public static void run(RobotController tomatojuice) {
		try {
		    rc = tomatojuice;
		    myTeam = rc.getTeam();
		    enemyTeam = myTeam.opponent();
		    myType = rc.getType();
		    myRange = myType.attackRadiusSquared;
		    producedRound = rc.readBroadcast(ROUND_NUM);
		    enemyHQ = rc.senseEnemyHQLocation();
		} catch (Exception e) {
		    e.printStackTrace();
		}
		
		while(true) {

            try {
                rc.setIndicatorString(0, "This is an indicator string.");
                rc.setIndicatorString(1, "I am a " + rc.getType());
                
                myLoc = rc.getLocation();
                teamOre = rc.getTeamOre();
                
                if (myType == RobotType.HQ) {
                    numBeavers = 0;
                    numHelipads = 0;
                    for (RobotInfo r : rc.senseNearbyRobots(999999, myTeam))
                    {
                        RobotType type = r.type;
                        if (type == RobotType.BEAVER) {
                            numBeavers++;
                        } else if (type == RobotType.HELIPAD) {
                            numHelipads++;
                        }
                    }
                    rc.broadcast(NUM_BEAVERS, numBeavers);
                    rc.broadcast(NUM_HELIPAD, numHelipads);
                    
                    roundNum++;
                    rc.broadcast(ROUND_NUM, roundNum);
                }
                else {
                    numBeavers = rc.readBroadcast(NUM_BEAVERS);
                    numHelipads = rc.readBroadcast(NUM_HELIPAD);
                    roundNum = rc.readBroadcast(ROUND_NUM);
                }
                
                if (myType == RobotType.HQ)
                    runHQ();
                else if (myType == RobotType.TOWER)
                    runTower();
                else if (myType == RobotType.BEAVER)
                    runBeaver();
                else if (myType == RobotType.HELIPAD)
                    runHelipad();
                else if (myType == RobotType.DRONE)
                    runDrone();
                
                rc.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
		}
	}
	
    static void runHQ() throws Exception {
        if (numBeavers < 10)
            trySpawn(RobotType.BEAVER);
        attackSomething();
	}
    
    static void runTower() throws Exception {
        attackSomething();
	}
	
    static void runBeaver() throws Exception {
        if ((numHelipads <= 3 && (numHelipads == 0 || roundNum > 200)))
            tryBuild(RobotType.HELIPAD);
        if (rc.senseOre(myLoc) > 0 && rc.isCoreReady() && rc.canMine())
            rc.mine();
        tryRandomMove();
	}

    static void runHelipad() throws Exception {
        trySpawn(RobotType.DRONE);
    }

    static void runDrone() throws Exception {
        attackSomething();
        if (rc.senseNearbyRobots(myRange, enemyTeam).length == 0)
        {
            if (roundNum < 1000)
                tryRandomMove();
            else
                tryMove(directionToInt(myLoc.directionTo(enemyHQ)));
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
	    if (!rc.isCoreReady() || teamOre < 500)
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
	    if (!rc.isCoreReady() || teamOre < 500)
	        return;
		int i = 0;
		while (i < 8 && !rc.canMove(directions[i]))
			i++;
		if (i < 8) {
			rc.build(directions[i], type);
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
