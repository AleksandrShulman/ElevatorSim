import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

/**
 * Building represents a building with a set of elevators, along with people
 * who make trips on those elevators.
 * <p/>
 * Usage: There is only one simulation running at a time. This is limiting, but
 * it allows for the use of static variables instead of having to pass around an
 * instance of BuildingSimulation.
 * <p/>
 * TODO: Allow for multiple BuildingSimulations to be running at once without
 * interfering with one another
 */
public class BuildingSimulation {

    final static Logger logger = Logger.getLogger("BuildingSimulation.class");

    SimpleFormatter fmt = new SimpleFormatter();
    StreamHandler sh = new StreamHandler(System.out, fmt);

    {
        logger.addHandler(sh);
        logger.setLevel(Level.INFO);
    }

    // TODO: Add a scaling factor for time, to make testing faster.
    // TODO: Make these parameters with sensible defaults
    final static int MS_PER_FLOOR_CLIMB = 100; //time it takes to move up or down a floor
    final static int LOAD_TIME_MS = 100; // time it takes to load an elevator
    final static int UNLOAD_TIME_MS = 100; // time it takes to unload an elevator
    final static int DEFAULT_TIMEOUT_SECONDS = 30;
    final int MILLISECONDS_PER_SECOND = 1000;

    static int NUM_FLOORS;
    static int NUM_ELEVATORS;
    static int TIMEOUT_SECONDS;

    // The wall-clock time, in milliseconds, that the job was started
    private static volatile long startTime;
    private static volatile long simulationDuration;

    public static ArrayList<Elevator> elevators;


    private static volatile List<Rider> activeRiders;
    public static volatile List<Rider> unassignedRiders;

    //TODO: Make this constructors more elegant, likely with a builder pattern
    public BuildingSimulation(final int NUM_ELEVATORS, final int NUM_RIDERS,
                              final int NUM_FLOORS) throws InterruptedException {

        this(NUM_ELEVATORS, NUM_RIDERS, BuildingSimulation.DEFAULT_TIMEOUT_SECONDS, NUM_FLOORS);
    }


    /**
     * This constructor is useful if you know which riders you have ahead of time.
     * Great for testing.
     * @param NUM_ELEVATORS
     * @param riders
     * @param TIMEOUT_SECONDS
     * @param NUM_FLOORS
     * @throws InterruptedException
     */
    public BuildingSimulation(final int NUM_ELEVATORS, List<Rider> riders, int TIMEOUT_SECONDS,
                              final int NUM_FLOORS) throws InterruptedException {

        BuildingSimulation.simulationDuration = TIMEOUT_SECONDS * MILLISECONDS_PER_SECOND;
        BuildingSimulation.NUM_FLOORS = NUM_FLOORS;
        BuildingSimulation.TIMEOUT_SECONDS = TIMEOUT_SECONDS;
        BuildingSimulation.NUM_ELEVATORS = NUM_ELEVATORS;

        if (NUM_ELEVATORS < 0 || TIMEOUT_SECONDS < 0 || NUM_FLOORS < 0 || riders == null) {
            throw new IllegalArgumentException("Invalid arguments for simulation");
        }

        this.activeRiders = Collections.synchronizedList(riders);

        // All active riders are initially unassigned. We will go off of that list
        BuildingSimulation.unassignedRiders = Collections.synchronizedList(BuildingSimulation
                .getActiveRiders());

    }

    public BuildingSimulation(final int NUM_ELEVATORS, final int NUM_RIDERS, int TIMEOUT_SECONDS,
                              final int NUM_FLOORS) throws InterruptedException {

        BuildingSimulation.simulationDuration = TIMEOUT_SECONDS * MILLISECONDS_PER_SECOND;
        BuildingSimulation.NUM_FLOORS = NUM_FLOORS;
        BuildingSimulation.TIMEOUT_SECONDS = TIMEOUT_SECONDS;
        BuildingSimulation.NUM_ELEVATORS = NUM_ELEVATORS;


        // Build a sample set of users in the main
        activeRiders = Collections.synchronizedList(new ArrayList<Rider>(NUM_RIDERS));
        for (int i = 0; i < NUM_RIDERS; i++) {
            getActiveRiders().add(BuildingSimulation.manufactureRandomRider());
        }

        // All active riders are initially unassigned. We will go off of that list
        BuildingSimulation.unassignedRiders = Collections.synchronizedList(BuildingSimulation
                .getActiveRiders());
    }

