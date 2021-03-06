package testplayer;

import java.util.ArrayList;
import battlecode.common.*;

class TimeLoc {
  int time;
  MapLocation loc;

  TimeLoc(int time, MapLocation loc) {
    this.time = time;
    this.loc = loc;
  }
}

/**
 * An extension of RobotController with more functionality
 */
public class Robot {
  RobotController rc;
  /**
   * How far away we can sense in a straight line
   */
  int sensor_radius;
  private int retarget_acc;
  int start_turn;

  public Robot(RobotController rc) {
    this.rc = rc;
    sensor_radius = (int) Math.sqrt((double) rc.getType().sensorRadiusSquared);
    retarget_acc = rc.getID();
    start_turn = getTurn();
  }

  int getEnemyVotes() {
    // This works, as long as we bid at least 1 every round
    return getTurn() - getTeamVotes();
  }

  /** How many votes does this robot's team have? */
  int getTeamVotes() {
    return rc.getTeamVotes();
  }

  /**
   * How many turns have passed, so 0 on the first turn and increasing by one
   * after that.
   */
  int getTurn() {
    return rc.getRoundNum() - 1;
  }

  MapLocation getLocation() {
    return rc.getLocation();
  }

  int getID() {
    return rc.getID();
  }

  int getInfluence() {
    return rc.getInfluence();
  }

  // -- COMMUNICATION, ETC. -- //

  MessageQueue queue = new MessageQueue();
  public ArrayList<TimeLoc> enemy_ecs = new ArrayList<TimeLoc>();
  public ArrayList<TimeLoc> friendly_ecs = new ArrayList<TimeLoc>();
  public RobotInfo[] nearby;
  public Integer minX = null;
  public Integer minY = null;
  public Integer maxX = null;
  public Integer maxY = null;

  /**
   * Processes an edge location that was found or recieved from a teammate. If we
   * already know about it, does nothing. If not, saves it and queues a message to
   * tell others about it.
   */
  void setEdge(boolean is_y, MapLocation flag_loc, MapLocation unit_loc) {
    if (is_y) {
      // It's possible both units are on the edge; if so, we can see which direction
      // is on the map
      if (flag_loc.y == unit_loc.y && flag_loc.y == getLocation().y) {
        MapLocation alt = getLocation().translate(0, 1);
        if (isOnMap(alt))
          unit_loc = alt;
        else
          unit_loc = getLocation().translate(0, -1);
      }

      // It's possible that unit is *at* the edge, so flag_loc.y = unit_loc.y;
      // but if so, this unit *isn't* at the edge, so we use that instead.
      if (flag_loc.y < unit_loc.y || flag_loc.y < getLocation().y) {
        // If we've already seen this edge, don't relay it further; we don't want
        // infinite loops.
        if (minY != null)
          return;
        minY = flag_loc.y;
      } else {
        if (maxY != null)
          return;
        maxY = flag_loc.y;
      }
    } else {
      if (flag_loc.x == unit_loc.x && flag_loc.x == getLocation().x) {
        MapLocation alt = getLocation().translate(1, 0);
        if (isOnMap(alt))
          unit_loc = alt;
        else
          unit_loc = getLocation().translate(-1, 0);
      }

      if (flag_loc.x < unit_loc.x || flag_loc.x < getLocation().x) {
        if (minX != null)
          return;
        minX = flag_loc.x;
      } else {
        if (maxX != null)
          return;
        maxX = flag_loc.x;
      }
    }

    // System.out.println("Found edge " + (is_y ? "y" : "x") + " at " + flag_loc);

    // Relay the message on so all units hear about the edge
    // The timestamp is ignored since edges don't change
    queue.enqueue(new Flag(getLocation(), Flag.Type.Edge, is_y, flag_loc, 0), MessageQueue.Priority.Low);
  }

  /**
   * Given a location we know is off the map, moves `(dx, dy)` by `(dx, dy)` until
   * we get to a location that's on the map, then calls setEdge() on that
   * location.
   */
  void findEdge(MapLocation start, int dx, int dy, boolean is_y) throws GameActionException {
    while (!rc.onTheMap(start)) {
      start = start.translate(dx, dy);
    }
    // Go one more to make sure it's not on the edge
    setEdge(is_y, start, start.translate(dx, dy));
  }

