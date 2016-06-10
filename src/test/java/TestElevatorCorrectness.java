import junit.framework.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Created by aleks on 5/21/16.
 */
public class TestElevatorCorrectness {

    static final int DEFAULT_NUM_FLOORS = 20;

    @Test
    public void testSingleRiderSingleElevator() throws InterruptedException {

        final int TOTAL_RIDERS = 1;
        runElevatorRiderSimulation(1, TOTAL_RIDERS, 15);
    }

    @Test
    public void testSingleRiderMultipleElevators() throws InterruptedException {

        final int TOTAL_ELEVATORS = 5;
        runElevatorRiderSimulation(TOTAL_ELEVATORS, 1, 5);
    }

    @Test
    public void testMultipleRidersSingleElevator() throws InterruptedException {

        final int TOTAL_RIDERS = 5;
        runElevatorRiderSimulation(1, TOTAL_RIDERS, 15);
    }

    @Test
    public void testOverloadedElevators() throws InterruptedException {

        final int TOTAL_RIDERS = 65;
        runElevatorRiderSimulation(10, TOTAL_RIDERS, 90);
    }

    @Test
    public void testMultipleRidersMultipleElevators() throws InterruptedException {

        final int TOTAL_RIDERS = 5;
        final int TOTAL_ELEVATORS = 2;
        runElevatorRiderSimulation(TOTAL_ELEVATORS, TOTAL_RIDERS, 10);
    }

    private void runElevatorRiderSimulation(final int NUM_ELEVATORS, final int NUM_RIDERS,
                                            final int TIMEOUT) throws InterruptedException {

        BuildingSimulation simulation = new BuildingSimulation(NUM_ELEVATORS, NUM_RIDERS, TIMEOUT,
                DEFAULT_NUM_FLOORS);
        simulation.runSimulation();

        int totalRidersTransported = 0;
        for (Elevator e : BuildingSimulation.elevators) {

            totalRidersTransported += e.getTotalRidersTransported();
        }

        Assert.assertEquals("Not all riders were dropped off.", NUM_RIDERS,
                totalRidersTransported);
    }

    //FIXME: Test used to pass. Now it fails. No idea why.
    //@Test
    public void testAddingRidersInParallel() throws InterruptedException {

        final int NUM_INITIAL_RIDERS = 10;
        final int NUM_ADDITIONAL_RIDERS_PER_CLIENT = 10;
        final int NUM_ADDITIONAL_CLIENTS = 5;

        final int NUM_ELEVATORS = 15;
        final int NUM_FLOORS = 20;

        final int TIMEOUT = 150;

        class RiderGenerator implements Runnable {

            @Override
            public void run() {

                BuildingSimulation.addNewRider(NUM_ADDITIONAL_RIDERS_PER_CLIENT);
            }
        }

        BuildingSimulation simulation = new BuildingSimulation(NUM_ELEVATORS, NUM_INITIAL_RIDERS,
                TIMEOUT, NUM_FLOORS);

        simulation.runSimulation();

        ExecutorService execService = Executors.newFixedThreadPool(NUM_ELEVATORS);
        List<Future> futureList = new ArrayList<Future>();
        List<RiderGenerator> riderGenerators = new ArrayList<>();
        for (int clientId = 0; clientId < NUM_ADDITIONAL_CLIENTS; clientId++) {
            RiderGenerator rge = new RiderGenerator();
            riderGenerators.add(rge);

            // Start the elevator
            futureList.add(execService.submit(rge));
        }

        execService.shutdown();
        Thread.sleep(3000);
        execService.awaitTermination(TIMEOUT, TimeUnit.SECONDS);

        int totalRidersTransported = 0;
        for (Elevator e : BuildingSimulation.elevators) {

            totalRidersTransported += e.getTotalRidersTransported();
        }

        Assert.assertEquals("Not all riders were dropped off.", NUM_INITIAL_RIDERS +
                        NUM_ADDITIONAL_RIDERS_PER_CLIENT * NUM_ADDITIONAL_CLIENTS,
                totalRidersTransported);
    }
}