    /**
     * Every instance of a trip
     *
     * @return
     */
    public static List<Rider> getActiveRiders() {
        return activeRiders;
    }

    /**
     * Every instance of a trip that hasn't been
     * assigned an elevator
     *
     * @return
     */
    public static List<Rider> getUnassignedRiders() {
        return unassignedRiders;
    }

    /**
     * A utility method to create a test rider
     *
     * @return
     */
    public static Rider manufactureRandomRider() {
        Random rand = new Random();
        int startFloor = rand.nextInt(BuildingSimulation.NUM_FLOORS) + 1;

        rand = new Random();
        int endFloor = rand.nextInt(BuildingSimulation.NUM_FLOORS) + 1;
        while (endFloor == startFloor) {
            rand = new Random();
            endFloor = rand.nextInt(BuildingSimulation.NUM_FLOORS) + 1;
        }

        Rider rider = new Rider(startFloor, endFloor);
        logger.info("Rider " + rider.riderId + " starting at floor " + rider.startFloor
                + " going to " + endFloor);

        return rider;
    }

    public static boolean simulationStillRunning() {

        long currentTime = System.currentTimeMillis();
        long difference = currentTime - BuildingSimulation.startTime;
        boolean shouldEnd = difference > BuildingSimulation.simulationDuration;

        return (!shouldEnd);
    }

    /**
     * Utility method for add new riders while a simulation is running.
     * Great for testing.
     *
     * TODO: Rearchitect runSimulation to be async (i.e. return after call)
     * so that we can add more riders w/o having to go into a new thread.
     *
     * @param numRiders
     */
    public static void addNewRider(int numRiders) {

        for (int i = 0; i < numRiders; i++) {
            logger.info("Adding rider " + i);
            Rider r = manufactureRandomRider();
            getActiveRiders().add(r);
            getUnassignedRiders().add(r);
        }

        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Runs the simulation, once it's been constructed.
     * I decoupled the running and the initialization
     * for flexibility
     *
     * @throws InterruptedException
     */
    public void runSimulation() throws InterruptedException {
        // Build the threadpool

        BuildingSimulation.startTime = System.currentTimeMillis();
        logger.info("Starting simulation at " + BuildingSimulation.startTime);

        ExecutorService execService = Executors.newFixedThreadPool(NUM_ELEVATORS);
        List<Future> futureList = new ArrayList<Future>();
        elevators = new ArrayList<>();
        for (int eId = 0; eId < NUM_ELEVATORS; eId++) {
            Elevator e = new ClosestAcceptElevator(eId);
            elevators.add(e);

            // Start the elevator
            futureList.add(execService.submit(e));
        }

        while (simulationStillRunning()) {

            if (unassignedRiders.size() > 0) {
                Rider rider = unassignedRiders.get(0);
                logger.info("About to assign rider " + rider.riderId);
                ClosestAcceptElevator.assignRiderToElevator(rider);
                unassignedRiders.remove(rider);
            }

            Thread.sleep(1000);
        }
        execService.shutdown();
        final int TIMEOUT_SECONDS = 90;
        Thread.sleep(3000);
        execService.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    public static void main(String args[]) throws InterruptedException {

        final int NUM_RIDERS = 10;
        final int NUM_ELEVATORS = 5;
        final int TIMEOUT_SECONDS = 50;

        BuildingSimulation bs = new BuildingSimulation(NUM_ELEVATORS, NUM_RIDERS, TIMEOUT_SECONDS);
        bs.runSimulation();
    }
}