  /**
   * Processes an enemy EC location that was found or recieved from a teammate. If
   * we already know about it, does nothing. If not, adds it to the list and
   * queues a message to tell others about it. Retuns whether it was new.
   */
  boolean addEnemyEC(MapLocation ec, int time) throws GameActionException {
    for (int i = 0; i < friendly_ecs.size(); i++) {
      TimeLoc l = friendly_ecs.get(i);
      if (l.loc.equals(ec)) {
        if (l.time > time)
          return false;
        else {
          friendly_ecs.remove(i);
          break;
        }
      }
    }
    for (TimeLoc l : enemy_ecs) {
      if (l.loc.equals(ec)) {
        l.time = Math.max(l.time, time);
        return false;
      }
    }

    if (enemy_ecs.size() > 6) {
      System.out.println("-----\n----\n----\nTOO MANY EECS, can't add " + ec + "\n----\n----");
      return false;
    }

    enemy_ecs.add(new TimeLoc(time, ec));
    rc.setIndicatorDot(ec, 255, 0, 0);

    // System.out.println("Enemy EC at " + ec);

    queue.enqueue(new Flag(getLocation(), Flag.Type.EnemyEC, ec, time), MessageQueue.Priority.Medium);

    return true;
  }

  /**
   * Processes a friendly EC location that was found or recieved from a teammate.
   * If we already know about it, does nothing. If not, adds it to the list and
   * queues a message to tell others about it. Retuns whether it was new.
   */
  boolean addFriendlyEC(MapLocation ec, int time) throws GameActionException {
    for (int i = 0; i < enemy_ecs.size(); i++) {
      TimeLoc l = enemy_ecs.get(i);
      if (l.loc.equals(ec)) {
        if (l.time > time)
          return false;
        else {
          enemy_ecs.remove(i);
          break;
        }
      }
    }
    for (TimeLoc l : friendly_ecs) {
      if (l.loc.equals(ec)) {
        l.time = Math.max(l.time, time);
        return false;
      }
    }

    if (friendly_ecs.size() > 6) {
      System.out.println("-----\n----\n----\nTOO MANY FECS, can't add " + ec + "\n----\n----");
      return false;
    }

    friendly_ecs.add(new TimeLoc(time, ec));
    rc.setIndicatorDot(ec, 0, 0, 255);

    // System.out.println("Friendly EC at " + ec);

    queue.enqueue(new Flag(getLocation(), Flag.Type.FriendlyEC, ec, time), MessageQueue.Priority.Medium);

    return true;
  }

  int update_turn = 0;

  /**
   * Should generally be called every turn - updates `nearby`, reads unit flags,
   * checks for the edge of the map, etc. Call `finish` at the end of the turn,
   * too.
   */
  void update() {
    try {

      update_turn = getTurn();

      nearby = rc.senseNearbyRobots();
      for (RobotInfo i : nearby) {
        if (Clock.getBytecodesLeft() < 1500 || update_turn != getTurn())
          break;

        if (i.team == rc.getTeam()) {
          if (i.type == RobotType.ENLIGHTENMENT_CENTER)
            addFriendlyEC(i.location, getTurn());

          Flag flag = new Flag(i.location, getTurn(), rc.getFlag(i.ID));
          switch (flag.type) {
          case Edge:
            setEdge(flag.aux_flag, flag.flag_loc, i.location);
            break;

          case EnemyEC:
            addEnemyEC(flag.flag_loc, flag.timestamp);
            break;

          case FriendlyEC:
            addFriendlyEC(flag.flag_loc, flag.timestamp);
            break;

          case None:
            break;
          }
        } else if (i.type == RobotType.ENLIGHTENMENT_CENTER) {
          // If we find a new EC, run away from it so we don't die before we tell anyone
          if (addEnemyEC(i.location, getTurn()))
            runFrom(i.location);
        }
      }

      // Check for edges ourselves
      MapLocation loc = getLocation();
      if (minX == null && !rc.onTheMap(loc.translate(-sensor_radius, 0))) {
        findEdge(loc.translate(-sensor_radius, 0), 1, 0, false);
      }
      if (maxX == null && !rc.onTheMap(loc.translate(sensor_radius, 0))) {
        findEdge(loc.translate(sensor_radius, 0), -1, 0, false);
      }
      if (minY == null && !rc.onTheMap(loc.translate(0, -sensor_radius))) {
        findEdge(loc.translate(0, -sensor_radius), 0, 1, true);
      }
      if (maxY == null && !rc.onTheMap(loc.translate(0, sensor_radius))) {
        findEdge(loc.translate(0, sensor_radius), 0, -1, true);
      }

    } catch (GameActionException e) {
      e.printStackTrace();
    }
  }

