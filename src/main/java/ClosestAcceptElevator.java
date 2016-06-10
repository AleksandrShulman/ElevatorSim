import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

/**
 * This class is an implementation of the ClosestAccept algorithm,
 * in which an elevator will be assigned based on how close it is
 * to the target
 * <p/>
 * The algorithm is:
 * The elevator that is moving in the direction of the user, and is closest,
 * will be assigned that user.
 */
public class ClosestAcceptElevator extends Elevator {

    //TODO: Figure out which methods apply to all elevators in general,
    //      and which apply to just this implementation
    final static Logger logger = Logger.getLogger("ClosestAcceptElevator.class");

    SimpleFormatter fmt = new SimpleFormatter();
    StreamHandler sh = new StreamHandler(System.out, fmt);

    {
        logger.addHandler(sh);
        logger.setLevel(Level.INFO);
    }

    public ClosestAcceptElevator(final int id) {

        this.id = id;
        Elevator.Direction travelDirection = Elevator.Direction.NOT_MOVING;
        this.riders = Collections.synchronizedSet(new HashSet<Rider>());
    }

    @Override
    public void acceptRiderRequest(Rider r) {

        logger.info("Elevator " + id + " has agreed to pick up rider " + r.riderId);
        this.riders.add(r);
    }

    @Override
    public int getNumberOfRiders() {
        return this.riders.size();
    }

    @Override
    public void run() {

        try {
            logger.info("Elevator " + this.id + " starting...");

            // While...

            // Move towards the nextFloor
            // Once you arrive at that floor, find the nextFloor and move towards it

            while (BuildingSimulation.simulationStillRunning()) {

                // Wait until we know where we're going
                while (nextFloorToVisit == null) {

                    if (riders.size() > 0) {
                        nextFloorToVisit = figureOutNextDestination();
                    }

                    if (!BuildingSimulation.simulationStillRunning()) {
                        break;
                    }

                    Thread.sleep(3000);
                }

                if (!BuildingSimulation.simulationStillRunning()) {
                    break;
                }

                if (nextFloorToVisit > this.currentFloor) {
                    upOneFloor();

                } else if (nextFloorToVisit < this.currentFloor) {
                    downOneFloor();

                } else if (nextFloorToVisit == this.currentFloor) {
                    arriveAtFloor(this.currentFloor);
                    this.nextFloorToVisit = figureOutNextDestination();
                } else {
                    this.nextFloorToVisit = null;
                }
            }

            logger.info("Elevator " + id + " simulation over. Dropped off " +
                    totalRidersTransported + " riders and had " + riders.size() + " " +
                    "remaining riders");

        } catch (Throwable e) {

            logger.severe("Caught an issue with elevator " + id);
            e.printStackTrace();
        }
    }

    private Integer figureOutNextDestination() {

        if (this.riders.size() == 0) {
            logger.info("Elevator " + id + " going to do nothing b/c no more riders " +
                    "assigned");
            return null;
        }

        Integer nextFloor = null;
        if (this.travelDirection == Elevator.Direction.UP) {
            nextFloor = getNextFloorGoingUp();
        } else if (this.travelDirection == Elevator.Direction.DOWN) {
            nextFloor = getNextFloorGoingDown();
        } else {
            if ((int) (Math.random() * 1000) % 2 == 0) {
                this.travelDirection = Elevator.Direction.UP;
                nextFloor = getNextFloorGoingUp();
            } else {
                this.travelDirection = Elevator.Direction.DOWN;
                nextFloor = getNextFloorGoingDown();
            }
        }
        return nextFloor;
    }

    private synchronized void arriveAtFloor(int floor) throws InterruptedException {

        // Let remove all the users whose destination floor is this one -
        // TODO: do this in a smarter way
        logger.info("Elevator " + id + " arriving at " + floor);

        List<Rider> ridersToDropOff = new ArrayList<Rider>();
        for (Rider r : riders) {
            if (r.getDestinationFloor() == floor) {
                logger.info("Elevator " + this.id + " dropping off rider " + r.riderId + "" +
                        " at " + floor);
                ridersToDropOff.add(r);
            }
        }

        for (Rider r : ridersToDropOff) {
            r.getOffElevator(this);
            this.riders.remove(r);
            this.totalRidersTransported++;
        }

        Thread.sleep(BuildingSimulation.UNLOAD_TIME_MS);

        // Accept all users whose destination is this one
        boolean waitForLoading = false;
        for (Rider r : BuildingSimulation.getActiveRiders()) {
            if (r.getElevator() == null & r.startFloor == floor) {
                r.getInElevator(this);
                waitForLoading = true;
            }
        }

        if (waitForLoading) {
            Thread.sleep(BuildingSimulation.LOAD_TIME_MS);
        }

        if (riders.size() != 0) {

            // Get the next floor you need to go to
            if (this.travelDirection == Elevator.Direction.UP) {
                this.nextFloorToVisit = getNextFloorGoingUp();
            } else if (this.travelDirection == Elevator.Direction.DOWN) {
                this.nextFloorToVisit = getNextFloorGoingDown();
            } else {
                // Not totally sure about this
            }
        } else {

            // make available for assignment
            this.nextFloorToVisit = null;
        }
    }

