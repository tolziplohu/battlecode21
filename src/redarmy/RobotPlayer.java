package redarmy;

import battlecode.common.*;
import static battlecode.common.RobotType.*;

public strictfp class RobotPlayer {
  public static void run(RobotController rc) throws GameActionException {
    if (rc.getType() == ENLIGHTENMENT_CENTER)
      ECenter.run(rc);
    else
      Robot.run(rc);
  }
}