  /**
   * Finishes the turn, sending the next message in the queue. Must be called
   * *after* moving, or the relative coordinates in the message will be wrong.
   */
  void finish() {
    try {

      // If we've taken too long and might be in next turn, don't send messages,
      // they're outdated
      if (Clock.getBytecodesLeft() < 500 || update_turn != getTurn()) {
        return;
      }

      if (Clock.getBytecodesLeft() > 2400 && queue.isEmpty()) {
        // If the queue is empty, send out all the information we know on repeat.
        // There's no reason not to, as long as the priority is Low.
        MapLocation loc = getLocation();
        if (minX != null)
          queue.enqueue(new Flag(loc, Flag.Type.Edge, false, new MapLocation(minX, loc.y), 0),
              MessageQueue.Priority.Low);
        if (maxX != null)
          queue.enqueue(new Flag(loc, Flag.Type.Edge, false, new MapLocation(maxX, loc.y), 0),
              MessageQueue.Priority.Low);
        if (minY != null)
          queue.enqueue(new Flag(loc, Flag.Type.Edge, true, new MapLocation(loc.x, minY), 0),
              MessageQueue.Priority.Low);
        if (maxY != null)
          queue.enqueue(new Flag(loc, Flag.Type.Edge, true, new MapLocation(loc.x, maxY), 0),
              MessageQueue.Priority.Low);
        for (TimeLoc i : enemy_ecs)
          queue.enqueue(new Flag(loc, Flag.Type.EnemyEC, i.loc, i.time), MessageQueue.Priority.Low);
        for (TimeLoc i : friendly_ecs)
          queue.enqueue(new Flag(loc, Flag.Type.FriendlyEC, i.loc, i.time), MessageQueue.Priority.Low);
      }

      // Send the next message
      Flag flag = queue.next();
      if (flag == null)
        flag = new Flag(getLocation(), Flag.Type.None, 0);
      // Our location might have changed since we enqueued it
      flag.unit_loc = getLocation();
      rc.setFlag(flag.encode(getTurn()));
    } catch (GameActionException e) {
      e.printStackTrace();
    }
  }

  // -- TARGETING & PATHFINDING -- //

  MapLocation target;

  /**
   * Checks if the location is on the map, returning `true` if unsure. Uses the
   * edges we've found if possible.
   */
  boolean isOnMap(MapLocation loc) {
    try {
      if ((minY != null && loc.y < minY) || (maxY != null && loc.y > maxY) || (minX != null && loc.x < minX)
          || (maxX != null && loc.x > maxX))
        return false;
      if (minY == null || maxY == null || minX == null || maxX == null) {
        return (!loc.isWithinDistanceSquared(getLocation(), rc.getType().sensorRadiusSquared) || rc.onTheMap(loc));
      } else {
        // We know the edges, no need to consult `rc`.
        return true;
      }
    } catch (GameActionException e) {
      e.printStackTrace();
      return true;
    }
  }

  /**
   * Makes sure we're going away from the given location.
   */
  void runFrom(MapLocation scary) {
    // We call this if there's an enemy EC in range
    if (rc.getType() == RobotType.ENLIGHTENMENT_CENTER)
      return;

    MapLocation loc = getLocation();

    for (int i = 0; i < 10; i++) {
      if (loc.equals(target))
        retarget();

      // Make sure we're moving away from the muckraker
      // This flips the target's components if they're pointing towards the muckraker
      // (i.e. target is closer to muckraker than we are)
      int x = target.x;
      int y = target.y;
      if (Math.abs(x - scary.x) < Math.abs(loc.x - scary.x)) {
        x = loc.x - (x - loc.x);
      }
      if (Math.abs(y - scary.y) < Math.abs(loc.y - scary.y)) {
        y = loc.y - (y - loc.y);
      }
      target = new MapLocation(x, y);

      // Just try again if it's not on the map, up to 10 times
      if (isOnMap(target)) {
        return;
      } else {
        retarget();
        continue;
      }
    }

    // None of those worked, just move away
    Direction dir = loc.directionTo(scary).opposite();
    target = loc.add(dir).add(dir);
  }

  void retarget() {
    int width = (minX != null && maxX != null) ? maxX - minX : 64;
    int height = (minY != null && maxY != null) ? maxY - minY : 64;
    MapLocation min = new MapLocation(minX != null ? minX : getLocation().x - width / 2,
        minY != null ? minY : getLocation().y - height / 2);

    if (rc.getType() == RobotType.SLANDERER && !friendly_ecs.isEmpty()) {
      // Stay close to the EC
      int dist_from_ec = 16;
      min = new MapLocation(Math.max(min.x, friendly_ecs.get(0).loc.x - dist_from_ec),
          Math.max(min.y, friendly_ecs.get(0).loc.y - dist_from_ec));
      width = dist_from_ec;
      height = dist_from_ec;
    }

    retarget(min, width, height);
  }