    /**
     * This is a very important algorithm.
     * Basically we look at the available elevators going
     * in the direction of the rider and we pick the one
     * closest (to minimize the wait time)
     *
     * If none are going that way, then we summon a stationary elevator.
     *
     * @param r
     */
    public static void assignRiderToElevator(Rider r) {

        // default to a random elevator, because they're going to all be on the same one otherwise
        Random rand = new Random();
        int elevatorIndex = rand.nextInt((BuildingSimulation.elevators.size()));

        Elevator closestElevator = BuildingSimulation.elevators.get(elevatorIndex);
        int smallest_distance = r.startFloor - closestElevator.currentFloor;
        Elevator.Direction necessaryDirectionToTravel = Elevator.Direction.UP;

        for (Elevator e : BuildingSimulation.elevators) {

            if (e.travelDirection == Elevator.Direction.UP && e.currentFloor <= r.startFloor) {

                // This person is above the elevator
                int distance = r.startFloor - e.currentFloor;
                if (distance < smallest_distance) {

                    smallest_distance = distance;
                    closestElevator = e;
                }

                necessaryDirectionToTravel = Elevator.Direction.UP;

            } else if (e.travelDirection == Elevator.Direction.DOWN && e.currentFloor >= r.startFloor) {

                int distance = e.currentFloor - r.startFloor;
                if (distance < smallest_distance) {

                    smallest_distance = distance;
                    closestElevator = e;
                }

                necessaryDirectionToTravel = Elevator.Direction.DOWN;

            } else if (e.travelDirection == Elevator.Direction.NOT_MOVING) {

                int distance = Math.abs(e.currentFloor - r.startFloor);
                if (distance < smallest_distance) {

                    smallest_distance = distance;
                    closestElevator = e;
                }

                if (e.currentFloor > r.startFloor) {
                    necessaryDirectionToTravel = Elevator.Direction.UP;
                } else if (e.currentFloor < r.startFloor) {
                    necessaryDirectionToTravel = Elevator.Direction.DOWN;
                } else {

                    if (r.getDestinationFloor() < e.currentFloor) {
                        necessaryDirectionToTravel = Elevator.Direction.DOWN; //they're on the same floor
                    } else if (r.getDestinationFloor() > e.currentFloor) {
                        necessaryDirectionToTravel = Elevator.Direction.UP;
                    } else {
                        logger.warning("Why are you using an elevator to stay on the same " +
                                "floor!??");
                        necessaryDirectionToTravel = Elevator.Direction.NOT_MOVING;
                    }
                }
            }
        }

        if (closestElevator == null) {

            logger.warning("Not able to assign rider. Very strange.");
        } else {

            // We found an elevator. Excellent.

            // Check the next floor that this elevator will hit. It's probably after the floor
            // where this user is. Adjust that, and we're all set.

            if (necessaryDirectionToTravel == Elevator.Direction.UP) {

                if (closestElevator.nextFloorToVisit == null || closestElevator.nextFloorToVisit > r
                        .getDestinationFloor()) {

                    closestElevator.nextFloorToVisit = r.getDestinationFloor();
                }
            } else if (necessaryDirectionToTravel == Elevator.Direction.DOWN) {

                if (closestElevator.nextFloorToVisit == null || closestElevator.nextFloorToVisit < r
                        .getDestinationFloor()) {
                }
            } else if (necessaryDirectionToTravel == Elevator.Direction.NOT_MOVING) {

                // If we don't need to move, then let's not mess with anything
            }

            logger.info("Assigned rider " + r.riderId + " to take elevator " +
                    closestElevator.id + " because the distance is " + smallest_distance);

            closestElevator.acceptRiderRequest(r);
        }
    }

    private int getNextFloorGoingUp() {
        int lowestDestinationFloor = BuildingSimulation.NUM_FLOORS + 1; //theoretical
        logger.info("Initial value for lowest floor is " + lowestDestinationFloor);
        // unattainable max value
        for (Rider r : riders) {
            int destinationFloor = r.getDestinationFloor();
            if (destinationFloor < lowestDestinationFloor) {
                lowestDestinationFloor = destinationFloor;
            }
        }
        return lowestDestinationFloor;
    }

    private int getNextFloorGoingDown() {

        int lowestDestinationFloor = 0;
        for (Rider r : riders) {

            int destinationFloor = r.getDestinationFloor();
            if (destinationFloor > lowestDestinationFloor) {
                lowestDestinationFloor = destinationFloor;
            }
        }

        return lowestDestinationFloor;
    }

    private void upOneFloor() throws InterruptedException {

        this.travelDirection = Elevator.Direction.UP;
        logger.fine(this.id + ": going up from " + this.currentFloor + " to " +
                (currentFloor + 1));
        if (this.currentFloor < BuildingSimulation.NUM_FLOORS) {
            Thread.sleep(BuildingSimulation.MS_PER_FLOOR_CLIMB);
            this.currentFloor++;
        }
    }

    private void downOneFloor() throws InterruptedException {

        this.travelDirection = Elevator.Direction.DOWN;
        logger.fine(this.id + ": going down from " + this.currentFloor + " to " +
                (currentFloor - 1));
        Thread.sleep(BuildingSimulation.MS_PER_FLOOR_CLIMB);
        if (this.currentFloor > 0) {
            this.currentFloor--;
        }
    }

    public int getTotalRidersTransported() {

        return this.totalRidersTransported;
    }

    @Override
    public int getId() {
        return id;
    }
}