  void retarget(MapLocation min, int width, int height) {
    int wxh = width * height;
    // This is a RNG technique I found online called Linear Congruential Generator
    // This should be a random 12 bits
    retarget_acc = (1231 * retarget_acc + 3171) % wxh;
    // Split into two, each in 0..N
    int x = retarget_acc % width;
    int y = retarget_acc / width;
    // Now switch to absolute coordinates
    target = min.translate(x, y);

    // Reset blocked_turns if we get a new target
    blocked_turns = 0;
  }

  int blocked_turns = 0;

  boolean can_move(MapLocation loc) {
    if (!isOnMap(loc))
      return false;

    for (TimeLoc i : friendly_ecs) {
      if (i.loc.isWithinDistanceSquared(target, 2)) {
        return false;
      }
    }

    return true;
  }

  boolean target_move(boolean retarget_if_there) {
    try {
      MapLocation loc = getLocation();

      if (loc.equals(target)) {
        if (retarget_if_there) {
          retarget();
        } else {
          return false;
        }
      }

      // Retarget if:
      // 1. it's not on the map
      if (!can_move(target)) {
        retarget();
      } else if (loc.isWithinDistanceSquared(target, rc.getType().sensorRadiusSquared)) {
        // 2. it's blocked, and has been blocked for long enough that it probably won't
        // be unblocked soon.
        // If it's an enemy robot, go there anyway, we're probably trying to kill it.
        RobotInfo robot = rc.senseRobotAtLocation(target);
        if (robot != null && robot.team == rc.getTeam()) {
          blocked_turns += 1;
          if (blocked_turns > 10) {
            retarget();
          }
        } else {
          blocked_turns = 0;
        }
      }

      rc.setIndicatorLine(getLocation(), target, 0, 255, 0);

      Direction to_dir = loc.directionTo(target);
      Direction dir = to_dir;

      // Try going around obstacles, first left, then right
      MapLocation[] options = { loc.add(dir), loc.add(dir.rotateLeft()), loc.add(dir.rotateRight()) };
      MapLocation best = null;
      double best_pass = -1;
      for (MapLocation i : options) {
        if (!can_move(i) || rc.isLocationOccupied(i))
          continue;
        if (i.equals(target)) {
          best = i;
          best_pass = 100;
        }
        if (rc.sensePassability(i) > best_pass) {
          best = i;
          best_pass = rc.sensePassability(i);
        }
      }
      if (best == null)
        return false;
      dir = loc.directionTo(best);

      return move(dir);

    } catch (GameActionException e) {
      System.out.println("Unreachable!");
      e.printStackTrace();
      return false;
    }
  }

  // -- ACTIONS -- //

  boolean expose(MapLocation loc) {
    if (rc.canExpose(loc)) {
      try {
        rc.expose(loc);
      } catch (GameActionException e) {
        System.out.println("Unreachable");
      }
      return true;
    } else {
      return false;
    }
  }

  boolean empower(int rad2) {
    if (rc.canEmpower(rad2)) {
      try {
        rc.empower(rad2);
      } catch (GameActionException e) {
        System.out.println("Unreachable");
      }
      return true;
    } else {
      return false;
    }
  }

  boolean bid(int bid) {
    if (rc.canBid(bid)) {
      try {
        rc.bid(bid);
      } catch (GameActionException e) {
        System.out.println("Unreachable");
      }
      return true;
    } else {
      return false;
    }
  }

  boolean move(Direction dir) {
    if (rc.canMove(dir)) {
      try {
        // In case we don't get around to setting the flag later
        // The one we had before is now invalid, since we moved
        rc.setFlag(0);
        rc.move(dir);
      } catch (GameActionException e) {
        // It is actually possible for us to get here, if we ran out
        // of bytecode and somebody else moved here before we came back
        return false;
      }
      return true;
    } else {
      return false;
    }
  }

  boolean build(RobotType type, Direction dir, int influence) {
    if (rc.canBuildRobot(type, dir, influence)) {
      try {
        rc.buildRobot(type, dir, influence);
      } catch (GameActionException e) {
        System.out.println("Unreachable");
      }
      return true;
    } else {
      return false;
    }
  }
}
